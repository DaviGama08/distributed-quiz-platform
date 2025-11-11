package pt.isec.server.threads.heartbeat;

import pt.isec.server.network.IServerNode;
import pt.isec.server.threads.db.DbCopyRequesterRunnable;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class MulticastRunnable implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;    // para não bloquear o loop
    private static final int BUFFER_SIZE = 4096;     // tamanho do buffer de receção (ANTES estava "mágico" no código)

    private final IServerNode tInfo;
    private MulticastSocket ms;

    public MulticastRunnable(IServerNode tInfo) { this.tInfo = tInfo; }

    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(tInfo.mcPort())) {
            this.ms = _ms;
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(tInfo.mcIf());

            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());
            _ms.joinGroup(new InetSocketAddress(grp, tInfo.mcPort()), tInfo.mcIf());

            long lastSent = 0;

            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // 1) Envio (só se for primário e deu o intervalo)
                if (tInfo.isPrimary() && now - lastSent >= HEARTBEAT_INTERVAL_MS) {
                    String beat = "MC_HB;version=" + tInfo.dbVersion() + ";dbPort=" + tInfo.dbCopyPort();
                    byte[] data = beat.getBytes(StandardCharsets.UTF_8);
                    _ms.send(new DatagramPacket(data, data.length, grp, tInfo.mcPort()));
                    lastSent = now;
                }

                // 2) Receção (se não for primário, tenta ler com timeout curto)
                if (!tInfo.isPrimary()) {
                    try {
                        _ms.receive(pkt);
                        String senderIp = pkt.getAddress().getHostAddress();
                        if (!senderIp.equals(tInfo.ip())) {
                            String s = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                            if (s.startsWith("MC_HB;version=")) {
                                int semi = s.indexOf(';', "MC_HB;version=".length());
                                long v = Long.parseLong(s.substring("MC_HB;version=".length(), semi > 0 ? semi : s.length()).trim());

                                if (v != tInfo.dbVersion()) {
                                    System.err.printf("[%s][MC] versão divergente: local=%d rx=%d — pedindo cópia a %s:%d%n",
                                            Instant.now(), tInfo.dbVersion(), v, senderIp, tInfo.dbCopyPort());
                                    new Thread(new DbCopyRequesterRunnable(
                                            tInfo, senderIp, tInfo.dbCopyPort()
                                    ), "dbcopy-request").start();
                                }
                            }
                        }
                    } catch (SocketTimeoutException ignore) {
                        // sem pacote → segue o loop
                    } catch (Exception e) {
                        if (tInfo.isRunning())
                            System.err.println("[MC-LOOP] erro rx: " + e.getMessage());
                    }
                }

                Thread.sleep(LOOP_SLEEP_MS);
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[MC-LOOP] erro: " + e.getMessage());
        }
    }

    @Override public void close() {
        if (ms != null) try { ms.close(); } catch (Exception ignore) {}
    }
}
