package pt.isec.directory.threads;

/*Remove servidores inativos de x em x tempo*/

import pt.isec.directory.IDirectoryService;

public class ReaperRunnable implements Runnable{
    private final IDirectoryService directoryService;
    private final long periodMs;

    public ReaperRunnable(IDirectoryService directoryService, long periodMs) {
        this.directoryService = directoryService;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        while (directoryService.isRunning()){
            try{
                long now = System.currentTimeMillis();

                directoryService.removeServersFromList(now);

                Thread.sleep(periodMs);
            }catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(periodMs); } catch (InterruptedException ignored) { break; }
            }
        }
    }
}
