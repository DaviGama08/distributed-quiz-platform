package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;

/**
 * Periodic metrics/logging thread for the directory service.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Periodically reads summary information from {@link IDirectoryThreadContext}</li>
 *     <li>Logs the number of known servers and which one is currently the primary</li>
 * </ul>
 * The thread runs while the directory is marked as running and then terminates
 * quietly when interrupted or when {@link IDirectoryThreadContext#isRunning()} becomes {@code false}.
 */
@SuppressWarnings("ClassCanBeRecord")
public class MetricsThread implements Runnable {

    /* ======================= FIELDS ======================= */

    /** Shared directory state and query methods. */
    private final IDirectoryThreadContext threadInfo;

    /** Interval between metric logs, in milliseconds. */
    private final long periodMs;

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new {@link MetricsThread}.
     *
     * @param threadInfo directory thread context providing metrics data
     * @param periodMs   log interval in milliseconds
     */
    public MetricsThread(IDirectoryThreadContext threadInfo, long periodMs) {
        this.threadInfo = threadInfo;
        this.periodMs = periodMs;
    }

    /* ======================= MAIN LOOP ======================= */

    /**
     * Periodically logs basic directory metrics while the directory is running.
     * <p>
     * The loop wakes up every {@code periodMs} milliseconds and logs:
     * <ul>
     *     <li>total number of registered servers</li>
     *     <li>identifier and TCP port of the current primary server (if any)</li>
     * </ul>
     * The thread stops when:
     * <ul>
     *     <li>{@link IDirectoryThreadContext#isRunning()} returns {@code false}, or</li>
     *     <li>the thread is interrupted</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("BusyWait") // intentional periodic sleep, not a tight busy-wait loop
    public void run() {
        while (threadInfo.isRunning()) {
            try {
                String masterUuid = threadInfo.masterServerUuid();
                int totalServers = threadInfo.serversCount();
                int masterPort = (masterUuid != null) ? threadInfo.serverTcpPort(masterUuid) : -1;

                String masterName = (masterUuid == null || masterPort <= 0)
                        ? "NONE"
                        : ("servidor" + masterPort);

                Log.info(
                        MetricsThread.class,
                        "servers=%d, master=%s, tcpPort=%d",
                        totalServers, masterName, masterPort
                );

                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                // Allow the thread to terminate gracefully on interruption
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // Log unexpected errors but keep the thread alive, unless it gets interrupted
                Log.error(MetricsThread.class, "Unexpected error in MetricsThread: " + t.getMessage(), t);
                try {
                    Thread.sleep(periodMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Log.info(MetricsThread.class, "MetricsThread finished.");
    }
}
