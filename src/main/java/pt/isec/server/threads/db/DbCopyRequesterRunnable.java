// FILE: src/main/java/pt/isec/server/network/threads/db/DbCopyRequesterRunnable.java
package pt.isec.server.threads.db;

import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.network.NetworkConnection;
import pt.isec.server.network.IServerNode;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;

/**
 * TCP (lado cliente, BACKUP): liga ao primário, envia DB_REQUEST_COPY
 * e grava os bytes recebidos no caminho local do .db.
 */
public class DbCopyRequesterRunnable implements Runnable {

    private static final int BUFFER_SIZE = 64 * 1024;        // 64 KiB para I/O de ficheiro
    private static final String REQ_PAYLOAD = "please";       // payload simples do pedido
    private static final String LOG_OK_PREFIX = "[DBCOPY/RQ] cópia concluída em ";
    private static final String LOG_ERR_PREFIX = "[DBCOPY/RQ] erro: ";
    private static final String LOG_BAD_RESP = "[DBCOPY/RQ] NACK/bad response";

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
        try (Socket s = new Socket(primaryIp, primaryDbCopyPort);
             NetworkConnection conn = new NetworkConnection(s)) {

            // pede cópia
            conn.sendMessage(new Message<>(MessageType.DB_REQUEST_COPY, REQ_PAYLOAD));

            // espera ACK
            var resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                System.err.println(LOG_BAD_RESP);
                return;
            }

            // recebe bytes brutos até EOF e grava no caminho local
            Files.createDirectories(tInfo.dbPath().getParent());
            try (InputStream in = s.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tInfo.dbPath().toFile())) {

                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
                fos.flush();
            }

            System.out.println(LOG_OK_PREFIX + tInfo.dbPath());

        } catch (Exception e) {
            System.err.println(LOG_ERR_PREFIX + e.getMessage());
        }
    }
}
