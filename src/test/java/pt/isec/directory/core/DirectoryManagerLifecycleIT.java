package pt.isec.directory.core;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryManagerLifecycleIT {

    @Test
    void expiresServersUsingConfiguredTtlAndStopsWithoutBlockedThreads() throws Exception {
        int port;
        try (DatagramSocket reservation = new DatagramSocket(0)) {
            port = reservation.getLocalPort();
        }

        DirectoryManager directory = new DirectoryManager(
                port, 32, 4_096, 1, 150, 25, 10_000
        );
        directory.run();

        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2_000);
            String register = exchange(client, port,
                    "TYPE=REGISTER|ID=server-1|TCP=127.0.0.1:5001|DBV=0");
            assertTrue(register.startsWith("200 OK 127.0.0.1:5001"), register);

            String principal = exchange(client, port, "TYPE=LOGIN");
            assertTrue(principal.startsWith("200 PRINCIPAL 127.0.0.1:5001"), principal);

            Thread.sleep(350);
            assertEquals("404 NO_PRINCIPAL", exchange(client, port, "TYPE=LOGIN"));
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(2), directory::stop);
        }
    }

    private static String exchange(DatagramSocket socket, int port, String payload) throws Exception {
        byte[] request = payload.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(
                request,
                request.length,
                InetAddress.getLoopbackAddress(),
                port
        ));

        byte[] response = new byte[4_096];
        DatagramPacket packet = new DatagramPacket(response, response.length);
        socket.receive(packet);
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }
}
