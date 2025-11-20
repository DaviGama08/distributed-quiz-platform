package pt.isec.directory;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.threads.MetricsThread;
import pt.isec.directory.threads.ReaperThread;
import pt.isec.directory.threads.UdpListenerThread;
import pt.isec.directory.threads.WorkerThread;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DirectoryManager implements IDirectoryManager {
    private final int udpPort;
    private final int queueCapacity;
    private volatile boolean running = true;

    private DatagramSocket socket;
    private BlockingQueue<UdpMessage> queue;
    private final int maxPacketSize;

    private final ConcurrentMap<String, ServerInfo> servers = new ConcurrentHashMap<>();
    private final Map<String, ServerInfo> serversOrdered    = new LinkedHashMap<>();
    private final Object serversLock                        = new Object();

    private final long ttlMs;
    private final long reaperEveryMs;
    private final long metricsEveryMs;

    private static final long DEFAULT_TTL_MS        = 17_000;
    private static final long DEFAULT_REAPER_EVERY  = 2_000;
    private static final long DEFAULT_METRICS_EVERY = 5_000;

    private Thread tListener;
    private Thread tReaper;
    private Thread tMetrics;
    private final Thread[] tWorkers;

    public DirectoryManager(int udpPort, int queueCapacity, int maxPacketSize) {
        this(udpPort, queueCapacity, maxPacketSize,
                4,
                DEFAULT_TTL_MS, DEFAULT_REAPER_EVERY, DEFAULT_METRICS_EVERY);
    }

    public DirectoryManager(int udpPort,
                            int queueCapacity,
                            int maxPacketSize,
                            int maxWorkers,
                            long ttlMs, long reaperEveryMs, long metricsEveryMs) {
        this.udpPort       = udpPort;
        this.queueCapacity = queueCapacity;
        this.maxPacketSize = Math.max(512, Math.min(65535, maxPacketSize));
        int workers        = Math.max(1, Math.min(64, maxWorkers));
        this.tWorkers      = new Thread[workers];
        this.ttlMs         = ttlMs;
        this.reaperEveryMs = reaperEveryMs;
        this.metricsEveryMs= metricsEveryMs;
    }

    public void start() {
        System.out.println("Starting DirectoryService...");
        try {
            this.socket = new DatagramSocket(udpPort);
        } catch (SocketException e) {
            throw new RuntimeException("Não foi possível abrir o socket UDP na porta " + udpPort, e);
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);

        tListener = new Thread(new UdpListenerThread(this), "dir-udp_listener");
        tListener.start();

        for (int i = 0; i < tWorkers.length; i++) {
            tWorkers[i] = new Thread(new WorkerThread(this), "dir-worker_" + i);
            tWorkers[i].start();
        }

        tReaper  = new Thread(new ReaperThread(this, reaperEveryMs), "dir-reaper");
        tReaper.start();

        tMetrics = new Thread(new MetricsThread(this, metricsEveryMs), "dir-metrics");
        tMetrics.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (tListener != null) tListener.interrupt();
        for (Thread t : tWorkers) if (t != null) t.interrupt();
        if (tReaper != null) tReaper.interrupt();
        if (tMetrics != null) tMetrics.interrupt();
    }

    @Override public int udpPort() { return udpPort; }
    @Override public int queueCapacity() { return queueCapacity; }
    @Override public boolean isRunning() { return running; }
    @Override public DatagramSocket socket() { return socket; }
    @Override public int maxPacketSize() { return maxPacketSize; }
    @Override public BlockingQueue<UdpMessage> queue() { return queue; }

    @Override public ConcurrentMap<String, ServerInfo> servers() { return servers; }
    @Override public Map<String, ServerInfo> serversOrdered() { return serversOrdered; }
    @Override public Object serversLock() { return serversLock; }

    @Override public int serversCount() { return servers.size(); }

    @Override
    public String masterServerUuid() {
        synchronized (serversLock) {
            return serversOrdered.isEmpty() ? null : serversOrdered.keySet().iterator().next();
        }
    }

    @Override
    public int serverTcpPort(String uuid) {
        if (uuid == null) return -1;
        ServerInfo info = servers.get(uuid);
        return info == null ? -1 : info.getTcpPort();
    }


    @Override public long ttlMillis() { return ttlMs; }

    @Override
    public void removeServersFromList(long currTime) {
        synchronized (serversLock) {
            if (serversOrdered.isEmpty()) return;

            Iterator<Map.Entry<String, ServerInfo>> it = serversOrdered.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ServerInfo> entry = it.next();
                String uuid     = entry.getKey();
                ServerInfo info = entry.getValue();
                long lastSeen   = (info != null) ? info.getLastSeenMillis() : 0L;

                if (currTime - lastSeen > ttlMs) {
                    it.remove();
                    servers.remove(uuid);
                    System.out.printf("[Diretoria] Removido inativo (TTL=%d ms): %s%n", ttlMs, uuid);
                }
            }
        }
    }
}
