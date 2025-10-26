package pt.isec.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MainClient {
    /*
      Uso:
        java pt.isec.client.MainClient <dirHost> <dirUdpPort>
    */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java pt.isec.client.MainClient <dirHost> <dirUdpPort>");
            return;
        }
        String dirHost = args[0];
        int dirPort = Integer.parseInt(args[1]);

        try (DatagramSocket udp = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(dirHost);

            // >>> Protocolo correto da diretoria
            String query = "VER=1|TYPE=CLIENT_QUERY";
            byte[] data = query.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, addr, dirPort);
            udp.send(p);

            byte[] buf = new byte[2048];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            udp.setSoTimeout(3000);
            udp.receive(resp);

            String payload = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("Diretoria respondeu: " + payload);

            // Esperado: "200 PRINCIPAL ip:port"  ou  "404 NO_PRINCIPAL"
            if (payload.startsWith("404")) {
                throw new IOException("Diretoria sem principal: " + payload);
            }
            if (!payload.startsWith("200 PRINCIPAL ")) {
                throw new IOException("Resposta inesperada da diretoria: " + payload);
            }

            String ep = payload.substring("200 PRINCIPAL ".length()).trim();
            String[] hp = ep.split(":");
            if (hp.length != 2) throw new IOException("Endpoint inválido: " + ep);

            String host = hp[0];
            int port = Integer.parseInt(hp[1]);

            // ====== TCP com o servidor ======
            try (Socket s = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

                System.out.println("Ligado ao servidor " + host + ":" + port);
                String hello = in.readLine();
                if (hello != null) System.out.println("Servidor disse: " + hello);

                String line;
                System.out.println("Escreve mensagens (ou 'quit' para sair):");
                while ((line = console.readLine()) != null) {
                    out.println(line);
                    String ans = in.readLine();
                    if (ans == null) break;
                    System.out.println(ans);
                    if ("bye".equalsIgnoreCase(ans)) break;
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout à espera de resposta da diretoria.");
        } catch (IOException e) {
            System.err.println("Erro no cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
