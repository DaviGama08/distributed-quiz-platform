package pt.isec.directory;

public class ServerInfo{
    private final int tcpPort;
    private final String id;
    private final String ip;

    public ServerInfo(String id, String ip, int tcpPort) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
    }
    public String tcpEndpoint() { return ip + ":" + tcpPort; }
}