package pt.isec.directory.threads;


import pt.isec.directory.IDirectoryManager;

public class MetricsThread implements Runnable{
    private final IDirectoryManager tInfo;
    private final long periodMs;

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

                System.out.printf(
                        "[Diretoria][Metrics] servers=%d, master=%s, tcpPort=%d",
                        total, masterName, port
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
