package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.IDirectoryManager;
import pt.isec.directory.ServerInfo;

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
    private final IDirectoryManager tInfo;
    private final long periodMs;

    /**
     * @param tInfo    directory manager
     * @param periodMs interval between sweeps in milliseconds
     */
    public ReaperThread(IDirectoryManager tInfo, long periodMs) {
        this.tInfo = tInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        try {
            while (tInfo.isRunning()){
                long now = System.currentTimeMillis();

                tInfo.removeServersFromList(now);

                Thread.sleep(periodMs);
            }
        } catch (InterruptedException ie) {
            // we do not use interrupt for shutdown, but if it happens just exit the loop
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Log.error(ReaperThread.class, "[Diretoria][Reaper] erro inesperado: " + t.getMessage(), t);
        } finally {
            // Directory is shutting down: send SHUTDOWN to all servers and close the socket.
            DatagramSocket socket = tInfo.socket();
            if (socket != null && !socket.isClosed()) {
                for (ServerInfo s : new ArrayList<>(tInfo.servers().values())) {
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
                                "[Directory] Falha a enviar SHUTDOWN para " +
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
