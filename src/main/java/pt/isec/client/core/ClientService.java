package pt.isec.client.core;

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
 * filas de envio/receptão e propriedades observáveis.
 *
 * Implementa o protocolo de handshake na ligação e um fluxo de
 * reconexão que segue rigorosamente as regras descritas no enunciado.
 */
public class ClientService implements IClientService {
    // propriedades / estados
    public static final String PROP_AUTHENTICATED = "authenticated";
    public static final String PROP_NOTIFICATION = "notification";
    public static final String PROP_USER_TYPE = "userType";
    public static final String PROP_USER_EMAIL = "userEmail";
    public static final String PROP_USER_NAME = "userName";
    public static final String PROP_STUDENT_NUMBER = "studentNumber";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_RECONNECTING = "RECONNECTING";
    public static final String STATUS_DISCONNECTED_PERMANENT = "DISCONNECTED_PERMANENT";

    public static final String PROP_LOGIN_OK = "loginOK";
    public static final String PROP_LOGIN_FAIL = "loginFail";
    public static final String PROP_REGISTER_OK = "registerOK";

    public static final String PROP_CREATE_QUESTION_RESPONSE = "createQuestionResponse";
    public static final String PROP_UPDATE_QUESTION_RESPONSE = "editQuestionResponse";
    public static final String PROP_LIST_QUESTIONS_RESPONSE = "listQuestionsResponse";
    public static final String PROP_JOIN_QUESTION_RESPONSE = "joinQuestionResponse";
    public static final String PROP_SUBMIT_ANSWER_OK = "submitAnswerOk";
    public static final String PROP_SUBMIT_ANSWER_FAIL = "submitAnswerFail";
    public static final String PROP_VIEW_ANSWERS_RESPONSE = "viewAnswersResponse";
    public static final String PROP_LIST_ANSWERED_RESPONSE = "listAnsweredResponse";
    public static final String PROP_ANSWER_SUBMITTED = "answerSubmitted";
    public static final String PROP_DELETE_QUESTION_RESPONSE = "deleteQuestionResponse";
    public static final String PROP_UPDATE_PROFILE_OK = "updateProfileOk";
    public static final String PROP_UPDATE_PROFILE_FAIL = "updateProfileFail";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private volatile boolean authenticated = false;
    private String userType;
    private String userEmail;
    private Integer userId;
    private Integer studentNumber;
    private String userName;

    private final int directoryUdpPort;
    private final String directoryHost;

    private int serverTcpPort;
    private String serverTcpHost;

    private static final int DATAGRAM_PACKET_SIZE = 1024;
    private static final int DISCOVERY_TIMEOUT_MS = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    private Thread tListener, tSender, tHandler;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final BlockingQueue<TcpMessage<? extends Serializable>> requestQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<TcpMessage<? extends Serializable>> responseQueue = new LinkedBlockingQueue<>();

    private volatile boolean running = false;

    private final ClientManager manager;

    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
    }

    /* ================ Observers ================ */
    public void addPropertyChangeListener(PropertyChangeListener l) { pcs.addPropertyChangeListener(l); }
    public void addPropertyChangeListener(String prop, PropertyChangeListener l) { pcs.addPropertyChangeListener(prop, l); }
    public void removePropertyChangeListener(String prop, PropertyChangeListener l) { pcs.removePropertyChangeListener(prop, l); }
    public void pushNotification(String text) { pcs.firePropertyChange(PROP_NOTIFICATION, null, text); }

    /* ================ Message queues ================ */
    @Override public BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue() { return requestQueue; }
    @Override public BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue() { return responseQueue; }

    public void sendMessage(TcpMessage<? extends Serializable> tcpMessage) {
        try { requestQueue.put(tcpMessage); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public TcpMessage<? extends Serializable> waitForResponse() throws InterruptedException { return responseQueue.take(); }
    public TcpMessage<? extends Serializable> waitForResponse(long timeoutMs) throws InterruptedException { return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS); }

    /* ================ Lifecycle ================ */
    public boolean run() {
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_CONNECTING");
        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++) discovered = discoverServer();
        if (!discovered) {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_ERROR");
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_CONNECTING");
        if (!connectToServer()) {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_ERROR");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_CONNECTED);
        running = true;
        startThreads();
        return true;
    }

    public void stop() {
        running = false;
        if (tListener != null) tListener.interrupt();
        if (tSender != null) tSender.interrupt();
        if (tHandler != null) tHandler.interrupt();
        closeConnection();
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    public void logout() {
        setAuthenticated(false);
        setUserType(null);
        setUserEmail(null);
        setUserId(null);
        setStudentNumber(null);
        setUserName(null);
    }

    /* ================ Discovery ================ */
    private boolean discoverServer() {
        try {
            if (udpSocket == null || udpSocket.isClosed()) udpSocket = new DatagramSocket();
            udpSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            byte[] data = "TYPE=LOGIN".getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(directoryHost), directoryUdpPort);
            udpSocket.send(packet);
            byte[] buffer = new byte[DATAGRAM_PACKET_SIZE];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(receive);
            String msg = new String(receive.getData(), 0, receive.getLength()).trim();
            if (!msg.startsWith("200 PRINCIPAL")) return false;
            String target = msg.substring("200 PRINCIPAL ".length()).trim();
            int idx = target.lastIndexOf(':'); if (idx <= 0) return false;
            serverTcpHost = target.substring(0, idx); serverTcpPort = Integer.parseInt(target.substring(idx + 1));
            return true;
        } catch (Exception e) {
            System.err.println("[ClientService] Discovery failed: " + e.getMessage());
            return false;
        }
    }

    /* ================ TCP connection & handshake ================ */
    private boolean connectToServer() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            ObjectOutputStream tmpOut = new ObjectOutputStream(tcpSocket.getOutputStream());
            tmpOut.flush();
            ObjectInputStream tmpIn = new ObjectInputStream(tcpSocket.getInputStream());

            // set socket read timeout for the handshake phase
            tcpSocket.setSoTimeout(CONNECTION_TIMEOUT_MS);
            try {
                Object obj = tmpIn.readObject();
                if (!(obj instanceof TcpMessage<?>)) {
                    tmpIn.close(); tmpOut.close(); tcpSocket.close();
                    return false;
                }
                TcpMessage<?> handshake = (TcpMessage<?>) obj;
                switch (handshake.getType()) {
                    case ACK -> {
                        out = tmpOut; in = tmpIn; tcpSocket.setSoTimeout(0);
                        return true;
                    }
                    case NACK -> {
                        tmpIn.close(); tmpOut.close(); tcpSocket.close(); return false;
                    }
                    default -> { tmpIn.close(); tmpOut.close(); tcpSocket.close(); return false; }
                }
            } catch (ClassNotFoundException e) {
                tmpIn.close(); tmpOut.close(); tcpSocket.close(); return false;
            }
        } catch (IOException e) {
            closeConnection(); return false;
        }
    }

    private void closeConnection() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close(); } catch (Exception ignored) {}
        in = null; out = null; tcpSocket = null;
    }

    private void startThreads() {
        tListener = new Thread(new ClientListenerThread(this), "ClientListener");
        tSender = new Thread(new RequestSenderThread(this), "RequestSender");
        tHandler = new Thread(new ResponseHandlerThread(this), "ResponseHandler");
        tListener.start(); tSender.start(); tHandler.start();
    }

    private void stopThreadsGracefully() {
        if (tSender != null) tSender.interrupt();
        if (tListener != null) tListener.interrupt();
        if (tHandler != null) tHandler.interrupt();
        try { if (tSender != null) tSender.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { if (tListener != null) tListener.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { if (tHandler != null) tHandler.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        tSender = null; tListener = null; tHandler = null;
    }

    /* ================ Reconnection flow (single worker) ================ */
    private final Object reconLock = new Object();
    private volatile boolean reconInProgress = false;

    @Override
    public void handleConnectionLost() {
        synchronized (reconLock) {
            if (reconInProgress) { System.out.println("[ClientService] Reconnection already in progress"); return; }
            reconInProgress = true;
        }
        Thread worker = new Thread(this::doReconnectionFlow, "ReconnectionWorker"); worker.setDaemon(true); worker.start();
    }

    private void doReconnectionFlow() {
        try {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_RECONNECTING);
            String oldHost = serverTcpHost; int oldPort = serverTcpPort;

            stopThreadsGracefully(); closeConnection();

            // 1) ask directory
            boolean discovered = discoverServer();
            if (!discovered) {
                try { Thread.sleep(20_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                discovered = discoverServer();
                if (!discovered) { pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT); manager.stop(); return; }
            }

            boolean hostChanged = (oldHost == null) || !oldHost.equals(serverTcpHost) || oldPort != serverTcpPort;

            if (hostChanged) {
                // try connect to new server for up to 17s
                if (attemptReconnectWindow(17_000)) return; // either connected and threads restarted or manager stopped inside
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                manager.stop(); return;
            }

            // same server: wait 20s and try discovery again
            try { Thread.sleep(20_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            discovered = discoverServer(); if (!discovered) { pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT); manager.stop(); return; }

            boolean nowChanged = (oldHost == null) || !oldHost.equals(serverTcpHost) || oldPort != serverTcpPort;
            if (nowChanged) {
                if (attemptReconnectWindow(17_000)) return;
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT); manager.stop(); return;
            }

            // still same: attempt reconnect to same for 17s
            if (attemptReconnectWindow(17_000)) return;

            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
            manager.stop();
        } finally {
            reconInProgress = false;
        }
    }

    private boolean attemptReconnectWindow(long maxMillis) {
        final long deadline = System.currentTimeMillis() + maxMillis;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            if (connectToServer()) {
                // optimistic reauth: if we recorded sessionId, server may accept it; otherwise app-level login will happen when necessary
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_CONNECTED);
                running = true; startThreads(); return true;
            }
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        return false;
    }

    /* ================ Reauth support (optimistic) ================ */
    private volatile String sessionIdForReauth = null;

    @Override
    public void setPropLoginOk(AuthResponseDTO dto) {
        setAuthenticated(true);
        setUserId(Integer.parseInt(dto.userId()));
        setUserType(dto.userType());
        setUserName(dto.name());
        setUserEmail(dto.email());
        if ("STUDENT".equals(dto.userType())) setStudentNumber(dto.studentNumber());
        sessionIdForReauth = dto.sessionId();
        pcs.firePropertyChange(PROP_LOGIN_OK, null, dto);
    }

    // optimistic placeholder — server currently has no explicit REAUTH message
    private boolean attemptReauth() { return true; }

    /* ================ IClientService getters/setters ================ */
    @Override public ObjectInputStream getInputStream() { return in; }
    @Override public ObjectOutputStream getOutputStream() { return out; }
    @Override public boolean isRunning() { return running; }
    @Override public Socket getTcpSocket() { return tcpSocket; }

    @Override public boolean isAuthenticated() { return authenticated; }
    @Override public Integer getUserId() { return userId; }
    @Override public Integer getStudentNumber() { return studentNumber; }
    @Override public String getUserType() { return userType; }
    @Override public String getUserEmail() { return userEmail; }
    @Override public String getUserName() { return userName; }

    @Override public void setUserName(String n) { String old = this.userName; this.userName = n; pcs.firePropertyChange(PROP_USER_NAME, old, n); }
    @Override public void setUserId(Integer id) { this.userId = id; }
    @Override public void setStudentNumber(Integer number) { Integer old = this.studentNumber; this.studentNumber = number; pcs.firePropertyChange(PROP_STUDENT_NUMBER, old, number); }
    @Override public void setAuthenticated(boolean auth) { boolean old = this.authenticated; this.authenticated = auth; pcs.firePropertyChange(PROP_AUTHENTICATED, old, auth); }
    @Override public void setUserType(String t) { String old = this.userType; this.userType = t; pcs.firePropertyChange(PROP_USER_TYPE, old, t); }
    @Override public void setUserEmail(String e) { String old = this.userEmail; this.userEmail = e; pcs.firePropertyChange(PROP_USER_EMAIL, old, e); }

    @Override public void setPropError(String s) { pcs.firePropertyChange(PROP_LOGIN_FAIL, null, s); }
    @Override public void setPropRegisterOk(AuthResponseDTO dto) { setPropLoginOk(dto); pcs.firePropertyChange(PROP_REGISTER_OK, null, dto); }

    @Override public void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto) { pcs.firePropertyChange(PROP_CREATE_QUESTION_RESPONSE, null, dto); }
    @Override public void setPropEditQuestionResponse(String message) { pcs.firePropertyChange(PROP_UPDATE_QUESTION_RESPONSE, null, message); }
    @Override public void setPropListQuestionsResponse(List<Question> questions) { pcs.firePropertyChange(PROP_LIST_QUESTIONS_RESPONSE, null, questions); }
    @Override public void setPropJoinQuestionResponse(Question question) { pcs.firePropertyChange(PROP_JOIN_QUESTION_RESPONSE, null, question); }
    @Override public void setPropSubmitAnswerOk(String message) { pcs.firePropertyChange(PROP_SUBMIT_ANSWER_OK, null, message); }
    @Override public void setPropSubmitAnswerFail(String message) { pcs.firePropertyChange(PROP_SUBMIT_ANSWER_FAIL, null, message); }
    @Override public void setPropViewAnswersResponse(List<Answer> answers) { pcs.firePropertyChange(PROP_VIEW_ANSWERS_RESPONSE, null, answers); }
    @Override public void setPropListAnsweredResponse(List<Answer> answers) { pcs.firePropertyChange(PROP_LIST_ANSWERED_RESPONSE, null, answers); }
    @Override public void setPropAnswerSubmitted(Integer questionId) { pcs.firePropertyChange(PROP_ANSWER_SUBMITTED, null, questionId); }
    @Override public void setPropDeleteQuestionResponse(String message) { pcs.firePropertyChange(PROP_DELETE_QUESTION_RESPONSE, null, message); }
    @Override public void setPropUpdateProfileOk(AuthResponseDTO dto) {
        setUserId(Integer.parseInt(dto.userId())); setUserType(dto.userType()); setUserName(dto.name()); setUserEmail(dto.email());
        if ("STUDENT".equals(dto.userType())) setStudentNumber(dto.studentNumber()); pcs.firePropertyChange(PROP_UPDATE_PROFILE_OK, null, dto);
    }
    @Override public void setPropUpdateProfileFail(String message) { pcs.firePropertyChange(PROP_UPDATE_PROFILE_FAIL, null, message); }
}
