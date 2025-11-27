package pt.isec.server.threads;

import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.server.core.IServerManager;
import pt.isec.server.core.ServerManager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Thread que emite e recebe heartbeats para sincronização entre servidores.
 * Envia SQL pendente quando primário e aplica updates ao receber.
 */
public class ClusterHeartbeatThread implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;
    private static final int ACCEPT_TIMEOUT_MS = 500;
    private static final int BUFFER_SIZE = 4096;

    private final IServerManager tInfo;
    private MulticastSocket ms;
    private ServerSocket dbCopyServerSocket;

    public ClusterHeartbeatThread(IServerManager tInfo) { this.tInfo = tInfo; }

    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(tInfo.multicastPort());
             ServerSocket _ss = new ServerSocket(tInfo.dbCopyPort())) {

            this.ms = _ms;
            this.dbCopyServerSocket = _ss;

            _ms.setReuseAddress(true);
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(tInfo.multicastInterface());
            try { _ms.setLoopbackMode(false); } catch (Throwable ignore) {}

            InetAddress grp = InetAddress.getByName(tInfo.multicastGroup());
            _ms.joinGroup(new InetSocketAddress(grp, tInfo.multicastPort()), tInfo.multicastInterface());

            _ss.setSoTimeout(ACCEPT_TIMEOUT_MS);

            long lastSent = 0L;
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();


                if (tInfo.isPrimary()) {
                    List<String> sqlToSend = null;

                    BlockingQueue<List<String>> q = tInfo.queue();
                    if (q != null) {
                        sqlToSend = q.poll();
                    }

                    if (sqlToSend != null) {
                        sendHeartbeat(_ms, grp, sqlToSend);  // heartbeat with SQL
                        lastSent = now;
                    } else if (now - lastSent >= HEARTBEAT_INTERVAL_MS) {
                        sendHeartbeat(_ms, grp, null);       // periodic heartbeat without SQL
                        lastSent = now;
                    }
                }

                // BACKUP: recebe heartbeat e aplica updates
                if (!tInfo.isPrimary()) {

                    try {
                        _ms.receive(pkt);
                        String senderIp = pkt.getAddress().getHostAddress();
                        String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                        if (msg.startsWith("MC_HB;")) {
                            long rxClientPort = extractLong(msg, "clientPort");
                            // ignora batimentos que o próprio servidor enviou
                            boolean fromMe = senderIp.equals(tInfo.serverTcpIp()) && rxClientPort == tInfo.serverTcpPort();
                            if (!fromMe) {

                                long rxVersion = extractLong(msg, "version");
                                boolean versionMismatch;

                                // Aplica SQL updates codificados em base64
                                String sqlEncoded = extractString(msg, "sql");
                                if (sqlEncoded != null && !sqlEncoded.isBlank()) {
                                    System.out.println("Entrei!");
                                    versionMismatch = rxVersion >= 0 && rxVersion != tInfo.dbVersion() + 1;

                                    if(versionMismatch){
                                        tInfo.stopRunning(false);
                                        System.out.println("RECEBI SQL MAS VERSION MAL");
                                        break;
                                    }
                                    byte[] bytes = Base64.getDecoder().decode(sqlEncoded);
                                    String joined = new String(bytes, StandardCharsets.UTF_8);

                                    String[] stmts = joined.split(";;");
                                    for (String s : stmts) {
                                        s = s.trim();
                                        if (s.isEmpty())
                                            continue;
                                        tInfo.getDb().executeUpdate(s);
                                        System.out.println("Executei: " + s);
                                    }
                                    tInfo.setDbVersion(rxVersion);
                                }
                                else{

                                    int  rxDbPort  = (int) extractLong(msg, "dbPort");

                                    boolean missingDb = !Files.exists(tInfo.dbPath());


                                    if (missingDb) {
                                        System.err.printf("[%s][MC] pedir cópia: falta=%s versao(local=%d, rx=%d) → %s:%d%n",
                                                Instant.now(), missingDb, tInfo.dbVersion(), rxVersion, senderIp, rxDbPort);

                                        if (rxDbPort > 0) {
                                            if (tInfo instanceof ServerManager sn) {
                                                if (!sn.tryLockCopy()) continue;
                                                try {
                                                    requestDbCopyFromPrimary(senderIp, rxDbPort, rxVersion);
                                                } finally {
                                                    sn.unlockCopy();
                                                }
                                            } else {
                                                requestDbCopyFromPrimary(senderIp, rxDbPort, rxVersion);
                                            }
                                        } else {
                                            System.err.println("[MC] heartbeat sem dbPort → não posso pedir cópia.");
                                        }
                                        continue;
                                    }

                                    versionMismatch = rxVersion >= 0 && rxVersion != tInfo.dbVersion();

                                    System.out.println("RX VERSION -> " + rxVersion + "\t MY VERSION -> " + tInfo.dbVersion());
                                    if(versionMismatch){
                                        System.out.println("PARA CRL!!");
                                        tInfo.stopRunning(false);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (SocketTimeoutException ignore) {
                        // sem heartbeat no ciclo
                    } catch (Exception e) {
                        if (tInfo.isRunning())
                            System.err.println("[MC-LOOP] erro rx: " + e.getMessage());
                    }
                }

                // aceitar pedidos de cópia de BD (lado primário)
                try {
                    Socket s = _ss.accept(); // timeout curto
                    handleDbCopySession(s);
                } catch (SocketTimeoutException ignore) {
                    // sem pedido
                } catch (Exception e) {
                    if (tInfo.isRunning())
                        System.err.println("[DBCOPY] erro no accept: " + e.getMessage());
                }

                Thread.sleep(LOOP_SLEEP_MS);
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[MC-LOOP] erro: " + e.getMessage());
        }
    }



    private static long extractLong(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) return -1;
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        try { return Long.parseLong(raw.trim()); }
        catch (Exception e) { return -1; }
    }


    private void sendHeartbeat(MulticastSocket ms, InetAddress grp,  List<String> sql) throws IOException {
        String encodedSql = "";
        if (sql != null && !sql.isEmpty()) {
            String joined = String.join(";;", sql);
            encodedSql = Base64.getEncoder()
                    .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
        }

        String beat = "MC_HB;id=servidor" + tInfo.serverTcpPort() +
                ";role=MASTER" +
                ";version=" + tInfo.dbVersion() +
                ";dbPort=" + tInfo.dbCopyPort() +
                ";clientPort=" + tInfo.serverTcpPort() +
                ";sql=" + encodedSql;

        byte[] data = beat.getBytes(StandardCharsets.UTF_8);
        ms.send(new DatagramPacket(data, data.length, grp, tInfo.multicastPort()));
    }
        private static String extractString(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) return null;
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        return raw.trim();
    }

    // Lado BACKUP: pede a cópia ao primário
    private void requestDbCopyFromPrimary(String primaryIp, int primaryPort, long rxVersion) {
        Path target = tInfo.dbPath();
        Path tmp    = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try (NetworkTcpConnection conn = NetworkTcpConnection.connect(
                primaryIp, primaryPort, Duration.ofSeconds(5))) {

            conn.setReadTimeout(Duration.ofSeconds(30));
            conn.sendMessage(new TcpMessage<>(MessageType.DB_REQUEST_COPY, "please"));

            var resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                System.err.println("[DBCOPY/RQ] resposta inválida (esperado ACK copy-start)");
                return;
            }

            Files.createDirectories(target.getParent());
            long size = conn.readLong();

            long total;
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                total = conn.receiveExactly(fos, size);
            }
            System.out.printf("[DBCOPY/RQ] %d bytes recebidos -> %s%n", total, tmp);

            boolean moved = false;
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (Exception ignore) {}
            if (!moved) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                } catch (Exception ignore) {}
            }
            if (!moved) {
                try {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp);
                    moved = true;
                } catch (Exception e) {
                    System.err.println("[DBCOPY/RQ] falha no copy+delete: " + e.getMessage());
                }
            }

            if (moved) {
                System.out.println("[DBCOPY/RQ] cópia concluída em " + target);
                tInfo.setDbVersion(rxVersion);
            } else {
                System.err.println("[DBCOPY/RQ] não consegui substituir " + target + " (ficou " + tmp + ")");
            }

        } catch (Exception e) {
            System.err.println("[DBCOPY/RQ] erro: " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }

    // Lado PRIMÁRIO: atende pedidos DB_REQUEST_COPY
    private void handleDbCopySession(Socket s) {
        try (NetworkTcpConnection connection = new NetworkTcpConnection(s)) {

            TcpMessage<?> req = connection.receiveMessage();
            if (req == null || req.getType() != MessageType.DB_REQUEST_COPY) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "bad-request", String.class));
                return;
            }

            if (!tInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary", String.class));
                return;
            }

            connection.sendMessage(new TcpMessage<>(MessageType.ACK, "copy-start"));

            Path dbFile = tInfo.dbPath();
            long size   = Files.size(dbFile);
            connection.writeLong(size);

            try (FileInputStream fis = new FileInputStream(dbFile.toFile())) {
                long sent = connection.sendStreamViaObjectOut(fis, size);
                System.out.printf("[DBCOPY] %d bytes enviados -> %s%n", sent, dbFile);
            }

        } catch (Exception e) {
            System.err.println("[DBCOPY] erro sessão cópia: " + e.getMessage());
        } finally {
            try { s.close(); } catch (Exception ignore) {}
        }
    }

    @Override public void close() {
        if (ms != null) try { ms.close(); } catch (Exception ignore) {}
        if (dbCopyServerSocket != null) try { dbCopyServerSocket.close(); } catch (Exception ignore) {}
    }
}
