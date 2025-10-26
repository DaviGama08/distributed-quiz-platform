package pt.isec.directory.model;

public class ServerInfo{
    private final int tcpPort;
    private final String id;
    private final String ip;
    private final int version;
    private long lastSeenMillis;

    public ServerInfo(String id, String ip, int tcpPort, int version) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
        this.version = version;
    }
    public String tcpEndpoint() { return ip + ":" + tcpPort; }
    public long getLastSeenMillis(){ return lastSeenMillis; }
    public void setLastSeenMillis(long v){ this.lastSeenMillis = v; }
    public int getTcpPort() {return tcpPort;}
    public String getId() {return id;}
    public String getIp() {return ip;}
    public int getVersion() {return version;}
}