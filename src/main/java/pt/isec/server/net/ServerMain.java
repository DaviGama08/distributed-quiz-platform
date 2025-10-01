package pt.isec.server.net;

// DaytimeServer — Slides 39–40, PD-3

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    private static final int DEFAULT_PORT = 59000;

    public static void main(String[] args) {
        int port = (args != null && args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.printf("Server listening on port %d%n", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.printf("Accepted connection from %s:%d%n",
                        clientSocket.getInetAddress().getHostAddress(),
                        clientSocket.getPort());

                Thread t = new Thread(new ClientHandler(clientSocket)); // RunnableThreadDemo — Slides 5–6, PD-4
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
