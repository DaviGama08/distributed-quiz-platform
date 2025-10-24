package pt.isec.directory;

public class ServerInfo{
    private final int tcpPort;
    private final String id;
    private final String ip;

    public ServerInfo(int tcpPort, String id, String ip) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
    }
    public String tcpEndpoint() { return ip + ":" + tcpPort; }
}