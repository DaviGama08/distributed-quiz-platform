package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Reaper thread responsible for:
 * <ul>
 *     <li>Removing inactive servers based on TTL</li>
 *     <li>Sending a {@code SHUTDOWN} message to all servers when the directory exits</li>
 *     <li>Closing the UDP socket at the end, unblocking the listener thread</li>
 * </ul>
 */
public class ReaperThread implements Runnable {
    private final IDirectoryThreadContext threadInfo;
    private final long periodMs;

    /**
     * @param threadInfo    directory manager
     * @param periodMs interval between sweeps in milliseconds
     */
    public ReaperThread(IDirectoryThreadContext threadInfo, long periodMs) {
        this.threadInfo = threadInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        try {
            while (threadInfo.isRunning()){
                long now = System.currentTimeMillis();

                threadInfo.removeServersFromList(now);

                Thread.sleep(periodMs);
            }
        } catch (InterruptedException ie) {
            // we do not use interrupt for shutdown, but if it happens just exit the loop
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Log.error(ReaperThread.class, "erro inesperado: " + t.getMessage(), t);
        } finally {
            // Directory is shutting down: send SHUTDOWN to all servers and close the socket.
            DatagramSocket socket = threadInfo.socket();
            if (socket != null && !socket.isClosed()) {
                for (ServerInfo s : new ArrayList<>(threadInfo.servers().values())) {
                    try {
                        String text = "SHUTDOWN";
                        byte[] out  = text.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket dp = new DatagramPacket(
                                out,
                                out.length,
                                InetAddress.getByName(s.getIp()),
                                s.getUdpPort()
                        );
                        socket.send(dp);
                    } catch (IOException e) {
                        Log.error(ReaperThread.class,
                                "Falha a enviar SHUTDOWN para " +
                                        s.getIp() + ":" + s.getUdpPort() + " – " + e.getMessage(),
                                e);
                    }
                }

                // closing the socket here will unblock UdpListenerThread (receive -> SocketException)
                socket.close();
            }

            Log.info(ReaperThread.class, "Reaper terminou.");
        }
    }
}
