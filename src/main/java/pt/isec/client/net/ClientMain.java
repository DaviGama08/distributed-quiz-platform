package pt.isec.client.net;

// DaytimeClient — Slides 37–38, PD-3
// Preparar streams em Socket — Slide 34, PD-3

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class ClientMain {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 59000;

    public static void main(String[] args) {
        String host = (args != null && args.length > 0) ? args[0] : DEFAULT_HOST;
        int port = (args != null && args.length > 1) ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintStream out = new PrintStream(socket.getOutputStream(), true);
             BufferedReader kb = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.printf("Connected to %s:%d%n", host, port);
            System.out.println("Type commands (e.g., PING, QUIT).");

            String line;
            while ((line = kb.readLine()) != null) {
                out.println(line);
                String response = in.readLine();
                if (response == null) {
                    System.out.println("Server closed connection.");
                    break;
                }
                System.out.println("<< " + response);
                if ("BYE".equalsIgnoreCase(response)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
