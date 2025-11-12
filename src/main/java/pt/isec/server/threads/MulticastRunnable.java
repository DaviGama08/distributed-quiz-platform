package pt.isec.server.threads;

import pt.isec.server.IServerNode;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

public class MulticastRunnable implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;    // para não bloquear o loop
    private static final int BUFFER_SIZE = 4096;     // tamanho do buffer de receção

    private final IServerNode tInfo;
    private MulticastSocket ms;

    public MulticastRunnable(IServerNode tInfo) { this.tInfo = tInfo; }

    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(tInfo.mcPort())) {
            this.ms = _ms;

            // ===== CONFIGURAÇÃO DO SOCKET =====
            _ms.setReuseAddress(true);
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(tInfo.mcIf());
            try {
                // Em muitos Windows, isto precisa estar "false" (desabilitar=FALSE → loopback ATIVADO)
                _ms.setLoopbackMode(false);
            } catch (Throwable ignore) { /* alguns JDKs marcam deprecated mas funciona */ }

            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());
            _ms.joinGroup(new InetSocketAddress(grp, tInfo.mcPort()), tInfo.mcIf());

            long lastSent = 0;

            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // Identificador humano: "servidor<portoCliente>" (ex.: 5002 → servidor5002)
                String serverId = "servidor" + tInfo.clientPort();

                // 1) Envio (só se for primário e deu o intervalo)
                if (tInfo.isPrimary() && now - lastSent >= HEARTBEAT_INTERVAL_MS) {
                    String beat = "MC_HB;id=" + serverId +
                            ";role=" + (tInfo.isPrimary() ? "MASTER" : "BACKUP") +
                            ";version=" + tInfo.dbVersion() +
                            ";dbPort=" + tInfo.dbCopyPort();
                    byte[] data = beat.getBytes(StandardCharsets.UTF_8);
                    _ms.send(new DatagramPacket(data, data.length, grp, tInfo.mcPort()));
                    lastSent = now;
                    // log útil para validação
                    // System.out.println("[MC] sent: " + beat);
                }

                // 2) Receção (se não for primário, tenta ler com timeout curto)
                if (!tInfo.isPrimary()) {
                    try {
                        _ms.receive(pkt);
                        String senderIp = pkt.getAddress().getHostAddress();
                        if (!senderIp.equals(tInfo.ip())) {
                            String s = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                            if (s.startsWith("MC_HB;")) {
                                long rxVersion = extractLong(s, "version");
                                int  rxDbPort  = (int) extractLong(s, "dbPort");

                                boolean needCopyBecauseMissing = !Files.exists(tInfo.dbPath());
                                boolean needCopyBecauseVersion = (rxVersion >= 0 && rxVersion != tInfo.dbVersion());

                                if (needCopyBecauseMissing || needCopyBecauseVersion) {
                                    System.err.printf("[%s][MC] copy-request: missing=%s version(local=%d, rx=%d) → pedir a %s:%d%n",
                                            Instant.now(), needCopyBecauseMissing, tInfo.dbVersion(), rxVersion, senderIp, rxDbPort);

                                    if (rxDbPort > 0) {
                                        new Thread(new DbCopyRequesterRunnable(
                                                tInfo, senderIp, rxDbPort
                                        ), "dbcopy-request").start();
                                    } else {
                                        System.err.println("[MC] hb sem dbPort → não posso pedir cópia.");
                                    }
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

    /** extrai um número longo de um payload tipo "k1=v1;k2=v2;..." (retorna -1 se não achar) */
    private static long extractLong(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) return -1;
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j) : payload.substring(i + needle.length())).trim();
        try { return Long.parseLong(raw); } catch (Exception e) { return -1; }
    }

    @Override public void close() {
        if (ms != null) try { ms.close(); } catch (Exception ignore) {}
    }
}
