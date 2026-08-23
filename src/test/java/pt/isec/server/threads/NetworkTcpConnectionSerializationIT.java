package pt.isec.server.threads;

import org.junit.jupiter.api.Test;
import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;

import java.io.File;
import java.io.InvalidClassException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkTcpConnectionSerializationIT {
    @Test
    void allowsProjectDtoAndRejectsUnexpectedSerializableClass() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket listener = new ServerSocket(0, 1, loopback);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accepted = executor.submit(() -> new NetworkTcpConnection(listener.accept()));
            try (NetworkTcpConnection client = NetworkTcpConnection.connect(
                    loopback.getHostAddress(), listener.getLocalPort(), Duration.ofSeconds(2));
                 NetworkTcpConnection server = accepted.get(2, TimeUnit.SECONDS)) {
                client.sendMessage(new TcpMessage<>(MessageType.LOGIN,
                        new LoginRequestDTO("student@example.test", "password")));
                assertEquals(MessageType.LOGIN, server.receiveMessage().getType());

                client.sendMessage(new TcpMessage<>(MessageType.LOGIN, new File("unexpected")));
                assertThrows(InvalidClassException.class, server::receiveMessage);
            }
        }
    }
}
