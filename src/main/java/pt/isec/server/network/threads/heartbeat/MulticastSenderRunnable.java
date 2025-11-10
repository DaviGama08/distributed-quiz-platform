package pt.isec.server.network.threads.heartbeat;

import pt.isec.server.network.IServerNode;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

/** envia heartbeat multicast a cada intervalo fixo (quando o nó é primário) */
public class MulticastSenderRunnable implements Runnable {
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int LOOP_SLEEP_MS = 50;
    private static final int MULTICAST_TTL = 1;

    private final IServerNode tInfo;

    public MulticastSenderRunnable(IServerNode tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        try (MulticastSocket ms = new MulticastSocket()) {
            // define qual interface de rede vai ser usada para enviar multicast
            ms.setNetworkInterface(tInfo.mcIf());
            // define o alcance máximo dos pacotes multicast (1 = apenas rede local)
            ms.setTimeToLive(MULTICAST_TTL);

            // obtém o endereço de grupo multicast
            InetAddress grp = InetAddress.getByName(tInfo.mcGroup());

            long lastSent = 0;

            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // envia heartbeat a cada intervalo definido e apenas se for o servidor primário
                if (now - lastSent >= HEARTBEAT_INTERVAL_MS && tInfo.isPrimary()) {
                    String beat = "MC_HB;version=" + tInfo.dbVersion() + ";dbPort=" + tInfo.dbCopyPort();
                    byte[] data = beat.getBytes(StandardCharsets.UTF_8);

                    ms.send(new DatagramPacket(data, data.length, grp, tInfo.mcPort()));

                    lastSent = now;
                }

                // pequena pausa para evitar uso excessivo de CPU
                Thread.sleep(LOOP_SLEEP_MS);
            }

        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[MC-TX] erro: " + e.getMessage());
        }
    }
}
