package pt.isec.client.services;

import javafx.beans.property.Property;
import pt.isec.client.ClientManager;
import pt.isec.client.threads.ClientListenerThread;
import pt.isec.client.threads.RequestSenderThread;
import pt.isec.common.messages.Message;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Serviço central de gestão de rede: discovery, conexão TCP,
 * filas de envio/recepção e propriedades observáveis.
 */
public class ClientService implements IClientService {
    public static final String PROP_AUTHENTICATED     = "authenticated";
    public static final String PROP_NOTIFICATION      = "notification";
    public static final String PROP_USER_TYPE         = "userType";
    public static final String PROP_USER_EMAIL        = "userEmail";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private volatile boolean authenticated = false;
    private String userType;
    private String userEmail;
    private Integer userId;
    private final int directoryUdpPort;
    private final String directoryHost;
    private int serverTcpPort;
    private String serverTcpHost;
    private static final int DATAGRAM_PACKET_SIZE  = 1024;
    private static final int DISCOVERY_TIMEOUT_MS  = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private Thread tListener, tSender;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private final BlockingQueue<Message<? extends Serializable>> requestQueue  = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message<? extends Serializable>> responseQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 2;
    private final ClientManager manager;

    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
    }

    public void addPropertyChangeListener(PropertyChangeListener l){
        pcs.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String prop, PropertyChangeListener l){
        pcs.addPropertyChangeListener(prop, l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l){
        pcs.removePropertyChangeListener(l);
    }

    /** Define o ID do utilizador autenticado. */
    public void setUserId(Integer id) { this.userId = id; }
    /** Obtém o ID do utilizador autenticado. */
    public Integer getUserId() { return userId; }

    public void setAuthenticated(boolean auth){
        boolean old = this.authenticated;
        this.authenticated = auth;
        pcs.firePropertyChange(PROP_AUTHENTICATED, old, auth);
    }

    public void logout() {
        setAuthenticated(false);
        setUserType(null);
        setUserEmail(null);
        setUserId(null);
    }

    public void setUserType(String t){
        String old = this.userType;
        this.userType = t;
        pcs.firePropertyChange(PROP_USER_TYPE, old, t);
    }

    public void setUserEmail(String e){
        String old = this.userEmail;
        this.userEmail = e;
        pcs.firePropertyChange(PROP_USER_EMAIL, old, e);
    }

    public void pushNotification(String text){
        pcs.firePropertyChange(PROP_NOTIFICATION, null, text);
    }

    public void sendMessage(Message<? extends Serializable> message) {
        try {
            requestQueue.put(message);
        } catch (InterruptedException e) {
            System.err.println("[ClientService] Failed to queue message: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public Message<? extends Serializable> waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    public Message<? extends Serializable> waitForResponse(long timeoutMs) throws InterruptedException {
        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void run(){
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "CONNECTING");
        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++) discovered = discoverServer();
        if (!discovered){
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
            return;
        }
        if (!connectToServer()){
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
            return;
        }
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "CONNECTED");
        running = true;
        startThreads();
    }

    public void stop(){
        running = false;
        if (tListener != null) tListener.interrupt();
        if (tSender   != null) tSender.interrupt();
        closeConnection();
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    private boolean discoverServer() {
        try{
            if (udpSocket == null || udpSocket.isClosed())
                udpSocket = new DatagramSocket();
            udpSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            byte[] data = "TYPE=LOGIN".getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(directoryHost), directoryUdpPort);
            udpSocket.send(packet);
            byte[] buffer = new byte[DATAGRAM_PACKET_SIZE];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(receive);
            String msg = new String(receive.getData(), 0, receive.getLength()).trim();
            if (!msg.startsWith("200 PRINCIPAL")) return false;
            String target = msg.substring("200 PRINCIPAL ".length()).trim();
            int idx = target.lastIndexOf(':');
            if (idx <= 0) return false;
            serverTcpHost = target.substring(0, idx);
            serverTcpPort = Integer.parseInt(target.substring(idx + 1));
            return true;
        } catch (Exception e){
            System.err.println("[ClientService] Discovery failed: " + e.getMessage());
            return false;
        }
    }

    private boolean connectToServer() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);
            out = new ObjectOutputStream(tcpSocket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(tcpSocket.getInputStream());
            try {
                Object obj = in.readObject();
                if (!(obj instanceof Message<?>)) {
                    System.err.println("[ClientService] Unexpected handshake object: " + obj);
                    closeConnection();
                    return false;
                }
                Message<?> handshake = (Message<?>) obj;
                return switch (handshake.getType()) {
                    case ACK -> true;
                    case NACK -> {
                        System.err.println("[ClientService] Server refused connection: " + handshake.getData());
                        closeConnection();
                        yield false;
                    }
                    default -> {
                        System.err.println("[ClientService] Unexpected handshake message: " + handshake.getType());
                        closeConnection();
                        yield false;
                    }
                };
            } catch (ClassNotFoundException e) {
                System.err.println("[ClientService] Handshake failed: " + e.getMessage());
                closeConnection();
                return false;
            }
        } catch (IOException e) {
            System.err.println("[ClientService] Error connecting TCP: " + e.getMessage());
            closeConnection();
            return false;
        }
    }

    private void closeConnection(){
        try { if (in != null) in.close(); }  catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try {
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (Exception ignored) {}
    }

    private void startThreads(){
        tListener = new Thread(new ClientListenerThread(this),  "ClientListener");
        tSender   = new Thread(new RequestSenderThread(this),   "RequestSender");
        tListener.start();
        tSender.start();
    }

    public void handleConnectionLost(){
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
        if (++reconnectAttempts > MAX_RECONNECT_ATTEMPTS){
            System.err.println("[ClientService] Max reconnect attempts reached. Stopping client.");
            manager.stop();
            return;
        }
        System.err.println("[ClientService] Connection lost. Trying to reconnect (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");
        closeConnection();
        try { Thread.sleep(3000); } catch (InterruptedException ignored){}
        run();
    }

    // Getters de IClientService
    public ObjectInputStream getInputStream() { return in; }
    public ObjectOutputStream getOutputStream() { return out; }
    public BlockingQueue<Message<? extends Serializable>> getRequestQueue() { return requestQueue; }
    public BlockingQueue<Message<? extends Serializable>> getResponseQueue() { return responseQueue; }
    public boolean isRunning() { return running; }
    public Socket getTcpSocket() { return tcpSocket; }
    public boolean isAuthenticated(){ return authenticated; }
    public String  getUserType(){ return userType; }
    public String  getUserEmail(){ return userEmail; }
}
