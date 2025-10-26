package pt.isec.directory.threads;

import pt.isec.directory.DirectoryService;

public class MetricsRunnable implements Runnable{
    private final DirectoryService tInfo;
    private final long periodMs;

    public MetricsRunnable(DirectoryService tInfo, long periodMs) {
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
                int ver  = (master != null) ? tInfo.serverVersion(master) : -1;

                System.out.printf(
                        "[Diretoria][Metrics] servers=%d, master=%s, tcpPort=%d, version=%d%n",
                        total, (master == null ? "NONE" : master), port, ver
                );
                //TODO: é o mais indicado?
                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(periodMs); } catch (InterruptedException ignored) { break; }
            }
        }
    }
}
