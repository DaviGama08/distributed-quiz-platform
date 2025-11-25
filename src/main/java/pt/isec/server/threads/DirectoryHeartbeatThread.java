package pt.isec.server.threads;
import pt.isec.server.core.IServerManager;
import pt.isec.server.core.ServerManager;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public class DirectoryHeartbeatThread implements Runnable, AutoCloseable {
    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int RETRY_COUNT = 3;
    private static final int SLEEP_INTERVAL_MS = 50;
    private static final int BUFFER_SIZE = 512;

    private final IServerManager managerTheardInfo;
    private DatagramSocket socket;

    public DirectoryHeartbeatThread(IServerManager managerTheardInfo) { this.managerTheardInfo = managerTheardInfo; }

    @Override
    public void run() {
        try (DatagramSocket s = new DatagramSocket()) {
            socket = s;
            s.setSoTimeout(SOCKET_TIMEOUT_MS);

            InetAddress dirAddr = InetAddress.getByName(managerTheardInfo.directoryHost());
            int dirPort = managerTheardInfo.directoryPort();

            // REGISTER
            String registerMsg = requestKeyValue(
                    "TYPE","REGISTER",
                    "ID", managerTheardInfo.id(),
                    "TCP", managerTheardInfo.serverTcpIp() + ":" + managerTheardInfo.serverTcpPort(), //ip e porto do servidor para o cliente saber
                    "DBV", String.valueOf(managerTheardInfo.dbVersion()), //versão da base de dados
                    "DBP", String.valueOf(managerTheardInfo.dbCopyPort()) //porto da base de dados
            );
            send(s, dirAddr, dirPort, registerMsg);

            // espera "200 PRINCIPAL ip:port[|DBV=X]"
            Endpoint reply = waitPrincipal(s);
            if (reply == null) {
                System.err.println("[DIR] sem resposta da diretoria");
                managerTheardInfo.stopRunning(false); // running=false e interrompe threads
                return;
            }

            managerTheardInfo.setPrimary(reply.ip, reply.port);
            boolean iAmPrimary = Objects.equals(reply.ip, managerTheardInfo.serverTcpIp()) && reply.port == managerTheardInfo.serverTcpPort();

            // versão enviada pela diretoria: -1 = primeira vez
            if (reply.dbv != null) {
                if (reply.dbv == -1 && iAmPrimary) {
                    managerTheardInfo.setDbVersion(1); // primeira vez: cria quiz-01.db
                } else if (reply.dbv >= 0) {
                    managerTheardInfo.setDbVersion(reply.dbv);
                }
            }

            if (iAmPrimary) {
                try {
                    // garantir que estamos a usar o ServerNode concreto
                    if (managerTheardInfo instanceof ServerManager node) {
                        node.initDatabaseLayerIfNeeded();
                    } else {
                        System.err.println("[DB] tInfo não é ServerNode — não consigo inicializar BD/Auth.");
                    }
                } catch (Exception e) {
                    System.err.println("[DB] erro a inicializar base de dados: " + e.getMessage());
                }
            } else {
                if (!Files.exists(managerTheardInfo.dbPath())) {
                    System.out.println("[DB] backup sem base de dados local; vai aguardar heartbeat multicast para copiar.");
                }
            }

            System.out.printf("[DIR] principal %s:%d | souPrimario=%s | versao=%d%n",
                    reply.ip, reply.port, iAmPrimary, managerTheardInfo.dbVersion());

            long last = 0;

            // HEARTBEAT — envia a versão atual (NÃO é 1 fixo; usa tInfo.dbVersion())
            while (managerTheardInfo.isRunning()) {
                long now = System.currentTimeMillis();

                if (now - last >= HEARTBEAT_INTERVAL_MS) {
                    String hb = requestKeyValue(
                            "TYPE","HEARTBEAT",
                            "ID", managerTheardInfo.id()
                    );
                    send(s, dirAddr, dirPort, hb);
                    last = now;
                }

                Endpoint cur = tryReceivePrincipal(s);
                if (cur != null) {
                    managerTheardInfo.setPrimary(cur.ip, cur.port);
                    boolean prim = Objects.equals(cur.ip, managerTheardInfo.serverTcpIp()) && cur.port == managerTheardInfo.serverTcpPort();
                    System.out.printf("[DIR] principal %s:%d | souPrimario=%s%n", cur.ip, cur.port, prim);
                }
                Thread.sleep(SLEEP_INTERVAL_MS);
            }

            // DEREGISTER
            String deregMsg = requestKeyValue("TYPE","DEREGISTER","ID", managerTheardInfo.id());
            send(s, dirAddr, dirPort, deregMsg);

        } catch (Exception e) {
            if (managerTheardInfo.isRunning())
                System.err.println("[DIR] erro: " + e.getMessage());
        }finally {
            try {
                close();
            }catch (Exception ignore){}
        }
    }

    /* ---------- auxiliares UDP ---------- */

    //Tipo um DTO
    private record Endpoint(String ip, int port, Integer dbv) { }

    //Para separar o pedido por key e value: Type=LOGIN -> "Type", "LOGIN"
    private static String requestKeyValue(String... keyValue) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keyValue.length; i += 2) b.append(keyValue[i]).append('=').append(keyValue[i + 1]).append('|');
        return b.toString();
    }

    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(data, data.length, addr, port));
    }

    //Espera a confirmação da ligação ao main server
    private Endpoint waitPrincipal(DatagramSocket s) {
        for (int i = 0; i < RETRY_COUNT; i++) {
            Endpoint ep = tryReceivePrincipal(s);
            if (ep != null) return ep;
        }
        return null;
    }

    // Tenta fazer ligação ao server principal
    private Endpoint tryReceivePrincipal(DatagramSocket s) {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            s.receive(dp);
            String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();

            //Se houver mais servidores iguais
            if (resp.startsWith("409 CONFLICT DUP_ENDPOINT")) {
                System.err.println("[DIR] ja existe servidor ativo com este ip:porto. a terminar.");
                System.exit(2); // encerra para não ficar dois no mesmo endpoint
                return null;    // unreachable
            }
            //Depois passar 17segs sem heartbeat(TTL), a diretoria envia ordem para encerrar servidor
            if (resp.startsWith("SHUTDOWN") || resp.startsWith("404 NO_PRINCIPAL")){
                managerTheardInfo.stopRunning(false);
                System.out.println("ENCERREI");
                return null;
            }

            String body = resp.substring("200 OK ".length());
            String[] mainAndRest = body.split("\\|", 2);
            String[] ipPort = mainAndRest[0].split(":");
            if (ipPort.length != 2) return null;

            String ip = ipPort[0];
            int port = Integer.parseInt(ipPort[1]);
            Integer dbv = null;

            return new Endpoint(ip, port, dbv);
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
