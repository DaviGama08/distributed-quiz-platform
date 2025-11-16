package pt.isec.server;
import pt.isec.server.db.Db;
import pt.isec.server.db.dao.StudentDAO;
import pt.isec.server.db.dao.TeacherDAO;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.threads.client.TcpClientAcceptorRunnable;
import pt.isec.server.threads.DirectoryHeartbeatRunnable;
import pt.isec.server.threads.MulticastRunnable;
import pt.isec.server.threads.DbCopyAcceptorRunnable;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ServerNode implements IServerNode, Runnable, AutoCloseable {
    private volatile boolean dbInitialised = false;
    private Db db;
    private TeacherDAO teacherDAO;
    private StudentDAO studentDAO;
    private AuthService authService;

    private final String id;
    private final String ip;
    private final int clientPort;
    private final int dbCopyPort;

    private final String dirHost;
    private final int dirPort;

    private final String mcGroup = "230.30.30.30";
    private final int mcPort = 3030;
    private NetworkInterface mcIf;

    private final Path dataDir;
    private volatile Path dbPath;

    private volatile boolean running = true;
    private volatile boolean isPrimary = false;
    private volatile Principal master;
    private final AtomicLong dbVersion = new AtomicLong(0);

    private final AtomicBoolean copying = new AtomicBoolean(false);

    private Thread tMulticastReceiver, tDirectoryHB, tTcpClient, tDbCopyAcceptor;

    public ServerNode(String dirHost, int dirPort, String mcIfIp,
                      int clientPort, int dbCopyPort, Path initialDbPath) throws Exception {
        this.id = UUID.randomUUID().toString();
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.authService = new AuthService(teacherDAO, studentDAO);

        this.dataDir = (initialDbPath.getParent() != null) ? initialDbPath.getParent().toAbsolutePath()
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

    private static String two(long v) { return String.format("%02d", v); }

    private synchronized void refreshDbPath() {
        String name = "quiz-" + two(dbVersion.get()) + (isPrimary ? ".db" : "-backup.db");
        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        System.out.println("[DB] agora a usar: " + this.dbPath + " (role=" + (isPrimary? "PRIMARY":"BACKUP") + ", v=" + dbVersion.get() + ")");
    }

    @Override
    public void initDatabaseLayerIfNeeded() {
        // só o primeiro thread faz a inicialização
        if (dbInitialised)
            return;

        synchronized (this) {
            if (dbInitialised)
                return;

            try {
                // garante que o ficheiro .db existe e tem o schema
                pt.isec.server.db.DbFiles.createIfMissing(
                        this.dbPath,
                        "/db/schema.sql"
                );

                // cria helper Db apontando para o ficheiro atual
                this.db = new pt.isec.server.db.Db("jdbc:sqlite:" + this.dbPath.toAbsolutePath());

                // cria DAOs
                this.teacherDAO = new pt.isec.server.db.dao.TeacherDAO(db);
                this.studentDAO = new pt.isec.server.db.dao.StudentDAO(db);

                // AuthService com DAOs válidos
                this.authService = new pt.isec.server.services.auth.AuthService(teacherDAO, studentDAO);

                System.out.println("[DB] camada de dados inicializada em " + dbPath);
                dbInitialised = true;
            } catch (Exception e) {
                System.err.println("[DB] erro a inicializar camada de dados: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Falha a inicializar DB/DAOs/AuthService", e);
            }
        }
    }
    public Db getDb() {
        initDatabaseLayerIfNeeded();
        return db;
    }
    @Override public String id() { return id; }
    @Override public String ip() { return ip; }
    @Override public int clientPort() { return clientPort; }
    @Override public int dbCopyPort() { return dbCopyPort; }

    @Override public String directoryHost() { return dirHost; }
    @Override public int directoryPort() { return dirPort; }

    @Override public String mcGroup() { return mcGroup; }
    @Override public int mcPort() { return mcPort; }
    @Override public NetworkInterface mcIf() { return mcIf; }
    @Override public boolean isRunning() { return running; }

    @Override
    public AuthService getAuthService() {
        initDatabaseLayerIfNeeded();
        return authService;
    }

    @Override public boolean isPrimary() { return isPrimary; }
    @Override public void setPrimary(String ip, int port) {
        master = new Principal(ip, port);
        boolean newIsPrimary = this.ip.equals(ip) && this.clientPort == port;
        if (this.isPrimary != newIsPrimary) {
            this.isPrimary = newIsPrimary;
            refreshDbPath();
        } else {
            this.isPrimary = newIsPrimary;
        }
    }

    @Override public long dbVersion() { return dbVersion.get(); }
    @Override public void setDbVersion(long v) {
        if (v < 0) v = 0;
        long old = dbVersion.getAndSet(v);
        if (old != v) refreshDbPath();
    }

    @Override public Path dbPath() { return dbPath; }

    // “fusível” para MulticastRunnable
    public boolean tryLockCopy()  { return copying.compareAndSet(false, true); }
    public void    unlockCopy()   { copying.set(false); }

    @Override public void run() { start(); }

    @Override
    public void close() throws Exception {
        running = false;
        if (tMulticastReceiver != null)  tMulticastReceiver.interrupt();
        if (tDirectoryHB != null)        tDirectoryHB.interrupt();
        if (tTcpClient != null)          tTcpClient.interrupt();
        if (tDbCopyAcceptor != null)     tDbCopyAcceptor.interrupt();
    }

    public void start() {
        tDirectoryHB       = new Thread(new DirectoryHeartbeatRunnable(this), "directory-hb");
        tMulticastReceiver = new Thread(new MulticastRunnable(this), "multicast-receiver");
        tTcpClient         = new Thread(new TcpClientAcceptorRunnable(this), "tcp-client");
        tDbCopyAcceptor    = new Thread(new DbCopyAcceptorRunnable(this), "dbcopy-acceptor");

        tMulticastReceiver.start();
        tDirectoryHB.start();
        tTcpClient.start();
        tDbCopyAcceptor.start();
    }
}
