package pt.isec.server.threads;

import pt.isec.server.core.IServerManager;
import pt.isec.common.util.Log;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * Thread responsible for accepting TCP connections from clients and creating
 * a {@link ClientHandlerThread} for each session.
 */
public class ClientListenerThread implements Runnable, AutoCloseable {

    private static final int THREAD_POOL_SIZE = 8;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final IServerManager tInfo;
    private ServerSocket serverSocket;

    private final List<ClientHandlerThread> handlers = new CopyOnWriteArrayList<>();

    /**
     * Creates a new client listener thread.
     *
     * @param tInfo server manager context
     */
    public ClientListenerThread(IServerManager tInfo) {
        this.tInfo = tInfo;
    }

    /**
     * Main accept loop:
     * <ul>
     *     <li>Opens a {@link ServerSocket} on the server TCP port</li>
     *     <li>Accepts client connections while the server is running</li>
     *     <li>Creates a {@link ClientHandlerThread} for each accepted socket</li>
     * </ul>
     */
    @Override
    public void run() {
        try {
            // Create the TCP socket that accepts client connections
            serverSocket = new ServerSocket(tInfo.serverTcpPort());
            serverSocket.setSoTimeout(1000); // 1 second
            Log.info(ClientListenerThread.class,
                    "[ACCEPT] Listening for TCP client connections on port %d", tInfo.serverTcpPort());

            // Main loop — accept clients while the server is running
            while (tInfo.isRunning()) {
                try {
                    Socket newSocket = serverSocket.accept();
                    ClientHandlerThread handler =
                            new ClientHandlerThread(tInfo, new NetworkTcpConnection(newSocket));
                    handlers.add(handler);
                    pool.execute(handler);

                } catch (SocketTimeoutException e) {
                    // Normal timeout: loop again and check tInfo.isRunning()
                }
            }

        } catch (Exception e) {
            if (tInfo.isRunning()) {
                Log.error(ClientListenerThread.class,
                        "[ACCEPT] Error in client accept loop: %s", e.getMessage());
            }
        } finally {
            Log.info(ClientListenerThread.class, "Client accept thread terminated.");
            try {
                close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Closes the server socket, all active client handlers and shuts down
     * the thread pool.
     *
     * @throws Exception if closing the server socket fails
     */
    @Override
    public void close() throws Exception {
        if (serverSocket != null) {
            serverSocket.close();
        }

        for (ClientHandlerThread h : handlers) {
            try {
                h.close();
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow(); // terminate all client threads
    }
}
