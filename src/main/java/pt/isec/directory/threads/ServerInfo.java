package pt.isec.directory.threads;

/**
 * Holds runtime information about a quiz server registered in the directory.
 */
public class ServerInfo {
    private final int tcpPort;
    private final int udpPort;
    private final String id;
    private final String ip;
    private long lastSeenMillis;

    /**
     * Creates a new {@link ServerInfo} instance.
     *
     * @param id      unique server identifier
     * @param ip      server IP address
     * @param tcpPort TCP port used to accept client connections
     * @param udpPort UDP port used by the server for directory communications
     */
    public ServerInfo(String id, String ip, int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
        this.udpPort = udpPort;
    }

    /**
     * Human-friendly name in the form {@code "servidor<port>"} (e.g. 5002 → "servidor5002").
     *
     * @return display name of this server
     */
    public String displayName() { return "servidor" + tcpPort; }

    /**
     * Returns the TCP endpoint as {@code "<ip>:<port>"}.
     *
     * @return TCP endpoint string
     */
    public String tcpEndpoint() { return ip + ":" + tcpPort; }

    /**
     * Returns the timestamp (epoch millis) when this server was last seen.
     *
     * @return last seen timestamp in milliseconds
     */
    public long getLastSeenMillis(){ return lastSeenMillis; }

    /**
     * Updates the timestamp when this server was last seen.
     *
     * @param v epoch milliseconds
     */
    public void setLastSeenMillis(long v){ this.lastSeenMillis = v; }

    public int getTcpPort() {return tcpPort;}
    public String getId() {return id;}
    public String getIp() {return ip;}
    public int getUdpPort(){ return udpPort;}
}
