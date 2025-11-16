package pt.isec.server.threads;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.NetworkConnection;
import pt.isec.server.IQuizServer;

import java.io.FileOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lado BACKUP: pede cópia, recebe ACK, lê:
 *   (1) tamanho (long)
 *   (2) exatamente 'tamanho' bytes pelo MESMO ObjectInputStream
 */
public class DbCopyRequesterRunnable implements Runnable {
    private final IQuizServer tInfo;
    private final String primaryIp;
    private final int primaryDbCopyPort;

    public DbCopyRequesterRunnable(IQuizServer tInfo, String primaryIp, int primaryDbCopyPort) {
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

            System.out.printf("[DBCOPY/RQ] pedir cópia a %s:%d -> %s%n", primaryIp, primaryDbCopyPort, target);

            conn.sendMessage(new Message<>(MessageType.DB_REQUEST_COPY, "please"));

            var resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                System.err.println("[DBCOPY/RQ] resposta inválida (esperado ACK)");
                return;
            }

            Files.createDirectories(target.getParent());

            // (1) lê tamanho
            long size = conn.readLong();

            // (2) recebe exatamente 'size' bytes
            long total;
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                total = conn.receiveExactly(fos, size);
            }
            System.out.printf("[DBCOPY/RQ] %d bytes recebidos -> %s%n", total, tmp);

            boolean moved = false;
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
                System.out.println("[DBCOPY/RQ] move ATOMIC ok -> " + target);
            } catch (Exception ignore) {}

            if (!moved) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                    System.out.println("[DBCOPY/RQ] move simples ok -> " + target);
                } catch (Exception ignore) {}
            }

            if (!moved) {
                try {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp);
                    moved = true;
                    System.out.println("[DBCOPY/RQ] copy+delete ok -> " + target);
                } catch (Exception e) {
                    System.err.println("[DBCOPY/RQ] falha no copy+delete: " + e.getMessage());
                }
            }

            if (moved) {
                System.out.println("[DBCOPY/RQ] cópia concluída em " + target);
            } else {
                System.err.println("[DBCOPY/RQ] não consegui substituir " + target + " (ficou " + tmp + ")");
            }

        } catch (Exception e) {
            System.err.println("[DBCOPY/RQ] erro: " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }
}
