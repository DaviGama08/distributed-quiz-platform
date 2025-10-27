package pt.isec.server.network.threads;

import pt.isec.server.network.IServerNode;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * gere o envio e receção de mensagens udp entre o servidor e o serviço de diretoria
 * envia "register" ao iniciar, envia "heartbeat" a cada 5s, e "deregister" ao terminar
 * recebe "200 principal ip:port" para saber quem é o servidor principal
 */
public class DirectoryHeartbeatRunnable implements Runnable, AutoCloseable {
    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int RETRY_COUNT = 3;
    private static final int SLEEP_INTERVAL_MS = 50;
    private static final int BUFFER_SIZE = 512;

    private final IServerNode tInfo; // referência ao servidor local (informações e estado)
    private DatagramSocket socket;   // socket udp usado para comunicação

    public DirectoryHeartbeatRunnable(IServerNode tInfo) {this.tInfo = tInfo;}

    @Override
    public void run() {
        try (DatagramSocket s = new DatagramSocket()) {
            socket = s;
            s.setSoTimeout(SOCKET_TIMEOUT_MS);

            InetAddress dirAddr = InetAddress.getByName(tInfo.directoryHost());
            int dirPort = tInfo.directoryPort();

            // enviar pedido de registo à diretoria
            String registerMsg = kv(
                    "VER", "1",
                    "TYPE", "REGISTER",
                    "ID", tInfo.id(),
                    "TCP", tInfo.ip() + ":" + tInfo.clientPort(),
                    "DBV", String.valueOf(tInfo.dbVersion()),
                    "DBP", String.valueOf(tInfo.dbCopyPort())
            );
            send(s, dirAddr, dirPort, registerMsg);

            // aguardar resposta com o principal
            Endpoint principal = waitPrincipal(s);
            if (principal == null) {
                System.err.println("[DIR] sem resposta da diretoria; thread terminada");
                return;
            }

            // atualiza a informação sobre o servidor principal
            tInfo.setPrimary(principal.ip, principal.port);
            boolean iAmPrimary = Objects.equals(principal.ip, tInfo.ip()) && principal.port == tInfo.clientPort();
            System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", principal.ip, principal.port, iAmPrimary);

            long last = 0;

            // loop principal enquanto o servidor estiver a correr
            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // envia heartbeat a cada 5 segundos
                if (now - last >= HEARTBEAT_INTERVAL_MS) {
                    String hb = kv(
                            "VER", "1",
                            "TYPE", "HEARTBEAT",
                            "ID", tInfo.id(),
                            "DBV", String.valueOf(tInfo.dbVersion()),
                            "DBP", String.valueOf(tInfo.dbCopyPort())
                    );
                    send(s, dirAddr, dirPort, hb);
                    last = now;
                }

                // tenta receber nova informação sobre o principal
                Endpoint update = tryReceivePrincipal(s);
                if (update != null) {
                    tInfo.setPrimary(update.ip, update.port);
                    boolean iAmPrim = Objects.equals(update.ip, tInfo.ip()) && update.port == tInfo.clientPort();
                    System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", update.ip, update.port, iAmPrim);
                }

                Thread.sleep(SLEEP_INTERVAL_MS);
            }

            // ao terminar, envia pedido de remoção
            String deregMsg = kv("VER", "1", "TYPE", "DEREGISTER", "ID", tInfo.id());
            send(s, dirAddr, dirPort, deregMsg);

        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[DIR] erro: " + e.getMessage());
        }
    }

    // classe simples que guarda ip e porto do servidor principal
    private record Endpoint(String ip, int port) {}

    // função utilitária que constrói mensagens tipo "CHAVE=VALOR|CHAVE=VALOR|..."
    private static String kv(String... kv) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2)
            //Ao encontrar o "=" insere os próximos caracteres e o "|" no fim.
            b.append(kv[i]).append('=').append(kv[i + 1]).append('|');
        return b.toString();
    }

    // envia uma mensagem udp
    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(data, data.length, addr, port));
    }

    // tenta receber resposta "200 principal ip:port" algumas vezes
    private Endpoint waitPrincipal(DatagramSocket s) {
        for (int i = 0; i < RETRY_COUNT; i++) {
            Endpoint ep = tryReceivePrincipal(s);
            if (ep != null)
                return ep;
        }
        return null;
    }

    // tenta ler um pacote udp e verificar se é uma resposta com o principal
    private Endpoint tryReceivePrincipal(DatagramSocket s) {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            s.receive(dp); // pode lançar SocketTimeoutException se nada vier
            String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();

            if (!resp.startsWith("200 PRINCIPAL "))
                return null;

            String[] parts = resp.substring("200 PRINCIPAL ".length()).split(":");
            if (parts.length != 2)
                return null;

            return new Endpoint(parts[0], Integer.parseInt(parts[1]));
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // fecha o socket udp quando for necessário
    @Override
    public void close() {
        if (socket != null && !socket.isClosed())
            socket.close();
    }
}
