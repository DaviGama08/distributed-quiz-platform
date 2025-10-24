package pt.isec.server.network;

import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.Message;
import pt.isec.common.model.network.NetworkConnection;
import pt.isec.server.services.auth.AuthService;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler extends Thread{

    private NetworkConnection connection;
    private AuthService authService;

    public ClientHandler(Socket clientSocket) throws IOException {
        this.connection = new NetworkConnection(clientSocket);
        this.authService = new AuthService(); //falta implementar
    }

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
        return switch (message.getMsgType()) {
            /*case REGISTER -> authService.handleRegister(message);
            case LOGIN -> authService.handleLogin(message);
            case ....continuar*/
            default -> new Message<>(MessageType.MESSAGE, "Unknown message type");
        };
    }
}
