package pt.isec.server.threads;

import pt.isec.common.messages.TcpMessage;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Classe responsável por fazer a ligação TCP entre servidor e cliente.
 * Usa ObjectInputStream/ObjectOutputStream para envio de TcpMessage e
 * também permite enviar/receber dados binários pelo MESMO stream.
 */
public class NetworkTcpConnection implements AutoCloseable {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_INT_TIMEOUT = Integer.MAX_VALUE;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public NetworkTcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public static NetworkTcpConnection connect(String host, int port, Duration timeout) throws IOException {
        Socket s = new Socket();
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        s.connect(new InetSocketAddress(host, port), to);
        return new NetworkTcpConnection(s);
    }

    public void setReadTimeout(Duration timeout) throws IOException {
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        socket.setSoTimeout(to);
    }

    public <T extends Serializable> void sendMessage(TcpMessage<T> tcpMessage) throws IOException {
        out.writeObject(tcpMessage);
        out.flush();
        out.reset();
    }

    public TcpMessage<?> receiveMessage() throws IOException, ClassNotFoundException {
        return (TcpMessage<?>) in.readObject();
    }

    /* ===== tipos primitivos e fluxo binário pelo MESMO ObjectStream ===== */

    public void writeLong(long v) throws IOException {
        out.writeLong(v);
        out.flush();
        out.reset();
    }

    public long readLong() throws IOException {
        return in.readLong();
    }

    /**
     * Envia exatamente 'size' bytes usando o MESMO ObjectOutputStream.
     */
    public long sendStreamViaObjectOut(InputStream src, long size) throws IOException {
        try (src) {
            byte[] buf = new byte[BUFFER_SIZE];
            long sent = 0;
            int read;
            while (sent < size &&
                    (read = src.read(buf, 0, (int) Math.min(buf.length, size - sent))) >= 0) {
                out.write(buf, 0, read);
                sent += read;
            }
            out.flush();
            out.reset();
            return sent;
        }
    }

    /**
     * Lê exatamente 'size' bytes usando o MESMO ObjectInputStream.
     */
    public long receiveExactly(OutputStream dst, long size) throws IOException {
        try (dst) {
            byte[] buf = new byte[BUFFER_SIZE];
            long got = 0;
            while (got < size) {
                int want = (int) Math.min(buf.length, size - got);
                int read = in.read(buf, 0, want);
                if (read < 0)
                    throw new EOFException("terminou antes de receber todos os bytes");
                dst.write(buf, 0, read);
                got += read;
            }
            dst.flush();
            return got;
        }
    }

    /* ===== métodos antigos (mantidos para compatibilidade) ===== */

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
            return total;
        }
    }

    public long receiveStreamAfterAck(OutputStream dst) throws IOException {
        try (dst) {
            byte[] buf = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            InputStream raw = this.in; // usa o ObjectInputStream como InputStream
            while ((read = raw.read(buf)) >= 0) {
                dst.write(buf, 0, read);
                total += read;
            }
            dst.flush();
            return total;
        }
    }

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

    @Override
    public void close() throws IOException {
        try { out.close(); } catch (Exception ignore) {}
        try { in.close(); }  catch (Exception ignore) {}
        try { socket.close(); } catch (Exception ignore) {}
    }
}
