package pt.isec.server.core;
import pt.isec.server.db.DbCommands;
import pt.isec.server.db.DbCreate;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.AnswerService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;
import pt.isec.server.services.question.QuestionService;
import pt.isec.server.threads.ClusterHeartbeatThread;
import pt.isec.server.threads.ClientListenerThread;
import pt.isec.server.threads.DirectoryHeartbeatThread;
import pt.isec.server.threads.NetworkTcpConnection;
import pt.isec.common.util.Log;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ServerManager implements IServerManager, IQuestionAnswerContext, Runnable, AutoCloseable {
    private volatile boolean dbInitialised = false;
    private DbCommands dbCommands;

    // Agora guardamos as referências como interfaces
    private IAuthService authService;
    private IQuestionService questionService;
    private IAnswerService answerService;

    private final Map<Long, String> activeSessions = new ConcurrentHashMap<>();
    private final BlockingQueue<List<String>> sqlToBroadcast = new LinkedBlockingQueue<>();

    private final String id;
    private final String serverIdShort;
    private final String ip;
    private final int clientPort;
    private final int dbCopyPort;

    private final String dirHost;
    private final int dirPort;

    private final String multicastGroup = "230.30.30.30";
    private final int multicastPort = 3030;
    private NetworkInterface multicastInterface;

    private final Path dataDir;
    private volatile Path dbPath;

    private volatile boolean running = true;
    private volatile boolean isPrimary = false;

    private final AtomicLong dbVersion = new AtomicLong(0);
    private final AtomicBoolean copying = new AtomicBoolean(false);

    private Thread threadClusterHeartbeat, tDirectoryHeartbeat, threadClientListener;

    private final Map<Long, NetworkTcpConnection> activeClientConnections = new ConcurrentHashMap<>();

    public ServerManager(String dirHost, int dirPort, String mcIfIp,
                         int clientPort, int dbCopyPort, Path initialDbPath) throws Exception {
        this.id = UUID.randomUUID().toString(); //gera um identificador único aleatório e atribui-o como uma string
        this.serverIdShort = id.substring(0, 8);
        this.ip = InetAddress.getLocalHost().getHostAddress(); //IP local desta máquina
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.dataDir = (initialDbPath.getParent() != null)
                ? initialDbPath.getParent().toAbsolutePath()
                : Paths.get(".").toAbsolutePath();

        this.isPrimary = false;

        this.multicastInterface = resolveMulticastInterface(mcIfIp);
        if (this.multicastInterface == null)
            throw new IllegalArgumentException("Interface de rede inválida para IP/criterio: " + mcIfIp);

        Log.info(ServerManager.class, "[MC] usando interface: " + multicastInterface.getName());
        Log.info(ServerManager.class, "[DB] path inicial=" + dbPath + " (versão=" + dbVersion.get() + ", role=BACKUP)");
    }

    private static NetworkInterface resolveMulticastInterface(String mcIfIp) throws Exception {
        if (mcIfIp == null || mcIfIp.isBlank() || "AUTO".equalsIgnoreCase(mcIfIp)) {
            return pickDefaultMulticastInterface();
        }
        InetAddress addr = InetAddress.getByName(mcIfIp);
        NetworkInterface ni = NetworkInterface.getByInetAddress(addr);
        if (ni != null && ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
            return ni;
        }
        return pickDefaultMulticastInterface();
    }

    private static NetworkInterface pickDefaultMulticastInterface() throws Exception {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast())
                continue;
            var addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address) {
                    return ni;
                }
            }
        }
        return null;
    }

    private static Path findNewestDbInDir(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".db"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    public synchronized void initDbPathAsPrincipalOnStartup() throws IOException {
        if (this.dbPath != null)
            return; // already chosen

        Path newest = findNewestDbInDir(dataDir);
        if (newest != null) {
            this.dbPath = newest.toAbsolutePath();
            System.out.println("[DB] PRINCIPAL: usar BD mais recente no diretório: " + this.dbPath);
        } else {
            String name = String.format("quiz-%s.db", serverIdShort);
            this.dbPath = dataDir.resolve(name).toAbsolutePath();
            System.out.println("[DB] PRINCIPAL: nenhuma BD encontrada, nova será: " + this.dbPath);
        }
    }

    public synchronized void initDbPathAsBackupOnStartup() {
        if (this.dbPath != null)
            return; // already chosen

        String name = String.format("quiz-%s.db", serverIdShort);
        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        System.out.println("[DB] BACKUP: BD local deste servidor será: " + this.dbPath);
    }

    //atualiza o caminho para o ficheiro da base de dados, construindo um novo nome que inclui a versão
    // da base de dados e se é a principal ou uma cópia de segurança.
    private synchronized void refreshDbPath() {
        // One file per server + role. You can even ignore role if you prefer.
        String role = isPrimary ? "primary" : "backup";
        String name = String.format("quiz-%s-%s.db", role, serverIdShort);

        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        Log.info(ServerManager.class, "[DB] agora a usar: " + this.dbPath +
                " (role=" + (isPrimary ? "PRIMARY" : "BACKUP") +
                ", v=" + dbVersion.get() + ")");
    }

    @Override
    public void initDatabaseLayerIfNeeded() {
        if (dbInitialised)
            return;
        synchronized (this) {
            if (dbInitialised)
                return;
            try {
                DbCreate.createIfMissing(this.dbPath, "/db/schema.sql");
                this.dbCommands = new DbCommands("jdbc:sqlite:" + this.dbPath.toAbsolutePath(), this::setDbVersion);
                IQuestionAnswerContext qaContext = this;
                // serviços concretos, mas guardados como interfaces
                this.authService = new AuthService(qaContext, dbCommands);


                this.questionService = new QuestionService(qaContext, dbCommands);
                this.answerService   = new AnswerService(qaContext, dbCommands);

                Log.info(ServerManager.class, "[DB] camada de dados inicializada em " + dbPath);
                dbInitialised = true;
                long v = dbCommands.get_db_version();   // SELECT db_version FROM config WHERE id = 1
                setDbVersion(v);
            } catch (Exception e) {
                Log.error(ServerManager.class, "[DB] erro a inicializar camada de dados: " + e.getMessage());
                throw new RuntimeException("Falha a inicializar DB/DAOs/AuthService", e);
            }
        }
    }

    // IQUIZSERVER INTERFACE
    @Override
    public IQuestionService getQuestionService() {
        //Inicializa a BD se necessário, e configura os serviços necessários para auth, perg e respostas
        initDatabaseLayerIfNeeded();
        return questionService; //retorna referencia do serviço de perguntas
    }

    @Override
    public IAnswerService getAnswerService() {
        initDatabaseLayerIfNeeded();
        return answerService; //retorna referencia do serviço de respostas
    }


    @Override
    public boolean isUserLogged(long id) {
        return activeSessions.containsKey(id);
    }

    @Override
    public void registerLogin(long id, String sessionId) {
        activeSessions.put(id, sessionId);
    }

    @Override
    public void unregisterLogin(long id) {
        activeSessions.remove(id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String serverTcpIp() {
        return ip;
    }

    @Override
    public int serverTcpPort() {
        return clientPort;
    }

    @Override
    public int dbCopyPort() {
        return dbCopyPort;
    }

    @Override
    public String directoryHost() {
        return dirHost;
    }

    @Override
    public int directoryPort() {
        return dirPort;
    }

    @Override
    public String multicastGroup() {
        return multicastGroup;
    }

    @Override
    public int multicastPort() {
        return multicastPort;
    }

    @Override
    public NetworkInterface multicastInterface() {
        return multicastInterface;
    }

    @Override
    public void shutdownServer() throws Exception {close();}

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public IAuthService getAuthService() {
        initDatabaseLayerIfNeeded();
        return authService;
    }

    @Override
    public long dbVersion() {
        return dbVersion.get();
    }

    @Override
    public Path dbPath() {
        return dbPath;
    }

    @Override
    public DbCommands getDb() {
        initDatabaseLayerIfNeeded();
        return dbCommands;
    }

    @Override
    public void registerClientConnection(long userId, NetworkTcpConnection conn) {
        if (conn == null) return;
        activeClientConnections.put(userId, conn);
    }

    @Override
    public void unregisterClientConnection(long userId) {
        activeClientConnections.remove(userId);
    }

    @Override
    public void sendToUser(long userId, pt.isec.common.messages.TcpMessage<?> msg) {
        NetworkTcpConnection c = activeClientConnections.get(userId);
        if (c == null) return;
        try {
            c.sendMessage(msg);
        } catch (Exception e) {
            Log.error(ServerManager.class, "[ServerManager] Failed to send message to user " + userId + ": " + e.getMessage());
        }
    }

    @Override
    public boolean tryLockCopy() {
        return copying.compareAndSet(false, true);
    }

    @Override
    public void unlockCopy() {
        copying.set(false);
    }

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }

    @Override
    public void setPrimary(String ip, int port) {
        boolean newIsPrimary = this.ip.equals(ip) && this.clientPort == port;
        if (this.isPrimary != newIsPrimary) {
            this.isPrimary = newIsPrimary;
            System.out.println("[ServerManager] role changed to " +
                    (isPrimary ? "PRIMARY" : "BACKUP"));
            // no refreshDbPath here – keep using the same db file
        }
    }

    @Override
    public void setDbVersion(long v) {
        long old = dbVersion.getAndSet(v);
        if (old != v) {
            System.out.printf("[ServerManager] dbVersion changed: %d -> %d%n", old, v);
        }
    }

    @Override
    public BlockingQueue<List<String>> queue(){return sqlToBroadcast;}
    // CLOSEABLE INTERFACE
    @Override
    public void close() throws Exception {
        // 1) sinal global – todas as threads vão começar a terminar
        running = false;

        // 2) reduzir o timeout de leitura das ligações TCP activas
        //    Isto não fecha o socket; apenas faz com que o readObject()
        //    lance SocketTimeoutException em vez de ficar bloqueado para sempre.
        for (NetworkTcpConnection conn : activeClientConnections.values()) {
            try {
                conn.setReadTimeout(Duration.ofSeconds(1)); // timeout pequeno
            } catch (IOException ignored) {}
        }

        // 3) verificamos qual foi a thread que chamou o close()
        Thread current = Thread.currentThread();

        if (tDirectoryHeartbeat != null && current != tDirectoryHeartbeat) {
            try { tDirectoryHeartbeat.join(); } catch (InterruptedException ignored) {}
        }

        if (threadClusterHeartbeat != null && current != threadClusterHeartbeat) {
            try { threadClusterHeartbeat.join(); } catch (InterruptedException ignored) {}
        }

        if (threadClientListener != null && current != threadClientListener) {
            try { threadClientListener.join(); } catch (InterruptedException ignored) {}
        }
        Log.info(ServerManager.class, "Shutdown completo.");
    }

    // RUNNABLE INTERFACE
    @Override
    public void run() {
        start();
    }

    private void start() {
        tDirectoryHeartbeat = new Thread(new DirectoryHeartbeatThread(this), "directory-heartbeat");
        threadClusterHeartbeat = new Thread(new ClusterHeartbeatThread(this),   "cluster-heartbeat");
        threadClientListener = new Thread(new ClientListenerThread(this),     "client-listener");

        threadClusterHeartbeat.start();
        tDirectoryHeartbeat.start();
        threadClientListener.start();
    }
}
