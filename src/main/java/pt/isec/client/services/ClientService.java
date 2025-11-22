package pt.isec.client.services;

import javafx.application.Platform;
import pt.isec.client.ClientManager;
import pt.isec.client.threads.ClientListenerThread;
import pt.isec.client.threads.RequestSenderThread;
import pt.isec.client.threads.ResponseHandlerThread;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Serviço central de gestão de rede: descoberta, conexão TCP,
 * filas de envio/recepção e propriedades observáveis.
 *
 * Esta classe implementa a interface IClientService e expõe métodos
 * para as threads de envio/recepção, assim como setters de eventos
 * que notificam os controladores da UI.
 */
public class ClientService implements IClientService {
    // Propriedades de autenticação e estado
    public static final String PROP_AUTHENTICATED     = "authenticated";
    public static final String PROP_NOTIFICATION      = "notification";
    public static final String PROP_USER_TYPE         = "userType";
    public static final String PROP_USER_EMAIL        = "userEmail";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    // Propriedades específicas para eventos de autenticação
    public static final String PROP_LOGIN_OK          = "loginOK";
    public static final String PROP_LOGIN_FAIL        = "loginFail";
    public static final String PROP_REGISTER_OK       = "registerOK";

    // Propriedades para operações de perguntas/respostas
    public static final String PROP_CREATE_QUESTION_RESPONSE = "createQuestionResponse";
    public static final String PROP_LIST_QUESTIONS_RESPONSE  = "listQuestionsResponse";
    public static final String PROP_JOIN_QUESTION_RESPONSE   = "joinQuestionResponse";
    public static final String PROP_SUBMIT_ANSWER_OK         = "submitAnswerOk";
    public static final String PROP_SUBMIT_ANSWER_FAIL       = "submitAnswerFail";
    public static final String PROP_VIEW_ANSWERS_RESPONSE    = "viewAnswersResponse";
    public static final String PROP_LIST_ANSWERED_RESPONSE   = "listAnsweredResponse";

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

    private Thread tListener, tSender, tHandler;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final BlockingQueue<TcpMessage<? extends Serializable>> requestQueue  = new LinkedBlockingQueue<>();
    private final BlockingQueue<TcpMessage<? extends Serializable>> responseQueue = new LinkedBlockingQueue<>();

    private volatile boolean running = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 2;

    private final ClientManager manager;

    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
    }

    /* ==================== Gestão de Observadores ==================== */

    public void addPropertyChangeListener(PropertyChangeListener l){
        pcs.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String prop, PropertyChangeListener l){
        pcs.addPropertyChangeListener(prop, l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l){
        pcs.removePropertyChangeListener(l);
    }

    public void pushNotification(String text){
        pcs.firePropertyChange(PROP_NOTIFICATION, null, text);
    }

    /* ==================== Envio/Recepção de Mensagens ==================== */

    /** Enfileira uma mensagem para ser enviada pela thread RequestSenderThread. */
    public void sendMessage(TcpMessage<? extends Serializable> tcpMessage) {
        try {
            requestQueue.put(tcpMessage);
        } catch (InterruptedException e) {
            System.err.println("[ClientService] Failed to queue message: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /** Bloqueia até obter uma resposta da fila. */
    public TcpMessage<? extends Serializable> waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    /** Bloqueia até obter uma resposta da fila durante um determinado timeout. */
    public TcpMessage<? extends Serializable> waitForResponse(long timeoutMs) throws InterruptedException {
        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /* ==================== Ciclo de Vida ==================== */

    public boolean run(){
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_CONNECTING");
        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++)
            discovered = discoverServer();

        if (!discovered){
            // erro de diretoria
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_ERROR");
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_CONNECTING");
        if (!connectToServer()){
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_ERROR");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) { }
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "CONNECTED");

        running = true;
        startThreads();
        return true;
    }


    public void stop(){
        running = false;

        if (tListener != null) tListener.interrupt();
        if (tSender   != null) tSender.interrupt();
        if (tHandler  != null) tHandler.interrupt();

        closeConnection();

        if (udpSocket != null && !udpSocket.isClosed())
            udpSocket.close();

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    public void logout() {
        setAuthenticated(false);
        setUserType(null);
        setUserEmail(null);
        setUserId(null);
    }

    /* ==================== Descoberta de Servidor via UDP ==================== */

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

    /* ==================== Conexão TCP ao Servidor ==================== */

    private boolean connectToServer() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            out = new ObjectOutputStream(tcpSocket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(tcpSocket.getInputStream());

            try {
                Object obj = in.readObject();
                if (!(obj instanceof TcpMessage<?>)) {
                    System.err.println("[ClientService] Unexpected handshake object: " + obj);
                    closeConnection();
                    return false;
                }

                TcpMessage<?> handshake = (TcpMessage<?>) obj;
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
            if (tcpSocket != null && !tcpSocket.isClosed())
                tcpSocket.close();
        } catch (Exception ignored) {}
    }

    private void startThreads(){
        tListener = new Thread(new ClientListenerThread(this),  "ClientListener");
        tSender   = new Thread(new RequestSenderThread(this),   "RequestSender");
        tHandler  = new Thread(new ResponseHandlerThread(this), "ResponseHandler");

        tListener.start();
        tSender.start();
        tHandler.start();
    }

    /* ==================== Reconexão ==================== */

    public void handleConnectionLost(){
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");

        if (++reconnectAttempts > MAX_RECONNECT_ATTEMPTS){
            System.err.println("[ClientService] Max reconnect attempts reached. Stopping client.");
            manager.stop();
            return;
        }

        System.err.println("[ClientService] Connection lost. Trying to reconnect (" +
                reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");

        closeConnection();

        try { Thread.sleep(3000); } catch (InterruptedException ignored){}

        run();
    }

    /* ==================== Implementação de IClientService ==================== */

    @Override public ObjectInputStream getInputStream() { return in; }
    @Override public ObjectOutputStream getOutputStream() { return out; }
    @Override public boolean isRunning() { return running; }
    @Override public Socket getTcpSocket() { return tcpSocket; }
    @Override public BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue() { return requestQueue; }
    @Override public BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue() { return responseQueue; }
    @Override public boolean isAuthenticated(){ return authenticated; }
    @Override public Integer getUserId() { return userId; }
    @Override public String  getUserType(){ return userType; }
    @Override public String  getUserEmail(){ return userEmail; }

    @Override public void setUserId(Integer id) { this.userId = id; }

    @Override
    public void setAuthenticated(boolean auth){
        boolean old = this.authenticated;
        this.authenticated = auth;
        pcs.firePropertyChange(PROP_AUTHENTICATED, old, auth);
    }

    @Override
    public void setUserType(String t){
        String old = this.userType;
        this.userType = t;
        pcs.firePropertyChange(PROP_USER_TYPE, old, t);
    }

    @Override
    public void setUserEmail(String e){
        String old = this.userEmail;
        this.userEmail = e;
        pcs.firePropertyChange(PROP_USER_EMAIL, old, e);
    }

    @Override
    public void setPropLoginOk(AuthResponseDTO dto){
        pcs.firePropertyChange(PROP_LOGIN_OK, null , dto);
    }

    @Override
    public void setPropError(String s){
        pcs.firePropertyChange(PROP_LOGIN_FAIL, null , s);
    }

    @Override
    public void setPropRegisterOk(AuthResponseDTO dto){
        pcs.firePropertyChange(PROP_REGISTER_OK, null , dto);
    }

    /* ======= Novos eventos para perguntas/respostas ======= */

    @Override
    public void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto){
        pcs.firePropertyChange(PROP_CREATE_QUESTION_RESPONSE, null, dto);
    }

    @Override
    public void setPropListQuestionsResponse(List<Question> questions){
        pcs.firePropertyChange(PROP_LIST_QUESTIONS_RESPONSE, null, questions);
    }

    @Override
    public void setPropJoinQuestionResponse(Question question){
        pcs.firePropertyChange(PROP_JOIN_QUESTION_RESPONSE, null, question);
    }

    @Override
    public void setPropSubmitAnswerOk(String message){
        pcs.firePropertyChange(PROP_SUBMIT_ANSWER_OK, null, message);
    }

    @Override
    public void setPropSubmitAnswerFail(String message){
        pcs.firePropertyChange(PROP_SUBMIT_ANSWER_FAIL, null, message);
    }

    @Override
    public void setPropViewAnswersResponse(List<Answer> answers){
        pcs.firePropertyChange(PROP_VIEW_ANSWERS_RESPONSE, null, answers);
    }

    @Override
    public void setPropListAnsweredResponse(List<Answer> answers){
        pcs.firePropertyChange(PROP_LIST_ANSWERED_RESPONSE, null, answers);
    }
}
