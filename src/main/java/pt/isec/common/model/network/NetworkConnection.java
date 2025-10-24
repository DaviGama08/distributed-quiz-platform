package pt.isec.common.model.network;

import pt.isec.common.messages.Message;
import java.io.*;
import java.net.Socket;

public class NetworkConnection {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public NetworkConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public <T extends Serializable> void sendMessage(Message<T> message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    public Message<?> receiveMessage() throws IOException, ClassNotFoundException {
        return (Message<?>) in.readObject();
    }

    public void close() throws IOException {
        if(out != null) out.close();
        if(in != null) in.close();
        if(socket != null) socket.close();
    }
}
