package pt.isec.directory.threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.IDirectoryManager;
import pt.isec.directory.ServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkerThread implements Runnable{
    private final IDirectoryManager tInfo;

    public WorkerThread(IDirectoryManager tInfo){this.tInfo = tInfo;}

    @Override
    public void run() {
        System.out.println("Worker inicializado...");
        while (tInfo.isRunning()){
            try {
                UdpMessage msg = tInfo.queue().take();

                String payload = new String(msg.data(), 0, msg.length(), StandardCharsets.UTF_8);
                System.out.println("[Worker] Recebido: " + payload);

                Map<String, String> kv = parseKv(payload);

                String type = kv.get("TYPE");
                if (type == null) {
                    send(msg, "400 BAD_REQUEST TYPE");
                    continue;
                }

                String reply;

                // Distinguir entre mensagens de CLIENTE (sem VER) e SERVIDOR (com VER)
                switch (type) {
                    // === MENSAGENS DE CLIENTE (sem VER requerido) ===
                    case "LOGIN" -> {
                        System.out.println("[Worker] → Cliente pede descoberta de servidor");
                        reply = handleLogin();
                    }

                    // === MENSAGENS DE SERVIDOR (requerem VER=1) ===
                    case "REGISTER", "HEARTBEAT", "DEREGISTER" -> {
                        String ver = kv.get("VER");
                        //TODO ANALISAR PARA O CASO DE A VERSÃO DA BASE DE DADOS MUDAR
                        if (!"1".equals(ver)) {
                            send(msg, "400 BAD_REQUEST VER");
                            continue;
                        }

                        //Para o servidor
                        reply = switch (type) {
                            case "REGISTER" -> {
                                System.out.println("[Worker] → Servidor pede registo");
                                yield handleRegister(kv, msg.port());
                            }
                            case "HEARTBEAT" -> {
                                System.out.println("[Worker] → Servidor envia heartbeat");
                                yield handleHeartbeat(kv);
                            }
                            case "DEREGISTER" -> {
                                System.out.println("[Worker] → Servidor pede desregisto");
                                yield handleDeregister(kv);
                            }
                            default -> "500 INTERNAL_ERROR";
                        };
                    }

                    default -> {
                        System.out.println("[Worker] → Tipo desconhecido: " + type);
                        reply = "400 BAD_REQUEST TYPE";
                    }
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
        System.out.println("Worker terminou.");
    }


    /**
     * Trata pedido de login/descoberta do servidor principal por parte do cliente.
     *
     * Protocolo esperado:
     * REQUEST:  TYPE=LOGIN
     * RESPONSE: 200 PRINCIPAL <ip>:<port> (servidor principal disponível via TCP)
     *           404 NO_PRINCIPAL (nenhum servidor registado)
     */
    private String handleLogin() {
        ServerInfo principal;
        synchronized (tInfo.serversLock()) {
            //vai buscar o servidor mais recente "Master"
            var iterator = tInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }
        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    /**
     * Trata pedido de desregisto de um servidor.
     *
     * Protocolo esperado:
     * REQUEST:  VER=1|TYPE=DEREGISTER|ID=<uuid>
     * RESPONSE: 200 OK (servidor removido com sucesso)
     *           400 BAD_REQUEST ID (ID ausente ou vazio)
     *           409 CONFLICT UNKNOWN_ID (ID desconhecido)
     */
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

    /**
     * Trata pedido de heartbeat (manter servidor ativo).
     *
     * Protocolo esperado:
     * REQUEST:  VER=1|TYPE=HEARTBEAT|ID=<uuid>
     * RESPONSE: 200 OK (heartbeat recebido, timestamp atualizado)
     *           400 BAD_REQUEST ID (ID ausente ou vazio)
     *           409 CONFLICT UNKNOWN_ID (ID desconhecido)
     */
    private String handleHeartbeat(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isBlank()) return "400 BAD_REQUEST ID";

        ServerInfo si = tInfo.servers().get(id);
        if (si == null) return "409 CONFLICT UNKNOWN_ID";

        long now = System.currentTimeMillis();
        si.setLastSeenMillis(now);

        ServerInfo principal;
        synchronized (tInfo.serversLock()) {
            var iterator = tInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }

        if (principal == null) return "404 NO_PRINCIPAL";
        return "200 OK " + principal.tcpEndpoint();
    }

    /**
     * Trata pedido de registo de um novo servidor.
     *
     * REQUEST:  VER=1|TYPE=REGISTER|ID=<uuid>|TCP=<ip>:<port>|DBV=<versão_bd>
     * RESPONSE: 200 PRINCIPAL <ip>:<port>|DBV=<versão_global_ou_-1>
     *           400 BAD_REQUEST ID/TCP/TCP_PORT
     *           404 NO_PRINCIPAL
     */
    // WorkerThread.java
    private String handleRegister(Map<String, String> kv, int udpPort) {
        String id  = kv.get("ID");
        String tcp = kv.get("TCP");
        String dbv = kv.get("DBV"); // opcional

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

        // === regra: não permitir dois servidores no MESMO <ip:port> com IDs diferentes
        for (ServerInfo other : tInfo.servers().values()) {
            if (other.getIp().equals(ip) && other.getTcpPort() == port && !other.getId().equals(id)) {
                System.out.printf("[Diretoria] rejeitado REGISTER: endpoint duplicado %s:%d para ID=%s (já existe %s)%n",
                        ip, port, id, other.getId());
                return "409 CONFLICT DUP_ENDPOINT";
            }
        }

        int version = 0;
        try { if (dbv != null && !dbv.isBlank()) version = Integer.parseInt(dbv.trim()); } catch (Exception ignore) {}

        ServerInfo si = tInfo.servers().get(id);
        if (si == null) {
            si = new ServerInfo(id, ip, port, udpPort);
            si.setLastSeenMillis(System.currentTimeMillis());
            tInfo.servers().put(id, si);
            synchronized (tInfo.serversLock()) {
                tInfo.serversOrdered().put(id, si);
            }
        } else {
            // mesmo ID a voltar: atualiza dados
            si.setLastSeenMillis(System.currentTimeMillis());
        }

        ServerInfo principal;
        synchronized (tInfo.serversLock()) {
            var iterator = tInfo.serversOrdered().values().iterator();
            principal = iterator.hasNext() ? iterator.next() : null;
        }
        if (principal == null) return "404 NO_PRINCIPAL";

        // devolve também a versão global se quiseres (opcional). Se não usas, podes remover "|DBV=..."
        return "200 OK " + principal.tcpEndpoint();
    }


    /**
     * Envia resposta UDP de volta ao remetente.
     *
     * @param to Mensagem UDP original (contém endereço e porta do remetente)
     * @param text Resposta em formato texto (ex: "200 OK", "404 NO_PRINCIPAL")
     * @throws IOException se houver erro ao enviar o datagrama
     */
    private void send(UdpMessage to, String text) throws IOException {
        byte[] out        = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(out, out.length, to.addr(), to.port());
        tInfo.socket().send(dp);
    }

    /**
     * Faz parsing de uma mensagem no formato KEY=VALUE|KEY=VALUE|...
     *
     * Exemplo: "VER=1|TYPE=REGISTER|ID=abc123|TCP=192.168.1.10:9999"
     * Resultado: Map{"VER"->"1", "TYPE"->"REGISTER", "ID"->"abc123", "TCP"->"192.168.1.10:9999"}
     *
     * @param s String com pares KEY=VALUE separados por pipe '|'
     * @return Map com os pares chave-valor extraídos
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
