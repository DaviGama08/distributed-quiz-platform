package pt.isec.directory;

import pt.isec.common.messages.UdpMessage;

import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface IDirectoryService {
    int getUdpPort();
    int getQueueCapacity();
    boolean getRunning();
    DatagramSocket getSocket();
    int getMaxPacketSize();

    ConcurrentMap<String, ServerInfo> getServers();

    Map<String, ServerInfo> getServersOrdered();
    Object serversLock();

    long getTtlMillis();

    BlockingQueue<UdpMessage> queue();
}
