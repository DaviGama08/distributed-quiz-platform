package pt.isec.server;

import pt.isec.server.db.Db;
import pt.isec.server.db.DbFiles;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class QuizServer implements IQuizServer, Runnable, AutoCloseable {
    private volatile boolean dbInitialised = false;
    private Db db;
    private AuthService authService;

    private final String id;
    private final String ip;
    private final int clientPort;
    private final int dbCopyPort;

    private QuestionService questionService;
    private AnswerService answerService;
    private final List<String> pendingSqlUpdates = Collections.synchronizedList(new ArrayList<>());


    private final String dirHost;
    private final int dirPort;

    private final String mcGroup = "230.30.30.30";
    private final int mcPort = 3030;
    private NetworkInterface mcIf;

    private final Path dataDir;
    private volatile Path dbPath;

    private volatile boolean running = true;
    private volatile boolean isPrimary = false;

    private final AtomicLong dbVersion = new AtomicLong(0);

    private final AtomicBoolean copying = new AtomicBoolean(false);

    private Thread tClusterHeartbeat, tDirectoryHeartbeat, tClientListener;

    public QuizServer(String dirHost, int dirPort, String mcIfIp,
                      int clientPort, int dbCopyPort, Path initialDbPath) throws Exception {
        this.id = UUID.randomUUID().toString();
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.dataDir = (initialDbPath.getParent() != null)
                ? initialDbPath.getParent().toAbsolutePath()
                : Paths.get(".").toAbsolutePath();

        this.isPrimary = false;
        refreshDbPath();

        this.mcIf = resolveMulticastInterface(mcIfIp);
        if (this.mcIf == null)
            throw new IllegalArgumentException("Interface de rede inválida para IP/criterio: " + mcIfIp);

        System.out.println("[MC] usando interface: " + mcIf.getName());
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
                DbFiles.createIfMissing(this.dbPath, "/db/schema.sql");
                this.db = new Db("jdbc:sqlite:" + this.dbPath.toAbsolutePath());
                this.authService = new AuthService(db);

                // inicializa novos serviços
                this.questionService = new QuestionService(this, db);
                this.answerService = new AnswerService(this, db);

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
    public QuestionService getQuestionService() { initDatabaseLayerIfNeeded(); return questionService; }
    @Override
    public AnswerService getAnswerService() { initDatabaseLayerIfNeeded(); return answerService; }
    @Override
    public void recordSqlUpdate(String sql) {
        if (sql != null && !sql.isBlank())
            pendingSqlUpdates.add(sql);
    }
    @Override
    public List<String> pollPendingSqlUpdates() {
        synchronized (pendingSqlUpdates) {
            List<String> copy = new ArrayList<>(pendingSqlUpdates);
            pendingSqlUpdates.clear();
            return copy;
        }
    }

    @Override public String id() { return id; }
    @Override public String serverTcpIp() { return ip; }
    @Override public int serverTcpPort() { return clientPort; }
    @Override public int dbCopyPort() { return dbCopyPort; }

    @Override public String directoryHost() { return dirHost; }
    @Override public int directoryPort() { return dirPort; }

    @Override public String multicastGroup() { return mcGroup; }
    @Override public int multicastPort() { return mcPort; }
    @Override public NetworkInterface multicastInterface() { return mcIf; }

    @Override public void setRunning(boolean v) throws Exception {
        running = v;
        tClientListener.join();
        System.out.println("[QuizServer] tClientListener encerrada");
        tClusterHeartbeat.join();
        System.out.println("[QuizServer] tClusterHeartbeat  encerrada");
        System.out.println("[QuizServer] tDirectoryHeartbeat encerrada");
        close();
    }
    @Override public boolean isRunning() { return running; }

    @Override public AuthService getAuthService() {initDatabaseLayerIfNeeded(); return authService;}
    @Override public long dbVersion() { return dbVersion.get(); }
    @Override public Path dbPath() { return dbPath; }
    @Override public Db getDb() {initDatabaseLayerIfNeeded(); return db;}

    @Override public boolean tryLockCopy() { return copying.compareAndSet(false, true); }
    @Override public void unlockCopy() { copying.set(false); }

    @Override public boolean isPrimary() { return isPrimary; }
    @Override public void setPrimary(String ip, int port) {
        boolean newIsPrimary = this.ip.equals(ip) && this.clientPort == port;
        if (this.isPrimary != newIsPrimary) {
            this.isPrimary = newIsPrimary;
            refreshDbPath();
        } else {
            this.isPrimary = newIsPrimary;
        }
    }

    @Override public void setDbVersion(long v) {
        if (v < 0) v = 0;
        long old = dbVersion.getAndSet(v);
        if (old != v) refreshDbPath();
    }

    // CLOSEABLE INTERFACE
    @Override
    public void close() throws Exception {
        if (tClusterHeartbeat != null)   tClusterHeartbeat.interrupt();
        if (tDirectoryHeartbeat != null) tDirectoryHeartbeat.interrupt();
        if (tClientListener != null)     tClientListener.interrupt();
    }

    // RUNNABLE INTERFACE
    @Override public void run() { start(); }

    private void start() {
        tDirectoryHeartbeat = new Thread(new DirectoryHeartbeatThread(this), "directory-heartbeat");
        tClusterHeartbeat   = new Thread(new ClusterHeartbeatThread(this),   "cluster-heartbeat");
        tClientListener     = new Thread(new ClientListenerThread(this),     "client-listener");

        tClusterHeartbeat.start();
        tDirectoryHeartbeat.start();
        tClientListener.start();
    }
}
