package pt.isec.server.threads.client;

import pt.isec.server.IServerNode;
import pt.isec.server.NetworkConnection;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * aceita conexões tcp de clientes e cria uma thread para cada sessão.
 * se o nó atual não for o servidor primário, responde "not-primary".
 */
public class TcpClientAcceptorRunnable implements Runnable, AutoCloseable {

    private static final int THREAD_POOL_SIZE = 8;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final IServerNode tInfo;
    private ServerSocket serverSocket;

    public TcpClientAcceptorRunnable(IServerNode tInfo) {this.tInfo = tInfo;}

    @Override
    public void run() {
        try {
            // cria o socket tcp que aceita conexões de clientes
            serverSocket = new ServerSocket(tInfo.clientPort());
            System.out.println("[ACCEPT] a escutar clientes em " + tInfo.clientPort());

            // loop principal — aceita clientes enquanto o servidor estiver a correr
            while (tInfo.isRunning()) {
                Socket newSocket = serverSocket.accept();
                pool.execute(new ClientHandlerRunnable(tInfo, new NetworkConnection(newSocket)));
            }

        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[ACCEPT] erro: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        if (serverSocket != null)
            serverSocket.close();
        pool.shutdownNow(); // encerra todas as threads de cliente
    }
}
