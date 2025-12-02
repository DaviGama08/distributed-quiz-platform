package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.IDirectoryManager;

/**
 * Metrics/logging thread.
 * <p>
 * Periodically prints simple stats about the directory:
 * number of servers, current primary, TCP port, etc.
 */
public class MetricsThread implements Runnable {
    private final IDirectoryManager tInfo;
    private final long periodMs;

    /**
     * @param tInfo    directory manager
     * @param periodMs log interval in milliseconds
     */
    public MetricsThread(IDirectoryManager tInfo, long periodMs) {
        this.tInfo = tInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        while (tInfo.isRunning()) {
            try {
                String master = tInfo.masterServerUuid();
                int total = tInfo.serversCount();
                int port = (master != null) ? tInfo.serverTcpPort(master) : -1;

                String masterName = (master == null || port <= 0) ? "NONE" : ("servidor" + port);

                Log.info(MetricsThread.class,
                        "[Diretoria][Metrics] servers=%d, master=%s, tcpPort=%d",
                        total, masterName, port);

                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(periodMs); } catch (InterruptedException ignored) { break; }
            }
        }
    }
}
