package pt.isec.directory;

public class ServerInfo{
    private final int tcpPort;
    private final String id;
    private final String ip;
    private long lastSeenMillis;

    public ServerInfo(String id, String ip, int tcpPort, int version) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
    }

    /** Nome humano: "servidor<porto>" (ex.: 5002 → "servidor5002"). */
    public String displayName() { return "servidor" + tcpPort; }

    public String tcpEndpoint() { return ip + ":" + tcpPort; }

    public long getLastSeenMillis(){ return lastSeenMillis; }
    public void setLastSeenMillis(long v){ this.lastSeenMillis = v; }

    public int getTcpPort() {return tcpPort;}
    public String getId() {return id;}
    public String getIp() {return ip;}

}
