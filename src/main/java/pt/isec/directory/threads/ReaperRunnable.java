package pt.isec.directory.threads;

/*Remove servidores inativos de x em x tempo*/

import pt.isec.directory.IDirectoryService;

public class ReaperRunnable implements Runnable{
    private final IDirectoryService tInfo;
    private final long periodMs;

    public ReaperRunnable(IDirectoryService tInfo, long periodMs) {
        this.tInfo = tInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        while (tInfo.isRunning()){
            try{
                long now = System.currentTimeMillis();

                tInfo.removeServersFromList(now);

                Thread.sleep(periodMs);
            }catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(periodMs); } catch (InterruptedException ignored) { break; }
            }
        }
    }
}
