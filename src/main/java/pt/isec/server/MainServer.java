package pt.isec;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainServer {
    /*
      Uso:
        java pt.isec.server.pt.isec.MainServer <dirHost> <dirUdpPort> [tcpPort]
      Ex.:
        java pt.isec.server.pt.isec.MainServer localhost 9999 0
      Nota: tcpPort=0 escolhe porto livre automaticamente.
    */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java pt.isec.server.pt.isec.MainServer <dirHost> <dirUdpPort> [tcpPort]");
            return;
        }
        String dirHost = args[0];
        int dirPort = Integer.parseInt(args[1]);
        int tcpPort = (args.length >= 3) ? Integer.parseInt(args[2]) : 0;

        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            int boundTcpPort = serverSocket.getLocalPort();
            String serverId = UUID.randomUUID().toString();
            String serverIp = getLocalIp(); // ip local para divulgar

            System.out.printf("Servidor TCP ativo em %s:%d (id=%s)%n", serverIp, boundTcpPort, serverId);

            // ====== REGISTO NA DIRETORIA ======
            InetAddress dirAddr = InetAddress.getByName(dirHost);
            try (DatagramSocket udp = new DatagramSocket()) {
                String registerMsg = "type=REGISTER|id=" + serverId + "|ip=" + serverIp + "|tcp=" + boundTcpPort;
                sendUdp(udp, dirAddr, dirPort, registerMsg);

                // Heartbeats periódicos (5s)
                Timer t = new Timer("hb", true);
                t.scheduleAtFixedRate(new TimerTask() {
                    @Override public void run() {
                        String hb = "type=HEARTBEAT|id=" + serverId + "|ip=" + serverIp + "|tcp=" + boundTcpPort;
                        try { sendUdp(udp, dirAddr, dirPort, hb); } catch (Exception ignored) {}
                    }
                }, 5000, 5000);

                // Fecho limpo: desregistar
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { sendUdp(udp, dirAddr, dirPort, "type=DEREGISTER|id=" + serverId); } catch (Exception ignored) {}
                }));

                // ====== ATENDER CLIENTES (echo) ======
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket s) {
        try (s;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("HELLO_FROM_SERVER");
            String line;
            while ((line = in.readLine()) != null) {
                if ("quit".equalsIgnoreCase(line)) { out.println("bye"); break; }
                out.println("echo: " + line);
            }
        } catch (IOException ignored) {}
    }

    private static String getLocalIp() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static void sendUdp(DatagramSocket s, InetAddress addr, int port, String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, addr, port);
        s.send(p);
    }
}
