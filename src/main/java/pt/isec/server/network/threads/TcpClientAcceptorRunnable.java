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
 * Aceita clientes TCP e trata a sessão. Se o nó não for primário,
 * responde NACK "not-primary" (não temos getter para principal).
 */
public class TcpClientAcceptorRunnable implements Runnable, AutoCloseable {
    private final IServerNode tInfo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private ServerSocket ss;

    public TcpClientAcceptorRunnable(IServerNode tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(tInfo.clientPort());
            System.out.println("[ACCEPT] clientes em " + tInfo.clientPort());
            while (tInfo.isRunning()) {
                Socket s = ss.accept();
                pool.execute(new ClientHandler(tInfo, s));
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[ACCEPT] erro: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        if (ss != null) ss.close();
        pool.shutdownNow();
    }

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
                conn.setReadTimeout(Duration.ofSeconds(30)); // 1ª msg em 30s
                Message<?> first = conn.receiveMessage();

                if (!tInfo.isPrimary()) {
                    conn.sendMessage(new Message<>(MessageType.NACK, "not-primary"));
                    return;
                }

                conn.sendMessage(new Message<>(MessageType.ACK, "ok"));
                conn.setReadTimeout(Duration.ZERO);

                while (tInfo.isRunning()) {
                    Message<?> m = conn.receiveMessage();
                    if (m == null) break;
                    // TODO: tratar comandos reais aqui...
                    conn.sendMessage(new Message<>(MessageType.PONG, "ok"));
                }
            } catch (Exception ignore) {
            } finally {
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
