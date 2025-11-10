package pt.isec.server.network;

import pt.isec.common.messages.Message;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * classe que gere a comunicação tcp (envio/receção de mensagens e streams)
 */
public class NetworkConnection implements AutoCloseable {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_INT_TIMEOUT = Integer.MAX_VALUE;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public NetworkConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    // cria uma nova ligação tcp para um determinado host/porto, com timeout configurável
    public static NetworkConnection connect(String host, int port, Duration timeout) throws IOException {
        Socket s = new Socket();
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        s.connect(new InetSocketAddress(host, port), to);
        return new NetworkConnection(s);
    }

    // define o tempo máximo de espera por leitura
    // math.min evita overflow (valores acima do valor max. dos inteiros)
    // math.max evita valores negativos
    public void setReadTimeout(Duration timeout) throws IOException {
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        socket.setSoTimeout(to);
    }

    // envia um objeto serializável (mensagem genérica)
    public <T extends Serializable> void sendMessage(Message<T> message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset();
    }

    // recebe um objeto (mensagem)
    public Message<?> receiveMessage() throws IOException, ClassNotFoundException {
        return (Message<?>) in.readObject();
    }
    //TODO: ver para que serve sendStream()
    public long sendStream(InputStream src) throws IOException {
        try (src) {
            byte[] buf = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            OutputStream raw = socket.getOutputStream();

            while ((read = src.read(buf)) >= 0) {
                raw.write(buf, 0, read);
                total += read;
            }
            raw.flush();
            return total; //n.º de bytes enviados
        }
    }

    //TODO: ver para que serve isto receiveTo()
    public long receiveTo(OutputStream dst) throws IOException {
        try (dst) {
            byte[] buf = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            InputStream raw = socket.getInputStream();

            while ((read = raw.read(buf)) >= 0) {
                dst.write(buf, 0, read);
                total += read;
            }
            dst.flush();
            return total;
        }
    }

    public Socket socket() {
        return socket;
    }

    public void close() throws IOException {
        if (out != null) out.close();
        if (in != null) in.close();
        if (socket != null) socket.close();
    }
}
