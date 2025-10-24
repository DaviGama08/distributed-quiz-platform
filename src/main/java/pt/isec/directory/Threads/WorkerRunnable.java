package pt.isec.directory.Threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.IDirectoryService;
import pt.isec.directory.ServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/*================= COMANDOS DA FILA (BlockingQueue) =================
Listener → produz (put/offer) | Worker → consome (take/poll)

queue.put(e)      // Adiciona, BLOQUEIA se cheia
queue.offer(e)    // Adiciona, NÃO bloqueia
queue.take()      // Retira, BLOQUEIA se vazia
queue.poll()      // Retira, NÃO bloqueia
queue.poll(t,u)   // Retira, espera até timeout   ⏱
queue.peek()      // Lê sem remover (debug)
queue.isEmpty()   // Verifica se está vazia
queue.size()      // Quantidade de elementos (aprox.)
====================================================================*/

/**
 * Worker — retira mensagens da fila, interpreta o protocolo e responde.
 * VER=1|TYPE=<tipo>|(outros campos)
 *  - VER=1 é obrigatório
 *  - TYPE=REGISTER   | ID=<serverId> | TCP=<ip:port>
 *  - TYPE=HEARTBEAT  | ID=<serverId> | DBV=<dbVersion>     (DBV opcional se não usares)
 *  - TYPE=DEREGISTER | ID=<serverId>
 *  - TYPE=CLIENT_QUERY
 *
 * Respostas:
 *  - "200 OK"
 *  - "200 PRINCIPAL <ip:port>"
 *  - "400 BAD_REQUEST <motivo>"
 *  - "404 NO_PRINCIPAL"
 *  - "409 CONFLICT <motivo>"
 *  - "500 ERROR <motivo>"
 */

public class WorkerRunnable implements Runnable{
    private final IDirectoryService directoryService;

    public WorkerRunnable(IDirectoryService directoryService){this.directoryService = directoryService;}

    @Override
    public void run() {
        System.out.println("Worker inicializado...");
        while (directoryService.getRunning()){
            try {
                UdpMessage msg = directoryService.queue().take();

                String payload = new String(msg.data(), 0, msg.length(), StandardCharsets.UTF_8);
                System.out.println("Recebido: " + payload);

                Map<String, String> kv = parseKv(payload);

                //Versão sempre será igual a 1
                String ver = kv.get("VER");
                if (!"1".equals(ver)) {
                    send(msg, "400 BAD_REQUEST VER");
                    continue;
                }
                //Estrutura do request está inválido
                String type = kv.get("TYPE");
                if (type == null) {
                    send(msg, "400 BAD_REQUEST TYPE");
                    continue;
                }

                //Verifica o TYPE e de acordo com ele chama a função associada a ele
                // e fazer o tratamento dos próximos argumentos.
                String reply;
                switch (type) {
                    case "REGISTER" -> reply = handleRegister(kv);
                    case "HEARTBEAT" -> reply = handleHeartbeat(kv);
                    case "DEREGISTER" -> reply = handleDeregister(kv);
                    case "CLIENT_QUERY" -> reply = handleClientQuery();
                    default -> reply = "400 BAD_REQUEST TYPE";
                }
                send(msg, reply);

            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //Não precisamos do Map aqui já que não existem mais argumentos depois do TYPE
    private String handleClientQuery() {
        // escolhe um servidor “principal” — aqui usamos o primeiro disponível
        ServerInfo principal = directoryService.getServers().values().stream().findFirst().orElse(null);
        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    private String handleDeregister(Map<String, String> kv) {
        String id = kv.get("ID");
        if(id == null || id.isEmpty()) return "400 BAD_REQUEST ID";

        ServerInfo removed = directoryService.getServers().remove(id);
        if (removed == null) return "409 CONFLICT UNKNOWN_ID";

        synchronized (directoryService.serversLock()) {
            directoryService.getServersOrdered().remove(id);
        }

        return "200 OK";
    }

    private String handleHeartbeat(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isBlank()) return "400 BAD_REQUEST ID";

        ServerInfo si = directoryService.getServers().get(id);
        if (si == null) return "409 CONFLICT UNKNOWN_ID";

        return "200 OK";
    }

    private String handleRegister(Map<String, String> kv) {
        String id = kv.get("ID");
        String tcp = kv.get("TCP");
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

        ConcurrentMap<String, ServerInfo> map = directoryService.getServers();
        ServerInfo prev = map.put(id, new ServerInfo(port, id, ip));

        return "200 OK";
    }

    private void send(UdpMessage to, String text) throws IOException {
        byte[] out        = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(out, out.length, to.addr(), to.port());
        directoryService.getSocket().send(dp);
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

