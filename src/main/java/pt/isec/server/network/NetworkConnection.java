package pt.isec.server.network;

import pt.isec.common.messages.Message;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public class NetworkConnection {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public NetworkConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public static NetworkConnection connect(String host, int port, Duration timeout) throws IOException{
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), (int)Math.min(Integer.MAX_VALUE, Math.max(0, timeout.toMillis())));
        return new NetworkConnection(s);
    }

    //Explicação das funções Math que usamos na função a seguir:
    //O Math.min(...): garante que nunca ultrapasse o limite dos inteiros.
    //O Math.max(...): garante que seja sempre positivo.
    public void setReadTimeout(Duration timeout) throws IOException {
        socket.setSoTimeout((int)Math.min(Integer.MAX_VALUE, Math.max(0, timeout.toMillis())));
    }

    public <T extends Serializable> void sendMessage(Message<T> message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset();
    }

    public Message<?> receiveMessage() throws IOException, ClassNotFoundException {
        return (Message<?>) in.readObject();
    }

    //Esses dois métodos a seguir terão o principal intuito de copiar os ficheiros .db
    //do servidor master para os servidores de backup.
    public long sendStream(InputStream src) throws IOException {
        try (src) {
                //TODO: definir constante
                byte[] buf = new byte[64 * 1024];
                long tot = 0;
                int r;
                OutputStream raw = socket.getOutputStream();
                while ((r = src.read(buf)) >= 0) { raw.write(buf,0,r); tot += r; }
                raw.flush();
                return tot;
        }
    }
    public long receiveTo(OutputStream dst) throws IOException {
        try (dst) {
            //TODO: definir constante
            byte[] buf = new byte[64 * 1024];
            long tot = 0;
            int r;
            InputStream raw = socket.getInputStream();
            while ((r = raw.read(buf)) >= 0) { dst.write(buf,0,r); tot += r; }
            dst.flush();
            return tot;
        }
    }

    public Socket socket() { return socket; }

    public void close() throws IOException {
        if(out != null) out.close();
        if(in != null) in.close();
        if(socket != null) socket.close();
    }
}
