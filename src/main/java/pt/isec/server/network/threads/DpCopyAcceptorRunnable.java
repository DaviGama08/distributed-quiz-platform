package pt.isec.server.network.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.network.IServerNode;
import pt.isec.server.network.NetworkConnection;

import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

/**
 * TCP: aceita pedidos de cópia de BD.
 * Espera DB_COPY_REQUEST, responde ACK e envia o ficheiro .db em bytes brutos.
 */
public class DpCopyAcceptorRunnable implements Runnable, AutoCloseable {
    private final IServerNode tInfo;
    private ServerSocket ss;

    public DpCopyAcceptorRunnable(IServerNode tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(tInfo.dbCopyPort());
            System.out.println("[DBCOPY] a escutar em " + tInfo.dbCopyPort());

            while (tInfo.isRunning()) {
                Socket s = ss.accept();
                NetworkConnection conn = null;
                try {
                    conn = new NetworkConnection(s);
                    conn.setReadTimeout(Duration.ofSeconds(5));

                    Message<?> req = conn.receiveMessage();
                    if (req == null || req.getType() != MessageType.DB_COPY_REQUEST) {
                        conn.sendMessage(new Message<>(MessageType.NACK, "bad request"));
                        continue;
                    }

                    conn.sendMessage(new Message<>(MessageType.ACK, "copy-start"));

                    // Nota: quando houver BD real, aplicar write-lock durante a cópia
                    try (FileInputStream fis = new FileInputStream(tInfo.dbPath().toFile())) {
                        conn.sendStream(fis);
                    }
                } catch (Exception e) {
                    System.err.println("[DBCOPY] erro sessão: " + e.getMessage());
                } finally {
                    try { if (conn != null) conn.close(); } catch (Exception ignore) {}
                    try { s.close(); } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[DBCOPY] erro: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        if (ss != null) ss.close();
    }
}
