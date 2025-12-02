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

/**
 * Main server coordinator.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Manages database initialization and access</li>
 *   <li>Creates and exposes application services (auth, questions, answers)</li>
 *   <li>Coordinates cluster behaviour (primary/backup, multicast heartbeats, DB copies)</li>
 *   <li>Tracks active sessions and TCP client connections</li>
 *   <li>Starts and stops background threads for directory, cluster and client listeners</li>
 * </ul>
 */
public class ServerManager implements IServerManager, IQuestionAnswerContext {
    private volatile boolean dbInitialised = false;
    private DbCommands dbCommands;

    // Exposed services
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

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new server manager.
     *
     * @param dirHost       directory service host
     * @param dirPort       directory service port
     * @param mcIfIp        multicast interface IP or {@code "AUTO"}
     * @param clientPort    TCP port used to accept client connections
     * @param dbCopyPort    TCP port used to handle DB copy requests
     * @param initialDbPath initial DB path hint (used to derive {@code dataDir})
     * @throws Exception if resolving multicast interface fails
     */
    public ServerManager(String dirHost, int dirPort, String mcIfIp,
                         int clientPort, int dbCopyPort, Path initialDbPath) throws Exception {
        this.id = UUID.randomUUID().toString();
        this.serverIdShort = id.substring(0, 8);
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.dataDir = (initialDbPath.getParent() != null)
                ? initialDbPath.getParent().toAbsolutePath()
                : Paths.get(".").toAbsolutePath();

        this.isPrimary = false;

        this.multicastInterface = resolveMulticastInterface(mcIfIp);
        if (this.multicastInterface == null) {
            throw new IllegalArgumentException("Interface de rede inválida para IP/critério: " + mcIfIp);
        }

        Log.info(ServerManager.class,
                "[MC] interface multicast seleccionada: %s", multicastInterface.getName());
        Log.info(ServerManager.class,
                "[DB] caminho inicial da BD=%s (versão=%d, role=BACKUP)",
                dbPath, dbVersion.get());
    }

    /* ======================= STATIC HELPERS ======================= */

    /**
     * Resolves the multicast network interface for a given IP or uses "AUTO" selection.
     *
     * @param mcIfIp IP address of desired interface or {@code "AUTO"}
     * @return a multicast-capable {@link NetworkInterface} or {@code null} if none found
     * @throws Exception if address resolution fails
     */
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

    /**
     * Picks a default multicast-capable network interface.
     *
     * @return a multicast-capable non-loopback interface or {@code null} if none found
     * @throws Exception if listing interfaces fails
     */
    private static NetworkInterface pickDefaultMulticastInterface() throws Exception {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) {
                continue;
            }
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

    /**
     * Finds the most recently modified {@code .db} file in a directory.
     *
     * @param dir directory to search
     * @return newest DB path or {@code null} if none found
     * @throws IOException if listing or reading attributes fails
     */
    private static Path findNewestDbInDir(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".db"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    /* ======================= DATABASE PATH CHOOSING ======================= */

    /**
     * Initializes DB path when starting as primary node.
     * <p>
     * If an existing DB is found in {@code dataDir}, uses the newest one;
     * otherwise creates a new file name.
     *
     * @throws IOException if directory access fails
     */
    public synchronized void initDbPathAsPrincipalOnStartup() throws IOException {
        if (this.dbPath != null) {
            return; // already chosen
        }

        Path newest = findNewestDbInDir(dataDir);
        if (newest != null) {
            this.dbPath = newest.toAbsolutePath();
            Log.info(ServerManager.class,
                    "[DB] PRIMARY: a usar a BD mais recente no diretório: %s", this.dbPath);
        } else {
            String name = String.format("quiz-%s.db", serverIdShort);
            this.dbPath = dataDir.resolve(name).toAbsolutePath();
            Log.info(ServerManager.class,
                    "[DB] PRIMARY: nenhuma BD encontrada; novo ficheiro será: %s", this.dbPath);
        }
    }

    /**
     * Initializes DB path when starting as backup node.
     * <p>
     * Uses a local DB file name derived from the server identifier.
     */
    public synchronized void initDbPathAsBackupOnStartup() {
        if (this.dbPath != null) {
            return; // already chosen
        }

        String name = String.format("quiz-%s.db", serverIdShort);
        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        Log.info(ServerManager.class,
                "[DB] BACKUP: BD local deste servidor será: %s", this.dbPath);
    }

    /**
     * Rebuilds the DB path using the current role (primary/backup) and short server id.
     * <p>
     * Note: Currently not used on role change to preserve the same DB file.
     */
    @SuppressWarnings("unused")
    private synchronized void refreshDbPath() {
        String role = isPrimary ? "primary" : "backup";
        String name = String.format("quiz-%s-%s.db", role, serverIdShort);

        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        Log.info(ServerManager.class,
                "[DB] agora a usar: %s (role=%s, versão=%d)",
                this.dbPath, (isPrimary ? "PRIMARY" : "BACKUP"), dbVersion.get());
    }

    /* ======================= DB INITIALIZATION ======================= */

    /**
     * Lazily initializes the database layer (schema + DbCommands + services).
     * Safe to call multiple times.
     */
    @Override
    public void initDatabaseLayerIfNeeded() {
        if (dbInitialised) {
            return;
        }
        synchronized (this) {
            if (dbInitialised) {
                return;
            }
            try {
                DbCreate.createIfMissing(this.dbPath, "/db/schema.sql");
                this.dbCommands = new DbCommands("jdbc:sqlite:" + this.dbPath.toAbsolutePath(), this::setDbVersion);
                IQuestionAnswerContext qaContext = this;

                this.authService = new AuthService(qaContext, dbCommands);
                this.questionService = new QuestionService(qaContext, dbCommands);
                this.answerService   = new AnswerService(qaContext, dbCommands);

                Log.info(ServerManager.class,
                        "[DB] camada de dados inicializada em %s", dbPath);
                dbInitialised = true;

                long v = dbCommands.get_db_version();   // SELECT db_version FROM config WHERE id = 1
                setDbVersion(v);                        // logging of version change occurs in setDbVersion
            } catch (Exception e) {
                Log.error(ServerManager.class,
                        "[DB] erro a inicializar a camada de dados: %s", e.getMessage());
                throw new RuntimeException("Falha a inicializar DB/DAOs/AuthService", e);
            }
        }
    }

    /* ======================= SERVICES ======================= */

    @Override
    public IAuthService getAuthService() {
        initDatabaseLayerIfNeeded();
        return authService;
    }

    @Override
    public IQuestionService getQuestionService() {
        initDatabaseLayerIfNeeded();
        return questionService;
    }

    @Override
    public IAnswerService getAnswerService() {
        initDatabaseLayerIfNeeded();
        return answerService;
    }

    @Override
    public DbCommands getDb() {
        initDatabaseLayerIfNeeded();
        return dbCommands;
    }

    /* ======================= SESSIONS / LOGIN ======================= */

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

    /* ======================= IDENTITY / NETWORK CONFIG ======================= */

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

    /* ======================= GLOBAL STATE ======================= */

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void shutdownServer() throws Exception {
        close();
    }

    @Override
    public long dbVersion() {
        return dbVersion.get();
    }

    @Override
    public void setDbVersion(long v) {
        long old = dbVersion.getAndSet(v);
        if (old != v) {
            Log.info(ServerManager.class,
                    "Versão da base de dados alterada: %d -> %d", old, v);
        }
    }

    @Override
    public Path dbPath() {
        return dbPath;
    }

    /* ======================= CLIENT CONNECTIONS ======================= */

    @Override
    public void registerClientConnection(long userId, NetworkTcpConnection conn) {
        if (conn == null) {
            return;
        }
        activeClientConnections.put(userId, conn);
    }

    @Override
    public void unregisterClientConnection(long userId) {
        activeClientConnections.remove(userId);
    }

    @Override
    public void sendToUser(long userId, pt.isec.common.messages.TcpMessage<?> msg) {
        NetworkTcpConnection c = activeClientConnections.get(userId);
        if (c == null) {
            return;
        }
        try {
            c.sendMessage(msg);
        } catch (Exception e) {
            Log.error(ServerManager.class,
                    "Falha ao enviar mensagem para o utilizador %d: %s",
                    userId, e.getMessage());
        }
    }

    /* ======================= REPLICATION / DB COPY ======================= */

    @Override
    public boolean tryLockCopy() {
        return copying.compareAndSet(false, true);
    }

    @Override
    public void unlockCopy() {
        copying.set(false);
    }

    @Override
    public BlockingQueue<List<String>> queue() {
        return sqlToBroadcast;
    }

    /* ======================= PRIMARY/BACKUP ROLE ======================= */

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }

    @Override
    public void setPrimary(String ip, int port) {
        boolean newIsPrimary = this.ip.equals(ip) && this.clientPort == port;
        if (this.isPrimary != newIsPrimary) {
            this.isPrimary = newIsPrimary;
            Log.info(ServerManager.class,
                    "Role alterado para %s", (isPrimary ? "PRIMARY" : "BACKUP"));
            // No refreshDbPath here – we keep using the same DB file
        }
    }

    /* ======================= LIFE CYCLE / THREADS ======================= */

    /**
     * Starts the main worker threads:
     * <ul>
     *   <li>Directory heartbeat</li>
     *   <li>Cluster heartbeat (multicast + DB copy)</li>
     *   <li>Client listener (TCP accept loop)</li>
     * </ul>
     */
    public void run() {
        tDirectoryHeartbeat   = new Thread(new DirectoryHeartbeatThread(this), "directory-heartbeat");
        threadClusterHeartbeat = new Thread(new ClusterHeartbeatThread(this),   "cluster-heartbeat");
        threadClientListener  = new Thread(new ClientListenerThread(this),     "client-listener");

        threadClusterHeartbeat.start();
        tDirectoryHeartbeat.start();
        threadClientListener.start();
    }

    /**
     * Performs a graceful shutdown:
     * <ol>
     *   <li>Signals global termination to all threads</li>
     *   <li>Reduces read timeouts of active TCP connections</li>
     *   <li>Waits for directory, cluster and client threads to finish</li>
     * </ol>
     *
     * @throws Exception if any close operation fails unexpectedly
     */
    public void close() throws Exception {
        // 1) global signal – all threads start terminating
        running = false;

        // 2) reduce read timeout of active TCP connections
        for (NetworkTcpConnection conn : activeClientConnections.values()) {
            try {
                conn.setReadTimeout(Duration.ofSeconds(1));
            } catch (IOException ignored) {}
        }

        // 3) wait for main threads to terminate (avoid joining the current thread)
        Thread current = Thread.currentThread();

        if (tDirectoryHeartbeat != null && current != tDirectoryHeartbeat) {
            try {
                tDirectoryHeartbeat.join();
            } catch (InterruptedException ignored) {}
        }

        if (threadClusterHeartbeat != null && current != threadClusterHeartbeat) {
            try {
                threadClusterHeartbeat.join();
            } catch (InterruptedException ignored) {}
        }

        if (threadClientListener != null && current != threadClientListener) {
            try {
                threadClientListener.join();
            } catch (InterruptedException ignored) {}
        }

        Log.info(ServerManager.class, "Shutdown completo do servidor.");
    }
}
