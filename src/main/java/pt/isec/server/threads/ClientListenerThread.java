package pt.isec.server.threads;
import pt.isec.server.core.IServerManager;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * aceita conexões tcp de clientes e cria uma thread para cada sessão.
 * se o nó atual não for o servidor primário, responde "not-primary".
 */
public class ClientListenerThread implements Runnable, AutoCloseable {

    private static final int THREAD_POOL_SIZE = 8;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final IServerManager tInfo;
    private ServerSocket serverSocket;

    public ClientListenerThread(IServerManager tInfo) {this.tInfo = tInfo;}

    @Override
    public void run() {
        try {
            // cria o socket tcp que aceita conexões de clientes
            serverSocket = new ServerSocket(tInfo.serverTcpPort());
            serverSocket.setSoTimeout(1000); // 1 segundo
            System.out.println("[ACCEPT] a escutar clientes em " + tInfo.serverTcpPort());

            // loop principal — aceita clientes enquanto o servidor estiver a correr
            while (tInfo.isRunning()) {
                try {
                    Socket newSocket = serverSocket.accept();
                    pool.execute(new ClientHandlerThread(tInfo, new NetworkTcpConnection(newSocket)));

                } catch (SocketTimeoutException e) {
                    // timeout normal: volta ao while e verifica tInfo.isRunning()
                    // não é erro, só quer dizer "ninguém ligou neste 1s"
                }
            }

        } catch (Exception e) {
            if (tInfo.isRunning()) {
                System.err.println("[ACCEPT] erro: " + e.getMessage());
            }
        } finally {
            try {
                close();
            } catch (Exception ignore) {}
        }
    }

    @Override
    public void close() throws Exception {
        if (serverSocket != null)
            serverSocket.close();
        pool.shutdownNow(); // encerra todas as threads de cliente
    }
}
