package pt.isec.server.threads;

import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.core.ServerManager;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.common.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Thread responsible for multicast heartbeats and database copy between servers.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Primary node: sends multicast heartbeats, optionally with pending SQL</li>
 *     <li>Backup nodes: receive heartbeats, apply SQL or request DB copy</li>
 *     <li>Primary node: handles DB copy requests via a separate TCP server socket</li>
 * </ul>
 */
public class ClusterHeartbeatThread implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;
    private static final int ACCEPT_TIMEOUT_MS = 500;
    private static final int BUFFER_SIZE = 4096;

    private final IServerThreadContext threadInfo;
    private MulticastSocket ms;
    private ServerSocket dbCopyServerSocket;

    /**
     * Creates a new cluster heartbeat thread.
     *
     * @param threadInfo server manager providing multicast and DB information
     */
    public ClusterHeartbeatThread(IServerThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /**
     * Main loop:
     * <ul>
     *     <li>If primary, send heartbeats and pending SQL</li>
     *     <li>If backup, receive heartbeats and apply SQL / request DB copy</li>
     *     <li>Handle DB copy requests (primary side) via TCP</li>
     * </ul>
     */
    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(threadInfo.multicastPort());
             ServerSocket _ss = new ServerSocket(threadInfo.dbCopyPort())) {

            this.ms = _ms;
            this.dbCopyServerSocket = _ss;

            _ms.setReuseAddress(true);
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(threadInfo.multicastInterface());
            try {
                _ms.setLoopbackMode(false);
            } catch (Throwable ignore) {
            }

            InetAddress grp = InetAddress.getByName(threadInfo.multicastGroup());
            _ms.joinGroup(new InetSocketAddress(grp, threadInfo.multicastPort()), threadInfo.multicastInterface());

            _ss.setSoTimeout(ACCEPT_TIMEOUT_MS);

            long lastSent = 0L;
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (threadInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // PRIMARY: send heartbeats (with or without SQL)
                if (threadInfo.isPrimary()) {
                    lastSent = handlePrimaryHeartbeatLoop(_ms, grp, lastSent, now);
                }

                // BACKUP: receive heartbeat and apply updates
                if (!threadInfo.isPrimary()) {
                    handleBackupHeartbeatLoop(_ms, pkt);
                }

                // accept DB copy requests (primary side)
                handleDbCopyAcceptLoop(_ss);

                Thread.sleep(LOOP_SLEEP_MS);
            }
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[MC-LOOP] Error in main cluster loop: %s", e.getMessage());
            }
        }
        Log.info(ClusterHeartbeatThread.class, "Cluster heartbeat thread terminated.");
    }

    /**
     * Primary-only part of the loop: sends heartbeats, optionally including SQL statements.
     *
     * @param _ms      multicast socket
     * @param grp      multicast group
     * @param lastSent timestamp of last heartbeat
     * @param now      current time in millis
     * @return new lastSent timestamp
     * @throws IOException if sending the heartbeat fails
     */
    private long handlePrimaryHeartbeatLoop(MulticastSocket _ms, InetAddress grp, long lastSent, long now)
            throws IOException {

        List<String> sqlToSend = null;

        BlockingQueue<List<String>> q = threadInfo.queue();
        if (q != null) {
            sqlToSend = q.poll();
        }

        if (sqlToSend != null) {
            // heartbeat with pending SQL
            sendHeartbeat(_ms, grp, sqlToSend);
            lastSent = now;
        } else if (now - lastSent >= HEARTBEAT_INTERVAL_MS) {
            // periodic heartbeat without SQL
            sendHeartbeat(_ms, grp, null);
            lastSent = now;
        }

        return lastSent;
    }

    /**
     * Backup-only part of the loop: receives heartbeats and applies updates.
     *
     * @param _ms multicast socket
     * @param pkt datagram packet reused for reception
     */
    private void handleBackupHeartbeatLoop(MulticastSocket _ms, DatagramPacket pkt) {
        try {
            _ms.receive(pkt);
            String senderIp = pkt.getAddress().getHostAddress();
            String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

            if (msg.startsWith("MC_HB;")) {
                long rxClientPort = extractLong(msg, "clientPort");
                // ignore our own heartbeats
                boolean fromMe = senderIp.equals(threadInfo.serverTcpIp()) && rxClientPort == threadInfo.serverTcpPort();
                if (!fromMe) {

                    long rxVersion = extractLong(msg, "version");
                    boolean versionMismatch;

                    // Apply SQL updates encoded in base64
                    String sqlEncoded = extractString(msg, "sql");
                    if (sqlEncoded != null && !sqlEncoded.isBlank()) {
                        Log.info(ClusterHeartbeatThread.class,
                                "[MC] Heartbeat with SQL received from primary; applying incremental updates.");

                        versionMismatch = rxVersion >= 0 && rxVersion != threadInfo.dbVersion() + 1;

                        if (versionMismatch) {
                            Log.error(ClusterHeartbeatThread.class,
                                    "[MC] Unexpected DB version while applying SQL (expected=%d, received=%d). Shutting down server.",
                                    threadInfo.dbVersion() + 1, rxVersion);
                            threadInfo.shutdownServer();
                            return;
                        }

                        byte[] bytes = Base64.getDecoder().decode(sqlEncoded);
                        String joined = new String(bytes, StandardCharsets.UTF_8);

                        String[] stmts = joined.split(";;");
                        for (String s : stmts) {
                            s = s.trim();
                            if (s.isEmpty()) {
                                continue;
                            }
                            threadInfo.getDb().executeUpdate(s);
                            Log.info(ClusterHeartbeatThread.class,
                                    "[MC] SQL executed from heartbeat: %s", s);
                        }
                        threadInfo.setDbVersion(rxVersion);
                    } else {

                        int rxDbPort = (int) extractLong(msg, "dbPort");

                        boolean missingDb = !Files.exists(threadInfo.dbPath());

                        if (missingDb) {
                            Log.error(ClusterHeartbeatThread.class,
                                    "[MC] DB copy required: missingDb=%s, localVersion=%d, remoteVersion=%d, primary=%s:%d",
                                    missingDb, threadInfo.dbVersion(), rxVersion, senderIp, rxDbPort);

                            if (rxDbPort > 0) {
                                if (threadInfo instanceof ServerManager sn) {
                                    if (!sn.tryLockCopy()) {
                                        Log.warn(ClusterHeartbeatThread.class,
                                                "[MC] DB copy request ignored: a copy is already in progress.");
                                        return;
                                    }
                                    try {
                                        requestDbCopyFromPrimary(senderIp, rxDbPort, rxVersion);
                                    } finally {
                                        sn.unlockCopy();
                                    }
                                } else {
                                    requestDbCopyFromPrimary(senderIp, rxDbPort, rxVersion);
                                }
                            } else {
                                Log.error(ClusterHeartbeatThread.class,
                                        "[MC] Heartbeat received without a valid dbPort – cannot request DB copy.");
                            }
                            return;
                        }

                        versionMismatch = rxVersion >= 0 && rxVersion != threadInfo.dbVersion();

                        Log.info(ClusterHeartbeatThread.class,
                                "[MC] DB version check: received=%d, local=%d",
                                rxVersion, threadInfo.dbVersion());

                        if (versionMismatch) {
                            Log.error(ClusterHeartbeatThread.class,
                                    "[MC] DB version mismatch detected (received=%d, local=%d). Shutting down server.",
                                    rxVersion, threadInfo.dbVersion());
                            threadInfo.shutdownServer();
                        }
                    }
                }
            }
        } catch (SocketTimeoutException ignore) {
            // no heartbeat in this cycle
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[MC-LOOP] Error receiving heartbeat: %s", e.getMessage());
            }
        }
    }

    /**
     * Primary-only part: accepts DB copy requests and handles them.
     *
     * @param _ss TCP server socket used for DB copy sessions
     */
    private void handleDbCopyAcceptLoop(ServerSocket _ss) {
        try {
            Socket s = _ss.accept(); // short timeout
            handleDbCopySession(s);
        } catch (SocketTimeoutException ignore) {
            // no incoming request
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[DBCOPY] Error accepting DB copy request: %s", e.getMessage());
            }
        }
    }

    /**
     * Extracts a long value from a semicolon-separated key=value payload.
     *
     * @param payload full payload string
     * @param key     key to search for
     * @return parsed long value or -1 on failure
     */
    private static long extractLong(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) {
            return -1;
        }
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Sends a multicast heartbeat with optional SQL payload (base64 encoded).
     *
     * @param ms  multicast socket
     * @param grp multicast group
     * @param sql list of SQL statements to include, or {@code null} for none
     * @throws IOException if sending fails
     */
    private void sendHeartbeat(MulticastSocket ms, InetAddress grp, List<String> sql) throws IOException {
        String encodedSql = "";
        if (sql != null && !sql.isEmpty()) {
            String joined = String.join(";;", sql);
            encodedSql = Base64.getEncoder()
                    .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
        }

        String beat = "MC_HB;id=servidor" + threadInfo.serverTcpPort() +
                ";role=MASTER" +
                ";version=" + threadInfo.dbVersion() +
                ";dbPort=" + threadInfo.dbCopyPort() +
                ";clientPort=" + threadInfo.serverTcpPort() +
                ";sql=" + encodedSql;

        byte[] data = beat.getBytes(StandardCharsets.UTF_8);
        ms.send(new DatagramPacket(data, data.length, grp, threadInfo.multicastPort()));
    }

    /**
     * Extracts a string value from a semicolon-separated key=value payload.
     *
     * @param payload full payload string
     * @param key     key to search for
     * @return string value or {@code null} if not found
     */
    private static String extractString(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        return raw.trim();
    }

    /**
     * Backup side: requests a DB copy from the primary and replaces the local DB file.
     *
     * @param primaryIp   primary server IP
     * @param primaryPort primary DB copy port
     * @param rxVersion   DB version received from heartbeat
     */
    private void requestDbCopyFromPrimary(String primaryIp, int primaryPort, long rxVersion) {
        Path target = threadInfo.dbPath();
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try (NetworkTcpConnection conn = NetworkTcpConnection.connect(
                primaryIp, primaryPort, Duration.ofSeconds(5))) {

            conn.setReadTimeout(Duration.ofSeconds(30));
            conn.sendMessage(new TcpMessage<>(MessageType.DB_REQUEST_COPY, "please"));

            var resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                Log.error(ClusterHeartbeatThread.class,
                        "[DBCOPY/RQ] Invalid response to copy request (expected ACK copy-start).");
                return;
            }

            Files.createDirectories(target.getParent());
            long size = conn.readLong();

            long total;
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                total = conn.receiveExactly(fos, size);
            }
            Log.info(ClusterHeartbeatThread.class,
                    "[DBCOPY/RQ] %d bytes received for DB copy -> %s%n", total, tmp);

            boolean moved = false;
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (Exception ignore) {
            }
            if (!moved) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                } catch (Exception ignore) {
                }
            }
            if (!moved) {
                try {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp);
                    moved = true;
                } catch (Exception e) {
                    Log.error(ClusterHeartbeatThread.class,
                            "[DBCOPY/RQ] Failed copy+delete when replacing DB file: %s", e.getMessage());
                }
            }

            if (moved) {
                Log.info(ClusterHeartbeatThread.class,
                        "[DBCOPY/RQ] DB copy completed at %s (new version=%d)", target, rxVersion);
                threadInfo.setDbVersion(rxVersion);
            } else {
                Log.error(ClusterHeartbeatThread.class,
                        "[DBCOPY/RQ] Could not replace %s (temporary file left: %s)", target, tmp);
            }

        } catch (Exception e) {
            Log.error(ClusterHeartbeatThread.class,
                    "[DBCOPY/RQ] Error during DB copy: %s", e.getMessage());
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Primary side: handles a single DB copy session for {@link MessageType#DB_REQUEST_COPY}.
     *
     * @param s accepted socket for the DB copy session
     */
    private void handleDbCopySession(Socket s) {
        try (NetworkTcpConnection connection = new NetworkTcpConnection(s)) {

            TcpMessage<?> req = connection.receiveMessage();
            if (req == null || req.getType() != MessageType.DB_REQUEST_COPY) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "bad-request", String.class));
                return;
            }

            if (!threadInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary", String.class));
                return;
            }

            connection.sendMessage(new TcpMessage<>(MessageType.ACK, "copy-start"));

            Path dbFile = threadInfo.dbPath();
            long size = Files.size(dbFile);
            connection.writeLong(size);

            try (FileInputStream fis = new FileInputStream(dbFile.toFile())) {
                long sent = connection.sendStreamViaObjectOut(fis, size);
                Log.info(ClusterHeartbeatThread.class,
                        "[DBCOPY] %d bytes sent in DB copy -> %s%n", sent, dbFile);
            }

        } catch (Exception e) {
            Log.error(ClusterHeartbeatThread.class,
                    "[DBCOPY] Error in DB copy session: %s", e.getMessage());
        } finally {
            try {
                s.close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Closes multicast and DB copy sockets if open.
     */
    @Override
    public void close() {
        if (ms != null) {
            try {
                ms.close();
            } catch (Exception ignore) {
            }
        }
        if (dbCopyServerSocket != null) {
            try {
                dbCopyServerSocket.close();
            } catch (Exception ignore) {
            }
        }
    }
}
