package pt.isec.directory;

public class ServerInfo{
    private final int tcpPort;
    private final String id;
    private final String ip;
    private int version;               // <- deixa de ser final para podermos atualizar no HB
    private long lastSeenMillis;

    public ServerInfo(String id, String ip, int tcpPort, int version) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
        this.version = version;
    }

    /** Nome humano: "servidor<porto>" (ex.: 5002 → "servidor5002"). */
    public String displayName() { return "servidor" + tcpPort; }

    public String tcpEndpoint() { return ip + ":" + tcpPort; }

    public long getLastSeenMillis(){ return lastSeenMillis; }
    public void setLastSeenMillis(long v){ this.lastSeenMillis = v; }

    public int getTcpPort() {return tcpPort;}
    public String getId() {return id;}
    public String getIp() {return ip;}

    public int getVersion() {return version;}
    public void setVersion(int v) { this.version = v; }
}
