package pt.isec.server.threads;

import pt.isec.server.core.IServerManager;
import pt.isec.server.core.ServerManager;
import pt.isec.common.util.Log;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Thread responsible for communicating with the directory service via UDP.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Send {@code REGISTER} on startup</li>
 *     <li>Receive and process the current primary server information</li>
 *     <li>Initialize database path based on primary/backup role</li>
 *     <li>Send periodic {@code HEARTBEAT}</li>
 *     <li>Send {@code DEREGISTER} on shutdown</li>
 * </ul>
 */
public class DirectoryHeartbeatThread implements Runnable, AutoCloseable {
    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int RETRY_COUNT = 3;
    private static final int SLEEP_INTERVAL_MS = 50;
    private static final int BUFFER_SIZE = 512;

    private final IServerManager managerTheardInfo;
    private DatagramSocket socket;

    /**
     * Creates a new directory heartbeat thread.
     *
     * @param managerTheardInfo server manager providing directory and DB information
     */
    public DirectoryHeartbeatThread(IServerManager managerTheardInfo) {
        this.managerTheardInfo = managerTheardInfo;
    }

    /**
     * Main loop: register, obtain the primary server, initialize DB (primary/backup),
     * send heartbeats, and deregister on shutdown.
     */
    @Override
    public void run() {
        try (DatagramSocket s = new DatagramSocket()) {
            socket = s;
            s.setSoTimeout(SOCKET_TIMEOUT_MS);

            InetAddress dirAddr = InetAddress.getByName(managerTheardInfo.directoryHost());
            int dirPort = managerTheardInfo.directoryPort();

            // REGISTER
            String registerMsg = requestKeyValue(
                    "TYPE", "REGISTER",
                    "ID", managerTheardInfo.id(),
                    "TCP", managerTheardInfo.serverTcpIp() + ":" + managerTheardInfo.serverTcpPort(), // this server TCP endpoint
                    "DBV", String.valueOf(managerTheardInfo.dbVersion()),                            // DB version
                    "DBP", String.valueOf(managerTheardInfo.dbCopyPort())                            // DB copy port
            );
            send(s, dirAddr, dirPort, registerMsg);

            // waits "200 PRINCIPAL ip:port[|DBV=X]"
            Endpoint reply = waitPrincipal(s);
            if (reply == null) {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] No response from directory for REGISTER; shutting down server.");
                managerTheardInfo.shutdownServer();
                return;
            }

            managerTheardInfo.setPrimary(reply.ip, reply.port);
            boolean iAmPrimary =
                    Objects.equals(reply.ip, managerTheardInfo.serverTcpIp()) &&
                            reply.port == managerTheardInfo.serverTcpPort();

            // version from directory: -1 = first time
            if (reply.dbv != null) {
                if (reply.dbv == -1 && iAmPrimary) {
                    managerTheardInfo.setDbVersion(1); // first time: create quiz-01.db
                } else if (reply.dbv >= 0) {
                    managerTheardInfo.setDbVersion(reply.dbv);
                }
            }

            if (managerTheardInfo instanceof ServerManager node) {
                if (iAmPrimary) {
                    try {
                        // primary chooses DB: newest or new
                        node.initDbPathAsPrincipalOnStartup();
                        // create/open DB and schema
                        node.initDatabaseLayerIfNeeded();
                    } catch (Exception e) {
                        Log.error(DirectoryHeartbeatThread.class,
                                "[DB] Failed to initialize primary server database: %s", e.getMessage());
                    }
                } else {
                    // backup: define local DB path
                    node.initDbPathAsBackupOnStartup();

                    if (!Files.exists(node.dbPath())) {
                        Log.info(DirectoryHeartbeatThread.class,
                                "[DB] Backup server without local database; will await multicast heartbeat to copy.");
                    }
                }
            } else {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DB] IServerManager instance is not a ServerManager; unexpected configuration.");
            }

            Log.info(DirectoryHeartbeatThread.class,
                    "[DIR] Current primary server: %s:%d | isPrimary=%s | dbVersion=%d",
                    reply.ip, reply.port, iAmPrimary, managerTheardInfo.dbVersion());

            long last = 0;

            // HEARTBEAT — always send current DB version (managerTheardInfo.dbVersion())
            while (managerTheardInfo.isRunning()) {
                long now = System.currentTimeMillis();

                if (now - last >= HEARTBEAT_INTERVAL_MS) {
                    String hb = requestKeyValue(
                            "TYPE", "HEARTBEAT",
                            "ID", managerTheardInfo.id()
                    );
                    send(s, dirAddr, dirPort, hb);
                    last = now;
                }

                Endpoint cur = tryReceivePrincipal(s);
                if (cur != null) {
                    managerTheardInfo.setPrimary(cur.ip, cur.port);
                    boolean prim =
                            Objects.equals(cur.ip, managerTheardInfo.serverTcpIp()) &&
                                    cur.port == managerTheardInfo.serverTcpPort();
                    Log.info(DirectoryHeartbeatThread.class,
                            "[DIR] Primary reported by directory: %s:%d | isPrimary=%s",
                            cur.ip, cur.port, prim);
                }
                Thread.sleep(SLEEP_INTERVAL_MS);
            }

            // DEREGISTER
            String deregMsg = requestKeyValue(
                    "TYPE", "DEREGISTER",
                    "ID", managerTheardInfo.id()
            );
            send(s, dirAddr, dirPort, deregMsg);

        } catch (Exception e) {
            if (managerTheardInfo.isRunning()) {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] Error in directory heartbeat thread: %s", e.getMessage());
            }
        } finally {
            Log.info(DirectoryHeartbeatThread.class, "Directory heartbeat thread terminated.");
            try {
                close();
            } catch (Exception ignore) {
            }
        }
    }

    /* ---------- UDP helpers ---------- */

    /**
     * Simple DTO-like record holding directory response details.
     *
     * @param ip  primary server IP
     * @param port primary server TCP port
     * @param dbv  DB version (may be {@code null})
     */
    private record Endpoint(String ip, int port, Integer dbv) { }

    /**
     * Builds a KEY=VALUE|KEY=VALUE|... style message from an array of key/value pairs.
     *
     * @param keyValue array of key/value pairs (must have even length)
     * @return formatted message
     */
    private static String requestKeyValue(String... keyValue) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keyValue.length; i += 2) {
            b.append(keyValue[i]).append('=').append(keyValue[i + 1]).append('|');
        }
        return b.toString();
    }

    /**
     * Sends a UDP message to the specified address/port.
     *
     * @param s    datagram socket
     * @param addr destination address
     * @param port destination port
     * @param msg  message to send
     * @throws IOException if sending fails
     */
    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(data, data.length, addr, port));
    }

    /**
     * Attempts to receive primary server information with a limited number of retries.
     *
     * @param s datagram socket
     * @return {@link Endpoint} information or {@code null} if no valid response is received
     */
    private Endpoint waitPrincipal(DatagramSocket s) {
        for (int i = 0; i < RETRY_COUNT; i++) {
            Endpoint ep = tryReceivePrincipal(s);
            if (ep != null) {
                return ep;
            }
        }
        return null;
    }

    /**
     * Tries to receive and parse a directory response with primary information.
     *
     * @param s datagram socket
     * @return {@link Endpoint} data or {@code null} on timeout/error
     */
    private Endpoint tryReceivePrincipal(DatagramSocket s) {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            s.receive(dp);
            String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();

            // Another server already using this endpoint
            if (resp.startsWith("409 CONFLICT DUP_ENDPOINT")) {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] Another server is already active with this endpoint (ip:port). Terminating process.");
                System.exit(2);
                return null; // unreachable
            }

            // After TTL without heartbeat, directory may send SHUTDOWN / 404 NO_PRINCIPAL
            if (resp.startsWith("SHUTDOWN") || resp.startsWith("404 NO_PRINCIPAL")) {
                managerTheardInfo.shutdownServer();
                Log.info(DirectoryHeartbeatThread.class,
                        "[DIR] Directory requested shutdown (or no primary available). Server will be terminated.");
                return null;
            }

            String body = resp.substring("200 OK ".length());
            String[] mainAndRest = body.split("\\|", 2);
            String[] ipPort = mainAndRest[0].split(":");
            if (ipPort.length != 2) {
                return null;
            }

            String ip = ipPort[0];
            int port = Integer.parseInt(ipPort[1]);
            Integer dbv = null;

            return new Endpoint(ip, port, dbv);
        } catch (SocketTimeoutException e) {
            // normal timeout: no response in this interval
            return null;
        } catch (Exception e) {
            // generic error receiving or parsing the response
            return null;
        }
    }

    /**
     * Closes the UDP socket if open.
     */
    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
