package pt.isec.directory.threads;

import pt.isec.directory.DirectoryService;

public class MetricsRunnable implements Runnable{
    private final DirectoryService directoryService;
    private final long periodMs;

    public MetricsRunnable(DirectoryService directoryService, long periodMs) {
        this.directoryService = directoryService;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        while (directoryService.isRunning()) {
            try {
                String master = directoryService.masterServerUuid();
                int total = directoryService.serversCount();
                int port = (master != null) ? directoryService.serverTcpPort(master) : -1;
                int ver  = (master != null) ? directoryService.serverVersion(master) : -1;

                System.out.printf(
                        "[Diretoria][Metrics] servers=%d, master=%s, tcpPort=%d, version=%d%n",
                        total, (master == null ? "NONE" : master), port, ver
                );

                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(periodMs); } catch (InterruptedException ignored) { break; }
            }
        }
    }
}
