package pt.isec.directory;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.Threads.UdpListenerRunnable;
import pt.isec.directory.Threads.WorkerRunnable;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DirectoryService implements IDirectoryService{
    private final int udpPort;
    private final int queueCapacity;
    private volatile boolean running = true;

    private final DatagramSocket socket;
    private final int maxPacketSize;

    private final BlockingQueue<UdpMessage> queue;

    //Lista de servidores
    private final ConcurrentMap<String, ServerInfo> servers = new ConcurrentHashMap<>();

    private final Map<String, ServerInfo> serversOrdered    = new LinkedHashMap<>();
    private final Object serversLock                        = new Object();

    // TTL (17s) segundo enunciado
    private final long TTLMILLIS = 17_000L;

    //Threads
    private final Thread tListener;
    private final Thread tWorker;

    public DirectoryService(int udpPort, int queueCapacity, int maxPacketSize)throws SocketException {
        this.udpPort       = udpPort;
        this.queueCapacity = queueCapacity;
        this.maxPacketSize = maxPacketSize;

        this.socket        = new DatagramSocket(udpPort);

        this.queue         = new ArrayBlockingQueue<>(queueCapacity);

        tListener          = new Thread(new UdpListenerRunnable(this));
        tWorker            = new Thread(new WorkerRunnable(this));
    }

    public void start() {
        tListener.start();
        tWorker.start();
    }

    public void stop() {
        running = false;
        socket.close(); // desbloqueia receives
        tListener.interrupt();
        tWorker.interrupt();
    }

    @Override public int getUdpPort() {return udpPort;}
    @Override public int getQueueCapacity() {return queueCapacity;}
    @Override public boolean getRunning() {return running;}
    @Override public DatagramSocket getSocket() {return socket;}
    @Override public int getMaxPacketSize() {return maxPacketSize;}
    @Override public ConcurrentMap<String, ServerInfo> getServers() {return servers;}
    @Override public Map<String, ServerInfo> getServersOrdered() { return serversOrdered; }
    @Override public Object serversLock() { return serversLock; }
    @Override public long getTtlMillis() { return TTLMILLIS; }
    @Override public BlockingQueue<UdpMessage> queue() {return queue;}
}
