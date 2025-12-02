package pt.isec.directory.threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.common.util.Log;
import pt.isec.directory.IDirectoryManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * UDP listener thread.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Receive UDP datagrams from clients and servers</li>
 *     <li>Wrap them into {@link UdpMessage}</li>
 *     <li>Push them into the shared queue for worker threads</li>
 * </ul>
 *
 * Protocol (text, {@code KEY=VALUE} pairs separated by {@code '|'}):
 *
 * <b>Client messages (no VER):</b>
 * <ul>
 *     <li>{@code TYPE=LOGIN}</li>
 * </ul>
 *
 * <b>Server messages:</b>
 * <ul>
 *     <li>{@code TYPE=REGISTER   | ID=&lt;serverId&gt; | TCP=&lt;ip:port&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=HEARTBEAT  | ID=&lt;serverId&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=DEREGISTER | ID=&lt;serverId&gt;}</li>
 * </ul>
 *
 * Replies (text):
 * <ul>
 *     <li>{@code "200 OK"}</li>
 *     <li>{@code "200 PRINCIPAL &lt;ip:port&gt;"}</li>
 *     <li>{@code "400 BAD_REQUEST &lt;reason&gt;"}</li>
 *     <li>{@code "404 NO_PRINCIPAL"}</li>
 *     <li>{@code "409 CONFLICT &lt;reason&gt;"}</li>
 *     <li>{@code "500 ERROR &lt;reason&gt;"}</li>
 * </ul>
 */
public class UdpListenerThread implements Runnable {
    private final IDirectoryManager tInfo;

    public UdpListenerThread(IDirectoryManager tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        DatagramSocket socket = tInfo.socket();
        Log.info(UdpListenerThread.class,
                "Directoria UDP a escutar na porta %d...", tInfo.udpPort());
        byte[] buffer         = new byte[tInfo.maxPacketSize()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (tInfo.isRunning()) {
            try {
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                tInfo.queue().put(
                        new UdpMessage(packet.getAddress(), packet.getPort(), data, data.length)
                );

            } catch (SocketTimeoutException ste) {
                // optional: socket configured with timeout; just re-check loop condition
            } catch (SocketException se) {
                // socket intentionally closed on shutdown will cause SocketException here
                if (!tInfo.isRunning() || socket.isClosed()) {
                    // ordered shutdown – exit loop quietly
                    break;
                }
                Log.error(UdpListenerThread.class,
                        "Erro de socket UDP: " + se.getMessage(), se);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (IOException e) {
                if (tInfo.isRunning()) {
                    Log.error(UdpListenerThread.class,
                            "Erro a receber UDP: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException ie) {
                // thread interrupted while blocked on queue.put()
                Thread.currentThread().interrupt();
                break;
            }
        }
        Log.info(UdpListenerThread.class, "UdpListenerThread terminou.");
    }
}
