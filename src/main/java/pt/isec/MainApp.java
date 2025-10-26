package pt.isec;

import pt.isec.server.network.threads.ClientHandlerRunnable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MainApp {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Servidor iniciado na porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                ClientHandlerRunnable handler = new ClientHandlerRunnable(clientSocket);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }
}
