package pt.isec.server.network.threads;

import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.Message;
import pt.isec.server.network.IServerNode;
import pt.isec.server.network.NetworkConnection;
import pt.isec.server.services.auth.AuthService;
import java.io.IOException;

public class ClientHandlerRunnable implements Runnable{
    private final IServerNode tInfo;
    private NetworkConnection connection;
    private AuthService authService;

    public ClientHandlerRunnable(IServerNode tInfo, NetworkConnection connection) {this.tInfo = tInfo; this.connection = connection;}

    @Override
    public void run() {
        try{
            Message<?> message;
            while((message = connection.receiveMessage()) != null) {
                Message<?> response = processMessage(message);
                connection.sendMessage(response);
            }
        } catch (Exception e){
            System.out.println("Client handler error: " + e.getMessage());
        } finally {
            try {
                if(connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }

    private Message<?> processMessage(Message<?> message) {
        return switch (message.getType()) {
            /*case REGISTER -> authService.handleRegister(message);
            case LOGIN -> authService.handleLogin(message);
            case ....continuar*/
            default -> new Message<>(MessageType.MESSAGE, "Unknown message type");
        };
    }
}
