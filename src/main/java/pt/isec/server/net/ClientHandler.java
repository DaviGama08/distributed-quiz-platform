package pt.isec.server.net;

// RunnableThreadDemo — Slides 5–6, PD-4
// Preparar streams em Socket — Slide 34, PD-3

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket client;

    public ClientHandler(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try (Socket s = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintStream out = new PrintStream(s.getOutputStream(), true)) {

            out.println("OK READY");

            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.trim();

                if (cmd.equalsIgnoreCase("PING")) {
                    out.println("PONG");
                } else if (cmd.equalsIgnoreCase("QUIT") || cmd.equalsIgnoreCase("EXIT")) {
                    out.println("BYE");
                    break;
                } else {
                    out.println("ERR UNKNOWN_CMD");
                }
            }
        } catch (IOException e) {
            System.err.printf("ClientHandler error (%s:%d): %s%n",
                    client.getInetAddress().getHostAddress(),
                    client.getPort(),
                    e.getMessage());
        }
    }
}
