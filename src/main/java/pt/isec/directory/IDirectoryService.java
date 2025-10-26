package pt.isec.directory;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.model.ServerInfo;

import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

public interface IDirectoryService {
    int udpPort();
    int queueCapacity();
    boolean isRunning();
    DatagramSocket socket();
    int maxPacketSize();

    ConcurrentMap<String, ServerInfo> servers();
    Map<String, ServerInfo> serversOrdered();
    Object serversLock();
    int serversCount();
    String masterServerUuid();
    int serverTcpPort(String uuid);
    int serverVersion(String uuid);
    void removeServersFromList(long currTime);

    long ttlMillis();

    BlockingQueue<UdpMessage> queue();
}
