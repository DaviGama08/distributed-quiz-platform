package pt.isec.server;

import pt.isec.server.db.DbCommands;
import pt.isec.server.db.DbCreate;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.services.question.AnswerService;
import pt.isec.server.services.question.QuestionService;
import pt.isec.server.threads.ClusterHeartbeatThread;
import pt.isec.server.threads.ClientListenerThread;
import pt.isec.server.threads.DirectoryHeartbeatThread;

import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ServerManager implements IServerManager, Runnable, AutoCloseable {
    private volatile boolean dbInitialised = false;
    private DbCommands dbCommands;

    private AuthService authService;
    private QuestionService questionService;
    private AnswerService answerService;

    private final List<String> pendingSqlUpdates = Collections.synchronizedList(new ArrayList<>());
    private final Map<Long, String> activeSessions = new ConcurrentHashMap<>();

    private final String id;
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
        this.ip = InetAddress.getLocalHost().getHostAddress(); //IP local desta máquina
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.dataDir = (initialDbPath.getParent() != null)
                ? initialDbPath.getParent().toAbsolutePath()
                : Paths.get(".").toAbsolutePath();

        this.isPrimary = false;
        refreshDbPath();

        this.multicastInterface = resolveMulticastInterface(mcIfIp);
        if (this.multicastInterface == null)
            throw new IllegalArgumentException("Interface de rede inválida para IP/criterio: " + mcIfIp);

        System.out.println("[MC] usando interface: " + multicastInterface.getName());
        System.out.println("[DB] path inicial=" + dbPath + " (versão=" + dbVersion.get() + ", role=BACKUP)");
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
                if (a instanceof java.net.Inet4Address) {
                    return ni;
                }
            }
        }
        return null;
    }
//atualiza o caminho para o ficheiro da base de dados, construindo um novo nome que inclui a versão
// da base de dados e se é a principal ou uma cópia de segurança.
    private synchronized void refreshDbPath() {
        String versionStr = String.format("%02d", dbVersion.get());
        String name = "quiz-" + versionStr + (isPrimary ? ".db" : "-backup.db");
        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        System.out.println("[DB] agora a usar: " + this.dbPath +
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
                this.dbCommands = new DbCommands("jdbc:sqlite:" + this.dbPath.toAbsolutePath());
                this.authService = new AuthService(dbCommands);

                // inicializa novos serviços
                this.questionService = new QuestionService(this, dbCommands);
                this.answerService = new AnswerService(this, dbCommands);

                System.out.println("[DB] camada de dados inicializada em " + dbPath);
                dbInitialised = true;
            } catch (Exception e) {
                System.err.println("[DB] erro a inicializar camada de dados: " + e.getMessage());
                throw new RuntimeException("Falha a inicializar DB/DAOs/AuthService", e);
            }
        }
    }

    // IQUIZSERVER INTERFACE
    @Override
    public QuestionService getQuestionService() {
        //Inicializa a BD se necessário, e configura os serviços necessários para auth, perg e respostas
        initDatabaseLayerIfNeeded();
        return questionService; //retorna referencia do serviço de perguntas
    }

    @Override
    public AnswerService getAnswerService() {
        initDatabaseLayerIfNeeded();
        return answerService; //retorna referencia do serviço de respostas
    }

    //Para adicionar tarefas SQL à lista de pendentes
    @Override
    public void recordSqlUpdate(String sql) {
        if (sql != null && !sql.isBlank())
            //Adiciona à lista tarefas com a segurança da concorrência (syncronizedList)
            pendingSqlUpdates.add(sql);
    }

    //Para retornar as tarefas da lista de pendentes
    @Override
    public List<String> pollPendingSqlUpdates() {
        //Bloqueia para concorrência
        synchronized (pendingSqlUpdates) {
            //Faz copia da lista
            List<String> copy = new ArrayList<>(pendingSqlUpdates);
            pendingSqlUpdates.clear();//limpa a lista para receber novos pedidos
            return copy; //retorna a cópia da lista
        }
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
    public void stopRunning(boolean v) throws Exception {
        running = v;
        threadClientListener.join();
        System.out.println("[QuizServer] tClientListener encerrada");
        threadClusterHeartbeat.join();
        System.out.println("[QuizServer] tClusterHeartbeat  encerrada");
        System.out.println("[QuizServer] tDirectoryHeartbeat encerrada");
        close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public AuthService getAuthService() {
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
            System.err.println("[ServerManager] Failed to send message to user " + userId + ": " + e.getMessage());
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
            refreshDbPath();
        } else {
            this.isPrimary = newIsPrimary;
        }
    }

    @Override
    public void setDbVersion(long v) {
        if (v < 0) v = 0;
        long old = dbVersion.getAndSet(v);
        if (old != v) refreshDbPath();
    }

    // CLOSEABLE INTERFACE
    @Override
    public void close() throws Exception {
        if (threadClusterHeartbeat != null)   threadClusterHeartbeat.interrupt();
        if (tDirectoryHeartbeat != null) tDirectoryHeartbeat.interrupt();
        if (threadClientListener != null)     threadClientListener.interrupt();
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
