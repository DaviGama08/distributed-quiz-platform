package pt.isec.server.threads;

import pt.isec.common.dto.cluster.DatabaseSnapshotMetadata;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.util.Log;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.replication.SqliteSnapshotManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Exchanges metadata-only heartbeats and consistent SQLite snapshots. */
public class ClusterHeartbeatThread implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;
    private static final int ACCEPT_TIMEOUT_MS = 500;
    private static final int BUFFER_SIZE = 4096;

    private final IServerThreadContext threadInfo;
    private MulticastSocket multicastSocket;
    private ServerSocket dbCopyServerSocket;

    public ClusterHeartbeatThread(IServerThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    @Override
    public void run() {
        try (MulticastSocket ms = new MulticastSocket(threadInfo.multicastPort());
             ServerSocket copyServer = new ServerSocket(threadInfo.dbCopyPort())) {
            multicastSocket = ms;
            dbCopyServerSocket = copyServer;
            ms.setReuseAddress(true);
            ms.setSoTimeout(RX_TIMEOUT_MS);
            ms.setTimeToLive(MULTICAST_TTL);
            ms.setNetworkInterface(threadInfo.multicastInterface());
            configureLoopbackMode(ms);

            InetAddress group = InetAddress.getByName(threadInfo.multicastGroup());
            ms.joinGroup(new InetSocketAddress(group, threadInfo.multicastPort()),
                    threadInfo.multicastInterface());
            copyServer.setSoTimeout(ACCEPT_TIMEOUT_MS);

            long lastHeartbeat = 0L;
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (threadInfo.isRunning()) {
                long now = System.currentTimeMillis();
                if (threadInfo.isPrimary() && now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    sendHeartbeat(ms, group);
                    lastHeartbeat = now;
                }
                if (!threadInfo.isPrimary()) {
                    packet.setLength(buffer.length);
                    receiveHeartbeat(ms, packet);
                }
                acceptCopyRequest(copyServer);
                Thread.sleep(LOOP_SLEEP_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class, "[CLUSTER] Main loop failed: %s", e.getMessage());
            }
        }
        Log.info(ClusterHeartbeatThread.class, "Cluster heartbeat thread terminated.");
    }

    private void sendHeartbeat(MulticastSocket socket, InetAddress group) throws IOException {
        String heartbeat = buildHeartbeat(threadInfo.id(), threadInfo.dbVersion(),
                threadInfo.serverTcpPort(), threadInfo.dbCopyPort());
        byte[] bytes = heartbeat.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(bytes, bytes.length, group, threadInfo.multicastPort()));
        Log.infoMaster(ClusterHeartbeatThread.class,
                "[MC] Metadata heartbeat sent: serverId=%s dbVersion=%d", threadInfo.id(), threadInfo.dbVersion());
    }

    static String buildHeartbeat(String serverId, long dbVersion, int clientPort, int dbCopyPort) {
        return "MC_HB;serverId=" + serverId
                + ";role=PRIMARY"
                + ";dbVersion=" + dbVersion
                + ";clientPort=" + clientPort
                + ";dbCopyPort=" + dbCopyPort;
    }

    private void receiveHeartbeat(MulticastSocket socket, DatagramPacket packet) {
        try {
            socket.receive(packet);
            String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            if (!payload.startsWith("MC_HB;")) {
                return;
            }

            String remoteId = extractString(payload, "serverId");
            if (threadInfo.id().equals(remoteId)) {
                return;
            }
            long remoteVersion = extractLong(payload, "dbVersion");
            int copyPort = (int) extractLong(payload, "dbCopyPort");
            Path localDatabase = threadInfo.dbPath();
            boolean missing = !Files.isRegularFile(localDatabase);
            long localVersion = missing ? -1L : threadInfo.dbVersion();

            if (shouldRequestSnapshot(localVersion, remoteVersion, missing)) {
                requestDbCopy(packet.getAddress().getHostAddress(), copyPort, remoteVersion);
            } else if (remoteVersion >= 0 && remoteVersion < localVersion) {
                Log.warn(ClusterHeartbeatThread.class,
                        "[MC] Ignoring stale primary metadata: remote=%d local=%d", remoteVersion, localVersion);
            }
        } catch (SocketTimeoutException ignored) {
            // A lost datagram is recovered by evaluating the next heartbeat.
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class, "[MC] Heartbeat processing failed: %s", e.getMessage());
            }
        }
    }

    static boolean shouldRequestSnapshot(long localVersion, long remoteVersion, boolean missingDatabase) {
        return remoteVersion >= 0 && (missingDatabase || remoteVersion > localVersion);
    }

    private void requestDbCopy(String primaryIp, int primaryPort, long announcedVersion) {
        if (primaryPort <= 0) {
            Log.warn(ClusterHeartbeatThread.class, "[DB COPY] Invalid copy port: %d", primaryPort);
            return;
        }
        if (!threadInfo.tryLockCopy()) {
            return;
        }
        try {
            requestDbCopyFromPrimary(primaryIp, primaryPort, announcedVersion);
        } finally {
            threadInfo.unlockCopy();
        }
    }

    void requestDbCopyFromPrimary(String primaryIp, int primaryPort, long announcedVersion) {
        Path target = threadInfo.dbPath().toAbsolutePath().normalize();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".incoming-", ".db");
            try (NetworkTcpConnection connection = NetworkTcpConnection.connect(
                    primaryIp, primaryPort, Duration.ofSeconds(5))) {
                connection.setReadTimeout(Duration.ofSeconds(30));
                connection.sendMessage(new TcpMessage<>(MessageType.DB_REQUEST_COPY, "snapshot"));

                TcpMessage<?> response = connection.receiveMessage();
                if (response == null || response.getType() != MessageType.ACK
                        || !(response.getData() instanceof DatabaseSnapshotMetadata metadata)) {
                    throw new IOException("Primary rejected the snapshot request");
                }
                if (metadata.dbVersion() < announcedVersion) {
                    throw new IOException("Snapshot is older than the announced heartbeat");
                }

                try (var output = Files.newOutputStream(temporary)) {
                    connection.receiveExactly(output, metadata.size());
                }
                SqliteSnapshotManager.validateSnapshot(temporary, metadata);
                SqliteSnapshotManager.replaceAtomically(temporary, target);
                temporary = null;

                long installedVersion = SqliteSnapshotManager.inspectDatabaseVersion(target);
                if (installedVersion != metadata.dbVersion()) {
                    throw new IOException("Installed database version changed unexpectedly");
                }
                Log.info(ClusterHeartbeatThread.class,
                        "[DB COPY] Installed validated snapshot version %d at %s",
                        installedVersion, target);
            }
        } catch (Exception e) {
            Log.warn(ClusterHeartbeatThread.class,
                    "[DB COPY] Snapshot synchronization failed; the next heartbeat will retry: %s", e.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void acceptCopyRequest(ServerSocket serverSocket) {
        try {
            Socket accepted = serverSocket.accept();
            handleDbCopySession(accepted);
        } catch (SocketTimeoutException ignored) {
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class, "[DB COPY] Accept failed: %s", e.getMessage());
            }
        }
    }

    void handleDbCopySession(Socket acceptedSocket) {
        try (Socket socket = acceptedSocket;
             NetworkTcpConnection connection = new NetworkTcpConnection(socket)) {
            connection.setReadTimeout(Duration.ofSeconds(10));
            TcpMessage<?> request = connection.receiveMessage();
            if (request == null || request.getType() != MessageType.DB_REQUEST_COPY) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "bad-request"));
                return;
            }
            if (!threadInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary"));
                return;
            }

            try (SqliteSnapshotManager.SnapshotFile snapshot =
                         SqliteSnapshotManager.createConsistentSnapshot(threadInfo.dbPath())) {
                connection.sendMessage(new TcpMessage<>(MessageType.ACK, snapshot.metadata()));
                try (var input = Files.newInputStream(snapshot.path())) {
                    long sent = connection.sendStreamViaObjectOut(input, snapshot.metadata().size());
                    if (sent != snapshot.metadata().size()) {
                        throw new IOException("Snapshot stream ended early");
                    }
                }
                Log.info(ClusterHeartbeatThread.class,
                        "[DB COPY] Sent validated snapshot version %d (%d bytes)",
                        snapshot.metadata().dbVersion(), snapshot.metadata().size());
            }
        } catch (Exception e) {
            Log.warn(ClusterHeartbeatThread.class, "[DB COPY] Session failed: %s", e.getMessage());
        }
    }

    private static long extractLong(String payload, String key) {
        String raw = extractString(payload, key);
        if (raw == null) {
            return -1L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static String extractString(String payload, String key) {
        String needle = key + "=";
        int start = payload.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = payload.indexOf(';', start);
        return (end < 0 ? payload.substring(start) : payload.substring(start, end)).trim();
    }

    @SuppressWarnings("deprecation")
    private static void configureLoopbackMode(MulticastSocket socket) throws IOException {
        socket.setLoopbackMode(false);
    }

    @Override
    public void close() {
        if (multicastSocket != null) {
            multicastSocket.close();
        }
        if (dbCopyServerSocket != null) {
            try {
                dbCopyServerSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
