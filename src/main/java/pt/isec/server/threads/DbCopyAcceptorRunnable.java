// FILE: src/main/java/pt/isec/server/network/threads/db/DbCopyAcceptorRunnable.java
package pt.isec.server.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.IServerNode;
import pt.isec.server.NetworkConnection;

import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

/**
 * TCP (lado servidor, PRIMÁRIO): aceita pedidos DB_REQUEST_COPY,
 * responde ACK e envia o ficheiro .db em bytes brutos.
 */
public class DbCopyAcceptorRunnable implements Runnable, AutoCloseable {
    private final IServerNode tInfo;
    private ServerSocket ss;

    public DbCopyAcceptorRunnable(IServerNode tInfo) { this.tInfo = tInfo; }

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

                    var req = conn.receiveMessage();
                    if (req == null || req.getType() != MessageType.DB_REQUEST_COPY) {
                        conn.sendMessage(new Message<>(MessageType.NACK, "bad request"));
                        continue;
                    }

                    if (!tInfo.isPrimary()) {
                        conn.sendMessage(new Message<>(MessageType.NACK, "not-primary", String.class));
                        continue;
                    }

                    conn.sendMessage(new Message<>(MessageType.ACK, "copy-start"));

                    try (FileInputStream fis = new FileInputStream(tInfo.dbPath().toFile())) {
                        conn.sendStream(fis); // seu NetworkConnection já envia o stream
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
