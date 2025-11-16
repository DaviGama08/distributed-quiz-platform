package pt.isec.server.threads;

import pt.isec.server.IQuizServer;
import pt.isec.server.QuizServer;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

public class ClusterHeartbeatThread implements Runnable, AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;
    private static final int RX_TIMEOUT_MS = 500;
    private static final int BUFFER_SIZE = 4096;

    private final IQuizServer tInfo;
    private MulticastSocket ms;

    public ClusterHeartbeatThread(IQuizServer tInfo) { this.tInfo = tInfo; }

    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(tInfo.mcPort())) {
            this.ms = _ms;

            _ms.setReuseAddress(true);
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(tInfo.mcIf());
            try { _ms.setLoopbackMode(false); } catch (Throwable ignore) {}

            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());
            _ms.joinGroup(new InetSocketAddress(grp, tInfo.mcPort()), tInfo.mcIf());

            long lastSent = 0;

            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // 1) envio (só master)
                if (tInfo.isPrimary() && now - lastSent >= HEARTBEAT_INTERVAL_MS) {
                    String beat = "MC_HB;"
                            + "id=servidor" + tInfo.clientPort()
                            + ";role=" + (tInfo.isPrimary() ? "MASTER" : "BACKUP")
                            + ";version=" + tInfo.dbVersion()
                            + ";dbPort=" + tInfo.dbCopyPort()
                            + ";clientPort=" + tInfo.clientPort();
                    byte[] data = beat.getBytes(StandardCharsets.UTF_8);
                    _ms.send(new DatagramPacket(data, data.length, grp, tInfo.mcPort()));
                    lastSent = now;
                }

                // 2) receção (backups)
                if (!tInfo.isPrimary()) {
                    try {
                        _ms.receive(pkt);
                        String senderIp = pkt.getAddress().getHostAddress();
                        String s = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                        if (s.startsWith("MC_HB;")) {
                            long rxClientPort = extractLong(s, "clientPort");
                            boolean fromMe = senderIp.equals(tInfo.ip()) && rxClientPort == tInfo.clientPort();
                            if (fromMe) continue;

                            long rxVersion = extractLong(s, "version");
                            int  rxDbPort  = (int) extractLong(s, "dbPort");

                            boolean needCopyBecauseMissing = !Files.exists(tInfo.dbPath());
                            boolean needCopyBecauseVersion = (rxVersion >= 0 && rxVersion != tInfo.dbVersion());

                            if (needCopyBecauseMissing || needCopyBecauseVersion) {
                                System.err.printf("[%s][MC] pedir cópia: falta=%s versao(local=%d, rx=%d) → %s:%d%n",
                                        Instant.now(), needCopyBecauseMissing, tInfo.dbVersion(), rxVersion, senderIp, rxDbPort);

                                if (rxDbPort > 0) {
                                    // NOVO: usa o “fusível” do ServerNode para evitar múltiplas cópias
                                    if (tInfo instanceof QuizServer sn) {
                                        if (!sn.tryLockCopy()) {
                                            continue; // já há uma cópia em curso
                                        }
                                        new Thread(() -> {
                                            try { new DbCopyRequesterRunnable(tInfo, senderIp, rxDbPort).run(); }
                                            finally { sn.unlockCopy(); }
                                        }, "dbcopy-request").start();
                                    } else {
                                        new Thread(new DbCopyRequesterRunnable(tInfo, senderIp, rxDbPort), "dbcopy-request").start();
                                    }
                                } else {
                                    System.err.println("[MC] heartbeat sem dbPort → não posso pedir cópia.");
                                }
                            }
                        }
                    } catch (SocketTimeoutException ignore) {
                        // sem pacote
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
