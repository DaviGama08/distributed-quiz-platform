package pt.isec.directory.threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.service.IDirectoryService;
import pt.isec.directory.ServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkerRunnable implements Runnable{
    private final IDirectoryService tInfo;

    public WorkerRunnable(IDirectoryService tInfo){this.tInfo = tInfo;}

    @Override
    public void run() {
        System.out.println("Worker inicializado...");
        while (tInfo.isRunning()){
            try {
                UdpMessage msg = tInfo.queue().take();

                String payload = new String(msg.data(), 0, msg.length(), StandardCharsets.UTF_8);
                System.out.println("Recebido: " + payload);

                Map<String, String> kv = parseKv(payload);

                String ver = kv.get("VER");

                if (!"1".equals(ver)) {
                    send(msg, "400 BAD_REQUEST VER");
                    continue;
                }


                String type = kv.get("TYPE");
                if (type == null) {
                    send(msg, "400 BAD_REQUEST TYPE");
                    continue;
                }

                String reply;
                switch (type) {
                    case "REGISTER"   -> reply = handleRegister(kv);
                    case "HEARTBEAT"  -> reply = handleHeartbeat(kv);
                    case "DEREGISTER" -> reply = handleDeregister(kv);
                    case "CLIENT_QUERY" -> reply = handleClientQuery();
                    default -> reply = "400 BAD_REQUEST TYPE";
                }
                send(msg, reply);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                System.err.println("Erro a enviar UDP: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Erro inesperado no Worker: " + e.getMessage());
            }
        }
    }

    private String handleClientQuery() {
        ServerInfo principal;
        synchronized (tInfo.serversLock()) {
            principal = tInfo.serversOrdered().values().stream().findFirst().orElse(null);
        }
        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    private String handleDeregister(Map<String, String> kv) {
        String id = kv.get("ID");
        if(id == null || id.isEmpty()) return "400 BAD_REQUEST ID";

        ServerInfo removed = tInfo.servers().remove(id);
        if (removed == null) return "409 CONFLICT UNKNOWN_ID";

        synchronized (tInfo.serversLock()) {
            tInfo.serversOrdered().remove(id);
        }

        return "200 OK";
    }

    private String handleHeartbeat(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isBlank()) return "400 BAD_REQUEST ID";

        ServerInfo si = tInfo.servers().get(id);
        if (si == null) return "409 CONFLICT UNKNOWN_ID";

        long now = System.currentTimeMillis();
        si.setLastSeenMillis(now);

        String dbv = kv.get("DBV");
        if (dbv != null && !dbv.isBlank()) {
            try {
                int newVer = Integer.parseInt(dbv.trim());
                if(newVer != si.getVersion()) {
                    System.out.printf("[HB] %s: version %d -> %d%n", si.displayName(), si.getVersion(), newVer);
                    si.setVersion(newVer); // atualiza valor guardado
                }
            } catch (NumberFormatException ignore) {}
        }

        return "200 OK";
    }

    private String handleRegister(Map<String, String> kv) {
        String id = kv.get("ID");
        String tcp = kv.get("TCP");
        String dbv = kv.get("DBV"); // se vier, aproveitamos

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

        int version = 1;
        try { if (dbv != null) version = Integer.parseInt(dbv.trim()); } catch (Exception ignore) {}

        ServerInfo si = tInfo.servers().get(id);
        if (si == null) {
            si = new ServerInfo(id, ip, port, version);
            si.setLastSeenMillis(System.currentTimeMillis());
            tInfo.servers().put(id, si);
            synchronized (tInfo.serversLock()) {
                tInfo.serversOrdered().put(id, si);
            }
        } else {
            si.setLastSeenMillis(System.currentTimeMillis());
            si.setVersion(version);
        }

        ServerInfo principal;
        synchronized (tInfo.serversLock()) {
            principal = tInfo.serversOrdered().values().stream().findFirst().orElse(null);
        }
        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    private void send(UdpMessage to, String text) throws IOException {
        byte[] out        = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(out, out.length, to.addr(), to.port());
        tInfo.socket().send(dp);
    }

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
