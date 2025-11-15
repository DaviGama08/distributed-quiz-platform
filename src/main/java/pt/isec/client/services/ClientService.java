package pt.isec.client.services;

import pt.isec.client.ClientManager;
import pt.isec.client.threads.ClientListenerRunnable;
import pt.isec.client.threads.RequestSenderRunnable;
import pt.isec.client.threads.ResponseHandlerRunnable;
import pt.isec.common.messages.Message;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientService implements IClientService {
    /* === PROPRIEDADES OBSERVÁVEIS (para UI) === */
    public static final String PROP_AUTHENTICATED     = "authenticated";
    public static final String PROP_NOTIFICATION      = "notification";
    public static final String PROP_USER_TYPE         = "userType";
    public static final String PROP_USER_EMAIL        = "userEmail";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /* === ESTADO DE AUTENTICAÇÃO === */
    private volatile boolean authenticated = false;
    private String userType;     // "STUDENT", "TEACHER"
    private String userEmail;

    /* === DISCOVERY / CONEXÃO === */
    private final int   directoryUdpPort;
    private final String directoryHost;
    private int    serverTcpPort;
    private String serverTcpHost;

    private static final int DATAGRAM_PACKET_SIZE  = 1024;
    private static final int DISCOVERY_TIMEOUT_MS  = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    /* === THREADS === */
    private Thread tListener, tSender, tResponse;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    /* === FILAS PARA REQUESTS / RESPONSES === */
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

    /* ========================= PUBLIC API: listeners ========================= */

    public void addPropertyChangeListener(PropertyChangeListener l){
        pcs.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String prop, PropertyChangeListener l){
        pcs.addPropertyChangeListener(prop, l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l){
        pcs.removePropertyChangeListener(l);
    }

    public void removePropertyChangeListener(String prop, PropertyChangeListener l){
        pcs.removePropertyChangeListener(prop, l);
    }

    private void fire(String prop, Object oldV, Object newV){
        pcs.firePropertyChange(prop, oldV, newV);
    }

    /* ========================= setters chamados pelos serviços ========================= */

    public void setAuthenticated(boolean auth){
        boolean old = this.authenticated;
        this.authenticated = auth;
        fire(PROP_AUTHENTICATED, old, auth);
        if (auth)
            fire(PROP_CONNECTION_STATUS, null, "AUTHENTICATED");
    }

    public void setUserType(String t){
        String old = this.userType;
        this.userType = t;
        fire(PROP_USER_TYPE, old, t);
    }

    public void setUserEmail(String e){
        String old = this.userEmail;
        this.userEmail = e;
        fire(PROP_USER_EMAIL, old, e);
    }

    /** notificação assíncrona vinda do servidor */
    public void pushNotification(String text){
        fire(PROP_NOTIFICATION, null, text);
    }

    /* ========================= API usada pelos serviços (mensagens) ========================= */

    /** Enfileira pedido para ser enviado pela RequestSenderRunnable */
    public void sendMessage(Message<? extends Serializable> message) {
        try {
            requestQueue.put(message);
        } catch (InterruptedException e) {
            System.err.println("[ClientService] Failed to queue message: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /** Bloqueia até receber uma resposta na responseQueue (preenchida pela ResponseHandlerRunnable) */
    public Message<? extends Serializable> waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    /* ========================= LIFECYCLE ========================= */

    /** ciclo completo: discovery → TCP connect → arrancar threads */
    public void run(){
        fire(PROP_CONNECTION_STATUS, null, "CONNECTING");

        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++)
            discovered = discoverServer();

        if (!discovered){
            fire(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
            return;
        }

        if (!connectToServer()){
            fire(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
            return;
        }

        fire(PROP_CONNECTION_STATUS, null, "CONNECTED");
        running = true;
        startThreads();
    }

    public void stop(){
        running = false;

        if (tListener != null) tListener.interrupt();
        if (tSender   != null) tSender.interrupt();
        if (tResponse != null) tResponse.interrupt();

        closeConnection();

        if (udpSocket != null && !udpSocket.isClosed())
            udpSocket.close();

        fire(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    /* ========================= NETWORK ========================= */

    private boolean discoverServer() {
        try{
            if (udpSocket == null || udpSocket.isClosed())
                udpSocket = new DatagramSocket();

            udpSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            byte[] data = "TYPE=LOGIN".getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(directoryHost),
                    directoryUdpPort
            );
            udpSocket.send(packet);

            byte[] buffer = new byte[DATAGRAM_PACKET_SIZE];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(receive);

            String msg = new String(receive.getData(), 0, receive.getLength()).trim();
            if (!msg.startsWith("200 PRINCIPAL"))
                return false;

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
        try{
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            out = new ObjectOutputStream(tcpSocket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(tcpSocket.getInputStream());

            return true;
        } catch (IOException e){
            System.err.println("[ClientService] Error connecting TCP: " + e.getMessage());
            closeConnection();
            return false;
        }
    }

    private void closeConnection(){
        try { if (in != null) in.close(); }  catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try {
            if (tcpSocket != null && !tcpSocket.isClosed())
                tcpSocket.close();
        } catch (Exception ignored) {}
    }

    private void startThreads(){
        tListener = new Thread(new ClientListenerRunnable(this),  "ClientListener");
        tSender   = new Thread(new RequestSenderRunnable(this),   "RequestSender");
        tResponse = new Thread(new ResponseHandlerRunnable(this), "ResponseHandler");

        tListener.start();
        tSender.start();
        tResponse.start();
    }

    public void handleConnectionLost(){
        fire(PROP_CONNECTION_STATUS, null, "DISCONNECTED");

        if (++reconnectAttempts > MAX_RECONNECT_ATTEMPTS){
            System.err.println("[ClientService] Max reconnect attempts reached. Stopping client.");
            manager.stop();
            return;
        }

        System.err.println("[ClientService] Connection lost. Trying to reconnect (" +
                reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");

        closeConnection();

        try { Thread.sleep(3000); } catch (InterruptedException ignored){}

        run(); // tenta tudo de novo
    }

    /* ========================= IClientService getters ========================= */

    @Override
    public ObjectInputStream getInputStream() { return in; }

    @Override
    public ObjectOutputStream getOutputStream() { return out; }

    @Override
    public BlockingQueue<Message<? extends Serializable>> getRequestQueue() { return requestQueue; }

    @Override
    public BlockingQueue<Message<? extends Serializable>> getResponseQueue() { return responseQueue; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public Socket getTcpSocket() { return tcpSocket; }

    /* ========================= Estado exposto à UI ========================= */

    public boolean isAuthenticated(){ return authenticated; }
    public String  getUserType(){ return userType; }
    public String  getUserEmail(){ return userEmail; }
}
