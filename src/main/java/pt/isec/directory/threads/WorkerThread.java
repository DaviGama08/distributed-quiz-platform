package pt.isec.directory.threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Worker thread for processing UDP messages from the queue.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Parse incoming protocol messages</li>
 *     <li>Update server registry (REGISTER/HEARTBEAT/DEREGISTER)</li>
 *     <li>Handle client LOGIN discovery</li>
 *     <li>Send textual responses back over UDP</li>
 * </ul>
 */
public class WorkerThread implements Runnable {
    private final IDirectoryThreadContext threadInfo;

    public WorkerThread(IDirectoryThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    @Override
    public void run() {
        Log.info(WorkerThread.class, "Worker inicializado...");
        try {
            // Continue processing as long as:
            //  - the system is running, OR
            //  - there are still messages in the queue
            while (threadInfo.isRunning() || !threadInfo.queue().isEmpty()) {
                UdpMessage msg = threadInfo.queue().poll(500, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    // timeout: re-check isRunning() in the outer loop
                    continue;
                }

                String payload = new String(msg.data(), 0, msg.length(), StandardCharsets.UTF_8);
                Log.info(WorkerThread.class,"Recebido: %s", payload);

                Map<String, String> kv = parseKv(payload);

                String type = kv.get("TYPE");
                if (type == null) {
                    send(msg, "400 BAD_REQUEST TYPE");
                    continue;
                }

                String reply;

                switch (type) {
                    case "LOGIN" -> {
                        Log.info(WorkerThread.class, "Cliente pede descoberta de servidor");
                        reply = handleLogin();
                    }

                    case "REGISTER", "HEARTBEAT", "DEREGISTER" -> {
                        reply = switch (type) {
                            case "REGISTER" -> {
                                Log.info(WorkerThread.class, "Servidor pede registo");
                                yield handleRegister(kv, msg.port());
                            }
                            case "HEARTBEAT" -> {
                                Log.info(WorkerThread.class, "Servidor envia heartbeat");
                                yield handleHeartbeat(kv);
                            }
                            case "DEREGISTER" -> {
                                Log.info(WorkerThread.class, "Servidor pede desregisto");
                                yield handleDeregister(kv);
                            }
                            default -> "500 INTERNAL_ERROR";
                        };
                    }

                    default -> {
                        Log.info(WorkerThread.class, "Tipo desconhecido: %s", type);
                        reply = "400 BAD_REQUEST TYPE";
                    }
                }

                send(msg, reply);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Log.error(WorkerThread.class, "Erro a enviar UDP: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.error(WorkerThread.class, "Erro inesperado no Worker: " + e.getMessage(), e);
        }

        Log.info(WorkerThread.class, "Worker terminou.");
    }

    /**
     * Handles a client login / server discovery request.
     * <p>
     * Request:
     * <pre>TYPE=LOGIN</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 PRINCIPAL <ip>:<port>} – primary server is available</li>
     *     <li>{@code 404 NO_PRINCIPAL} – no registered server</li>
     * </ul>
     */
    private String handleLogin() {
        ServerInfo principal;
        synchronized (threadInfo.serversLock()) {
            // pick the "master" server as the first one in insertion order
            var iterator = threadInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }
        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    /**
     * Handles a server deregistration request.
     * <p>
     * Request:
     * <pre>TYPE=DEREGISTER|ID=&lt;uuid&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK}</li>
     *     <li>{@code 400 BAD_REQUEST ID} – missing/blank ID</li>
     *     <li>{@code 409 CONFLICT UNKNOWN_ID} – unknown ID</li>
     * </ul>
     */
    private String handleDeregister(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isEmpty()) return "400 BAD_REQUEST ID";

        ServerInfo removed = threadInfo.servers().remove(id);
        if (removed == null) return "409 CONFLICT UNKNOWN_ID";

        synchronized (threadInfo.serversLock()) {
            threadInfo.serversOrdered().remove(id);
        }

        return "200 OK";
    }

    /**
     * Handles a heartbeat request from a server.
     * <p>
     * Request:
     * <pre>TYPE=HEARTBEAT|ID=&lt;uuid&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK <ip>:<port>} – ack + current primary endpoint</li>
     *     <li>{@code 400 BAD_REQUEST ID}</li>
     *     <li>{@code 409 CONFLICT UNKNOWN_ID}</li>
     *     <li>{@code 404 NO_PRINCIPAL}</li>
     * </ul>
     */
    private String handleHeartbeat(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isBlank()) return "400 BAD_REQUEST ID";

        ServerInfo si = threadInfo.servers().get(id);
        if (si == null) return "409 CONFLICT UNKNOWN_ID";

        long now = System.currentTimeMillis();
        si.setLastSeenMillis(now);

        ServerInfo principal;
        synchronized (threadInfo.serversLock()) {
            var iterator = threadInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }

        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 OK " + principal.tcpEndpoint();
    }

    /**
     * Handles registration of a new server.
     * <p>
     * Request:
     * <pre>TYPE=REGISTER|ID=&lt;uuid&gt;|TCP=&lt;ip&gt;:&lt;port&gt;|DBV=&lt;dbVersion&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK <ip>:<port>} – OK + current primary endpoint</li>
     *     <li>{@code 400 BAD_REQUEST ID/TCP/TCP_PORT}</li>
     *     <li>{@code 404 NO_PRINCIPAL}</li>
     *     <li>{@code 409 CONFLICT DUP_ENDPOINT}</li>
     * </ul>
     */
    private String handleRegister(Map<String, String> kv, int udpPort) {
        String id  = kv.get("ID");
        String tcp = kv.get("TCP");
        String dbv = kv.get("DBV"); // optional

        if (id == null || id.isBlank()) return "400 BAD_REQUEST ID";
        if (tcp == null || !tcp.contains(":")) return "400 BAD_REQUEST TCP";

        String[] parts = tcp.split(":", 2);
        String ip = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException nfe) {
            return "400 BAD_REQUEST TCP_PORT";
        }
        if (ip.isEmpty() || port <= 0 || port > 65535) return "400 BAD_REQUEST TCP";

        // Rule: do not allow two servers on the same <ip:port> with different IDs
        for (ServerInfo other : threadInfo.servers().values()) {
            if (other.getIp().equals(ip) && other.getTcpPort() == port && !other.getId().equals(id)) {
                Log.info(WorkerThread.class,
                        "rejeitado REGISTER: endpoint duplicado %s:%d para ID=%s (já existe %s)",
                        ip, port, id, other.getId());
                return "409 CONFLICT DUP_ENDPOINT";
            }
        }

        int version = 0;
        try {
            if (dbv != null && !dbv.isBlank()) version = Integer.parseInt(dbv.trim());
        } catch (Exception ignore) {}

        ServerInfo si = threadInfo.servers().get(id);
        if (si == null) {
            si = new ServerInfo(id, ip, port, udpPort);
            si.setLastSeenMillis(System.currentTimeMillis());
            threadInfo.servers().put(id, si);
            synchronized (threadInfo.serversLock()) {
                threadInfo.serversOrdered().put(id, si);
            }
        } else {
            // same ID coming back: update last seen
            si.setLastSeenMillis(System.currentTimeMillis());
        }

        ServerInfo principal;
        synchronized (threadInfo.serversLock()) {
            var iterator = threadInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }
        if (principal == null) return "404 NO_PRINCIPAL";

        // Optionally send global DB version; kept simple here
        return "200 OK " + principal.tcpEndpoint();
    }

    /**
     * Sends a UDP text response to the sender.
     *
     * @param to   original UDP message (contains sender address and port)
     * @param text response text (e.g., "200 OK", "404 NO_PRINCIPAL")
     * @throws IOException if sending fails
     */
    private void send(UdpMessage to, String text) throws IOException {
        byte[] out        = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(out, out.length, to.addr(), to.port());
        threadInfo.socket().send(dp);
    }

    /**
     * Parses a message in the form {@code KEY=VALUE|KEY=VALUE|...}.
     * <p>
     * Example:
     * <pre>"TYPE=REGISTER|ID=abc123|TCP=192.168.1.10:9999"</pre>
     *
     * @param s string with key-value pairs separated by pipe {@code '|'}
     * @return map with extracted key/value pairs
     */
    private static Map<String, String> parseKv(String s){
        Map<String, String> m = new LinkedHashMap<>();
        for(String token : s.split("\\|")){
            String t = token.trim();
            if(t.isEmpty())continue;
            int eq = t.indexOf('=');
            if(eq < 0) continue;
            String k = t.substring(0, eq).trim();
            String v = t.substring(eq + 1).trim();
            if (!k.isEmpty()) m.put(k, v);
        }
        return m;
    }
}
