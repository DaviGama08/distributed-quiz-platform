package pt.isec.server.network.threads;

import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.Message;
import pt.isec.server.network.IServerNode;
import pt.isec.server.network.NetworkConnection;
import pt.isec.server.services.auth.AuthService;
import java.io.IOException;
import java.time.Duration;

public class ClientHandlerRunnable implements Runnable{
    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;
    private static final Duration NO_TIMEOUT = Duration.ZERO;

    private final IServerNode tInfo;
    private NetworkConnection connection;
    private AuthService authService;

    public ClientHandlerRunnable(IServerNode tInfo, NetworkConnection connection) {this.tInfo = tInfo; this.connection = connection;}

    @Override
    public void run() {
        try {
            // o cliente deve enviar a 1ª mensagem em até 30 segundos
            connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

            Message<?> first = connection.receiveMessage();

            // se o servidor não for primário, rejeita o cliente
            if (!tInfo.isPrimary()) {
                connection.sendMessage(new Message<>(MessageType.NACK, "not-primary"));
                return;
            }

            // caso contrário, confirma ligação
            connection.sendMessage(new Message<>(MessageType.ACK, "ok"));

            // remove timeout depois da primeira interação
            connection.setReadTimeout(NO_TIMEOUT);

            // loop principal da sessão do cliente
            while (tInfo.isRunning()) {
                Message<?> msg = connection.receiveMessage();
                if (msg == null)
                    break;

                processMessage(msg);

                // TODO: processar comandos reais vindos do cliente
                connection.sendMessage(new Message<>(MessageType.PONG, "ok"));
            }

        } catch (Exception ignore) {
            // falha silenciosa — o cliente pode ter fechado a ligação
        } finally {
            // garante que o socket é fechado corretamente
            try {
                if (connection != null) connection.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Message<?> processMessage(Message<?> message) {
        //TODO: falta implementar o resto das mensagens
        return switch (message.getType()) {
            //case REGISTER -> authService.handleRegister(message);
            //case LOGIN -> authService.handleLogin(message);
            default -> new Message<>(MessageType.MESSAGE, "Unknown message type");
        };
    }
}
