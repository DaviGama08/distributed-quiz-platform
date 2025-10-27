package pt.isec.server.network.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.network.IServerNode;
import pt.isec.server.network.NetworkConnection;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * aceita conexões tcp de clientes e cria uma thread para cada sessão.
 * se o nó atual não for o servidor primário, responde "not-primary".
 */
public class TcpClientAcceptorRunnable implements Runnable, AutoCloseable {

    private static final int THREAD_POOL_SIZE = 8;
    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;
    private static final Duration NO_TIMEOUT = Duration.ZERO;
    private final IServerNode tInfo;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
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
                Socket client = serverSocket.accept();
                pool.execute(new ClientHandler(tInfo, client));
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

    // thread interna que trata de um cliente tcp específico
    static class ClientHandler implements Runnable {
        private final IServerNode tInfo;
        private final Socket socket;

        ClientHandler(IServerNode tInfo, Socket socket) {
            this.tInfo = tInfo;
            this.socket = socket;
        }

        @Override
        public void run() {
            NetworkConnection conn = null;
            try {
                conn = new NetworkConnection(socket);
                // o cliente deve enviar a 1ª mensagem em até 30 segundos
                conn.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

                Message<?> first = conn.receiveMessage();

                // se o servidor não for primário, rejeita o cliente
                if (!tInfo.isPrimary()) {
                    conn.sendMessage(new Message<>(MessageType.NACK, "not-primary"));
                    return;
                }

                // caso contrário, confirma ligação
                conn.sendMessage(new Message<>(MessageType.ACK, "ok"));

                // remove timeout depois da primeira interação
                conn.setReadTimeout(NO_TIMEOUT);

                // loop principal da sessão do cliente
                while (tInfo.isRunning()) {
                    Message<?> msg = conn.receiveMessage();
                    if (msg == null)
                        break;

                    // TODO: processar comandos reais vindos do cliente
                    conn.sendMessage(new Message<>(MessageType.PONG, "ok"));
                }

            } catch (Exception ignore) {
                // falha silenciosa — o cliente pode ter fechado a ligação
            } finally {
                // garante que o socket é fechado corretamente
                try {
                    if (conn != null) conn.close();
                } catch (Exception ignore) {}
                try {
                    socket.close();
                } catch (Exception ignore) {}
            }
        }
    }
}
