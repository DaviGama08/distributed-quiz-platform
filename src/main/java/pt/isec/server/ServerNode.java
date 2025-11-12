package pt.isec.server;

import pt.isec.server.threads.client.TcpClientAcceptorRunnable;
import pt.isec.server.threads.DirectoryHeartbeatRunnable;
import pt.isec.server.threads.MulticastRunnable;
import pt.isec.server.threads.DbCopyAcceptorRunnable;

import java.net.*;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ServerNode implements IServerNode, Runnable, AutoCloseable {

    private final String id;
    private final String ip;
    private final int clientPort;
    private final int dbCopyPort;

    private final String dirHost;
    private final int dirPort;

    private final String mcGroup = "230.30.30.30";
    private final int mcPort = 3030;
    private NetworkInterface mcIf;

    private final Path dbPath;

    private volatile boolean running = true;
    private volatile boolean isPrimary = false;
    private volatile Principal master;
    private final AtomicLong dbVersion = new AtomicLong(0);

    private Thread tMulticastReceiver, tDirectoryHB, tTcpClient, tDbCopyAcceptor;

    public ServerNode(String dirHost, int dirPort, String mcIfIp,
                      int clientPort, int dbCopyPort, Path dbPath) throws Exception {
        this.id = UUID.randomUUID().toString();
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;
        this.dbPath = dbPath;

        // === escolher interface de multicast ===
        this.mcIf = resolveMulticastInterface(mcIfIp);
        if (this.mcIf == null)
            throw new IllegalArgumentException("Interface de rede inválida para IP/criterio: " + mcIfIp);

        System.out.println("[MC] usando interface: " + mcIf.getName());
    }

    private static NetworkInterface resolveMulticastInterface(String mcIfIp) throws Exception {
        // "AUTO" => tenta escolher automaticamente uma interface válida
        if (mcIfIp == null || mcIfIp.isBlank() || "AUTO".equalsIgnoreCase(mcIfIp)) {
            return pickDefaultMulticastInterface();
        }
        // tentar por IP explícito
        InetAddress addr = InetAddress.getByName(mcIfIp);
        NetworkInterface ni = NetworkInterface.getByInetAddress(addr);
        if (ni != null && ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
            return ni;
        }
        // fallback automático
        return pickDefaultMulticastInterface();
    }

    private static NetworkInterface pickDefaultMulticastInterface() throws Exception {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast())
                continue;
            // preferir interfaces com IPv4
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
    @Override public boolean isPrimary() { return isPrimary; }
    @Override public void setPrimary(String ip, int port) {
        master = new Principal(ip, port);
        this.isPrimary = this.ip.equals(ip) && this.clientPort == port;
    }
    @Override public long dbVersion() { return dbVersion.get(); }
    @Override public void setDbVersion(long v) { dbVersion.set(v); }
    @Override public Path dbPath() { return dbPath; }

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
        tTcpClient         = new Thread(new pt.isec.server.threads.client.TcpClientAcceptorRunnable(this), "tcp-client");
        tDbCopyAcceptor    = new Thread(new pt.isec.server.threads.DbCopyAcceptorRunnable(this), "dbcopy-acceptor");

        tMulticastReceiver.start();
        tDirectoryHB.start();
        tTcpClient.start();
        tDbCopyAcceptor.start();
    }
}
