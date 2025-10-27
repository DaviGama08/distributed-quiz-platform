package pt.isec.server.network.threads;

import pt.isec.server.network.IServerNode;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Recebe heartbeats multicast de outros nós e valida a versão da BD.
 * Se não for primário e detectar divergência, apenas avisa (não podemos alterar running).
 */
public class MulticastReceiverRunnable implements Runnable, AutoCloseable {
    private final IServerNode tInfo;
    private MulticastSocket ms;

    private final int BUFFER_SIZE = 4096;

    public MulticastReceiverRunnable(IServerNode tInfo) {this.tInfo = tInfo;}

    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(tInfo.mcPort())) {
            ms = _ms;
            _ms.setNetworkInterface(tInfo.mcIf());
            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());
            _ms.joinGroup(new InetSocketAddress(grp, tInfo.mcPort()), tInfo.mcIf());

            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            while (tInfo.isRunning()) {
                _ms.receive(pkt);
                if (Objects.equals(pkt.getAddress().getHostAddress(), tInfo.ip()))
                    continue;

                String s = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                if (s.startsWith("MC_HB;version=")) {
                    int semi = s.indexOf(';', "MC_HB;version=".length());
                    long v = Long.parseLong(s.substring("MC_HB;version=".length(), semi > 0 ? semi : s.length()).trim());
                    if (!tInfo.isPrimary() && v != tInfo.dbVersion()) {
                        System.err.printf("[MC-RX] versão divergente: local=%d rx=%d — (aviso)\n",
                                tInfo.dbVersion(), v);
                        // Não podemos alterar running porque IServerNode não expõe setter.
                    }
                }
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[MC-RX] erro: " + e.getMessage());
        }
    }

    @Override public void close() {
        if (ms != null) try { ms.close(); } catch (Exception ignore) {}
    }
}
