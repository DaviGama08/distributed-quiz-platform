package pt.isec.directory.threads;

import pt.isec.directory.IDirectoryManager;
import pt.isec.directory.ServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ReaperThread implements Runnable{
    private final IDirectoryManager tInfo;
    private final long periodMs;

    public ReaperThread(IDirectoryManager tInfo, long periodMs) {
        this.tInfo = tInfo;
        this.periodMs = periodMs;
    }

    @Override
    public void run() {
        try {
            while (tInfo.isRunning()){
                long now = System.currentTimeMillis();

                tInfo.removeServersFromList(now);

                Thread.sleep(periodMs);
            }
        } catch (InterruptedException ie) {
            // não vamos usar interrupt para shutdown, mas se acontecer,
            // apenas saímos do loop
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            System.err.println("[Diretoria][Reaper] erro inesperado: " + t.getMessage());
        } finally {
            // === Diretoria a encerrar: enviar SHUTDOWN a todos os servidores e fechar o socket ===
            DatagramSocket socket = tInfo.socket();
            if (socket != null && !socket.isClosed()) {
                for (ServerInfo s : new ArrayList<>(tInfo.servers().values())) {
                    try {
                        String text = "SHUTDOWN";
                        byte[] out  = text.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket dp = new DatagramPacket(
                                out,
                                out.length,
                                InetAddress.getByName(s.getIp()),
                                s.getUdpPort()
                        );
                        socket.send(dp);
                    } catch (IOException e) {
                        System.err.println("[Directory] Falha a enviar SHUTDOWN para "
                                + s.getIp() + ":" + s.getUdpPort() + " – " + e.getMessage());
                    }
                }

                // fechar o socket aqui desbloqueia o UdpListenerThread (receive -> SocketException)
                socket.close();
            }

            System.out.println("Reaper terminou.");
        }
    }
}
