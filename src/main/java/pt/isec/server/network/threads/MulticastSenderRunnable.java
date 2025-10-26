package pt.isec.server.network.threads;

import pt.isec.server.network.IServerNode;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

/** Envia heartbeat multicast a cada 5s quando o nó é primário. */
public class MulticastSenderRunnable implements Runnable {
    private final IServerNode tInfo;

    public MulticastSenderRunnable(IServerNode tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        try (MulticastSocket ms = new MulticastSocket()) {
            ms.setNetworkInterface(tInfo.mcIf());
            ms.setTimeToLive(1);
            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());

            long last = 0;
            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();
                if (now - last >= 5000 && tInfo.isPrimary()) {
                    String beat = "MC_HB;version=" + tInfo.dbVersion() + ";dbPort=" + tInfo.dbCopyPort();
                    byte[] d = beat.getBytes(StandardCharsets.UTF_8);
                    ms.send(new DatagramPacket(d, d.length, grp, tInfo.mcPort()));
                    last = now;
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[MC-TX] erro: " + e.getMessage());
        }
    }
}
