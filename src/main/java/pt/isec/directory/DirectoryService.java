package pt.isec.directory;

import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DirectoryService {
    private static class ServerInfo{
        private final int tcpPort;
        private final String serverId;
        private final String tcpIp;

        public ServerInfo(int tcpPort, String serverId, String tcpIp) {
            this.tcpPort = tcpPort;
            this.serverId = serverId;
            this.tcpIp = tcpIp;
        }
        String tcpEndpoint() { return tcpIp + ":" + tcpPort; }
    }

    private final int udpPort;
    private final int queueCapacity;

    private DatagramSocket socket;
    private final int maxPacketSize;

    //Lista de servidores
    private final ConcurrentMap<String, ServerInfo> servers = new ConcurrentHashMap<>();

    //Threads
    private Thread tListener;
    private Thread tMetrics;
    private Thread tWorker;
    private Thread tReaper;

    public DirectoryService(int udpPort, int queueCapacity, int maxPacketSize) {
        this.udpPort = udpPort;
        this.queueCapacity = queueCapacity;
        this.maxPacketSize = maxPacketSize;
    }
}
