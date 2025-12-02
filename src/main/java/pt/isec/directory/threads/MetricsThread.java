package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;

/**
 * Metrics/logging thread.
 * <p>
 * Periodically prints simple stats about the directory:
 * number of servers, current primary, TCP port, etc.
 */
public class MetricsThread implements Runnable {
    private final IDirectoryThreadContext threadInfo;
    private final long periodMs;

    /**
     * @param threadInfo    directory manager
     * @param periodMs log interval in milliseconds
     */
    public MetricsThread(IDirectoryThreadContext threadInfo, long periodMs) {
        this.threadInfo = threadInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        while (threadInfo.isRunning()) {
            try {
                String master = threadInfo.masterServerUuid();
                int total = threadInfo.serversCount();
                int port = (master != null) ? threadInfo.serverTcpPort(master) : -1;

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
