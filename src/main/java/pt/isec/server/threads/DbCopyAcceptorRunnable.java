package pt.isec.server.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.IQuizServer;
import pt.isec.server.NetworkConnection;

import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Lado PRIMÁRIO: aceita DB_REQUEST_COPY, responde ACK e envia:
 *   (1) tamanho (long)
 *   (2) bytes do .db pelo MESMO ObjectOutputStream
 */
public class DbCopyAcceptorRunnable implements Runnable, AutoCloseable {
    private final IQuizServer tInfo;
    private ServerSocket ss;

    public DbCopyAcceptorRunnable(IQuizServer tInfo) { this.tInfo = tInfo; }

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

                    // OK: vamos enviar
                    conn.sendMessage(new Message<>(MessageType.ACK, "copy-start"));

                    var dbFile = tInfo.dbPath();
                    long size = Files.size(dbFile);
                    conn.writeLong(size); // (1) tamanho

                    try (FileInputStream fis = new FileInputStream(dbFile.toFile())) {
                        long sent = conn.sendStreamViaObjectOut(fis, size); // (2) bytes
                        System.out.printf("[DBCOPY] %d bytes enviados -> %s%n", sent, dbFile);
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
