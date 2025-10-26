package pt.isec.server.network.threads;

import pt.isec.server.network.IServerNode;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * UDP: REGISTER + HEARTBEAT para a diretoria.
 * Espera resposta "200 PRINCIPAL ip:port" e chama tInfo.setPrimary(ip, port).
 */
public class DirectoryHeartbeatRunnable implements Runnable, AutoCloseable {
    private final IServerNode tInfo;
    private DatagramSocket sock;

    public DirectoryHeartbeatRunnable(IServerNode tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        try (DatagramSocket s = new DatagramSocket()) {
            sock = s;
            s.setSoTimeout(3000);
            InetAddress dirAddr = InetAddress.getByName(tInfo.directoryHost());

            String reg = kv("VER","1","TYPE","REGISTER",
                    "ID",tInfo.id(),
                    "TCP",tInfo.ip()+":"+tInfo.clientPort(),
                    "DBV",String.valueOf(tInfo.dbVersion()),
                    "DBP",String.valueOf(tInfo.dbCopyPort()));
            send(s, dirAddr, tInfo.directoryPort(), reg);

            var p0 = waitPrincipal(s);
            if (p0 == null) {
                System.err.println("[DIR] no response from directory; stopping thread");
                return; // sem mexer em running (IServerNode não expõe setter)
            }
            tInfo.setPrimary(p0.ip, p0.port);
            boolean iAmPrimary = Objects.equals(p0.ip, tInfo.ip()) && p0.port == tInfo.clientPort();
            System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", p0.ip, p0.port, iAmPrimary);

            long last = 0;
            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();
                if (now - last >= 5000) {
                    String hb = kv("VER","1","TYPE","HEARTBEAT",
                            "ID",tInfo.id(),
                            "DBV",String.valueOf(tInfo.dbVersion()),
                            "DBP",String.valueOf(tInfo.dbCopyPort()));
                    send(s, dirAddr, tInfo.directoryPort(), hb);
                    last = now;
                }
                var up = tryReceivePrincipal(s);
                if (up != null) {
                    tInfo.setPrimary(up.ip, up.port);
                    boolean iAmPrim = Objects.equals(up.ip, tInfo.ip()) && up.port == tInfo.clientPort();
                    System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", up.ip, up.port, iAmPrim);
                }
                Thread.sleep(50);
            }

            String unreg = kv("VER","1","TYPE","DEREGISTER","ID",tInfo.id());
            send(s, dirAddr, tInfo.directoryPort(), unreg);

        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[DIR] erro: " + e.getMessage());
        }
    }

    private record P(String ip, int port) {}
    private static String kv(String... kv){
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) b.append(kv[i]).append('=').append(kv[i+1]).append('|');
        return b.toString();
    }
    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] d = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(d, d.length, addr, port));
    }
    private P waitPrincipal(DatagramSocket s){
        for (int i = 0; i < 3; i++) { var p = tryReceivePrincipal(s); if (p != null) return p; }
        return null;
    }
    private P tryReceivePrincipal(DatagramSocket s) {
        try {
            byte[] buf = new byte[512];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            s.receive(dp);
            String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
            if (resp.startsWith("200 PRINCIPAL ")) {
                String ep = resp.substring("200 PRINCIPAL ".length()).trim();
                String[] hp = ep.split(":");
                return new P(hp[0], Integer.parseInt(hp[1]));
            }
            return null;
        } catch (SocketTimeoutException ignore) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override public void close() { if (sock != null) sock.close(); }
}
