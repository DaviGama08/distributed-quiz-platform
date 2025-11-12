package pt.isec.server.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.NetworkConnection;
import pt.isec.server.IServerNode;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * TCP (lado cliente, BACKUP): liga ao primário, envia DB_REQUEST_COPY
 * e grava os bytes recebidos no caminho local do .db.
 */
public class DbCopyRequesterRunnable implements Runnable {

    private static final int BUFFER_SIZE = 64 * 1024;        // 64 KiB para I/O de ficheiro
    private static final String REQ_PAYLOAD = "please";       // payload simples do pedido

    private final IServerNode tInfo;
    private final String primaryIp;
    private final int primaryDbCopyPort;

    public DbCopyRequesterRunnable(IServerNode tInfo, String primaryIp, int primaryDbCopyPort) {
        this.tInfo = tInfo;
        this.primaryIp = primaryIp;
        this.primaryDbCopyPort = primaryDbCopyPort;
    }

    @Override
    public void run() {
        Path target = tInfo.dbPath();
        Path tmp    = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try (Socket s = new Socket(primaryIp, primaryDbCopyPort);
             NetworkConnection conn = new NetworkConnection(s)) {

            System.out.printf("[DBCOPY/RQ] pedir cópia a %s:%d → %s%n",
                    primaryIp, primaryDbCopyPort, target);

            // pede cópia
            conn.sendMessage(new Message<>(MessageType.DB_REQUEST_COPY, REQ_PAYLOAD));

            // espera ACK
            var resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                System.err.println("[DBCOPY/RQ] NACK/bad response");
                return;
            }

            // recebe bytes brutos para ficheiro temporário
            Files.createDirectories(target.getParent());
            try (InputStream in = s.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tmp.toFile())) {

                byte[] buf = new byte[BUFFER_SIZE];
                int n, total = 0;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    total += n;
                }
                fos.flush();
                System.out.printf("[DBCOPY/RQ] %d bytes recebidos para %s%n", total, tmp);
            }

            // tenta substituir de forma robusta
            boolean done = false;
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                done = true;
                System.out.println("[DBCOPY/RQ] move ATOMIC ok → " + target);
            } catch (Exception ignore) {
                // OneDrive e certos FS não suportam ATOMIC_MOVE
            }

            if (!done) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    done = true;
                    System.out.println("[DBCOPY/RQ] move simples ok → " + target);
                } catch (Exception ignore) { /* continua */ }
            }

            if (!done) {
                // fallback final: copy + delete
                try {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp);
                    done = true;
                    System.out.println("[DBCOPY/RQ] copy+delete ok → " + target);
                } catch (Exception e) {
                    System.err.println("[DBCOPY/RQ] falha no copy+delete: " + e.getMessage());
                }
            }

            if (!done) {
                System.err.println("[DBCOPY/RQ] não consegui substituir " + target + " (ficou " + tmp + ")");
            } else {
                System.out.println("[DBCOPY/RQ] cópia concluída em " + target);
            }

        } catch (Exception e) {
            System.err.println("[DBCOPY/RQ] erro: " + e.getMessage());
            // se falhar, limpa o .tmp para não confundir
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }
}
