package pt.isec.directory.Threads;

public class ReaperRunnable implements Runnable{
    private volatile boolean running;

    public ReaperRunnable(boolean running){this.running = running;}

    @Override
    public void run() {

    }
}
