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
import pt.isec.common.util.Log;

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
 * Central network service: server discovery, TCP connection,
 * send/receive queues and observable properties.
 * <p>
 * Implements the connection handshake protocol and an advanced
 * reconnection flow that follows the rules defined in the assignment.
 */
public class ClientService implements IClientService {

    // Observable property names

    public static final String PROP_AUTHENTICATED = "authenticated";
    public static final String PROP_NOTIFICATION = "notification";
    public static final String PROP_USER_TYPE = "userType";
    public static final String PROP_USER_EMAIL = "userEmail";
    public static final String PROP_USER_NAME = "userName";
    public static final String PROP_STUDENT_NUMBER = "studentNumber";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    // Connection status values
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_RECONNECTING = "RECONNECTING";
    public static final String STATUS_DISCONNECTED_PERMANENT = "DISCONNECTED_PERMANENT";

    // Authentication-related events
    public static final String PROP_LOGIN_OK = "loginOK";
    public static final String PROP_LOGIN_FAIL = "loginFail";
    public static final String PROP_REGISTER_OK = "registerOK";

    // Question/answer events
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

    private final BlockingQueue<TcpMessage<? extends Serializable>> requestQueue =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<TcpMessage<? extends Serializable>> responseQueue =
            new LinkedBlockingQueue<>();

    private volatile boolean running = false;

    private final ClientManager manager;

    // Advanced reconnection control
    private final Object reconLock = new Object();
    private volatile boolean reconInProgress = false;

    // Support for potential re-authentication after reconnection
    private volatile String sessionIdForReauth = null;

    /**
     * Creates a new client service.
     *
     * @param manager          client manager
     * @param directoryUdpPort UDP port of the directory service
     * @param directoryHost    host of the directory service
     */
    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
    }

    /* ==================== Observer Management ==================== */

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String prop, PropertyChangeListener l) {
        pcs.addPropertyChangeListener(prop, l);
    }

    public void removePropertyChangeListener(String prop, PropertyChangeListener l) {
        pcs.removePropertyChangeListener(prop, l);
    }

    public void pushNotification(String text) {
        pcs.firePropertyChange(PROP_NOTIFICATION, null, text);
    }

    /* ==================== Message Queues ==================== */

    @Override
    public BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue() {
        return requestQueue;
    }

    @Override
    public BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue() {
        return responseQueue;
    }

    /**
     * Enqueues a message to be sent by {@link RequestSenderThread}.
     *
     * @param tcpMessage message to send
     */
    public void sendMessage(TcpMessage<? extends Serializable> tcpMessage) {
        try {
            requestQueue.put(tcpMessage);
        } catch (InterruptedException e) {
            Log.error(ClientService.class, "Failed to queue message: " + e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Blocks until a response is available in the response queue.
     *
     * @return next response message
     * @throws InterruptedException if interrupted while waiting
     */
    public TcpMessage<? extends Serializable> waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    /**
     * Blocks until a response is available in the response queue or timeout expires.
     *
     * @param timeoutMs timeout in milliseconds
     * @return response message or {@code null} on timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public TcpMessage<? extends Serializable> waitForResponse(long timeoutMs) throws InterruptedException {
        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /* ==================== Lifecycle ==================== */

    /**
     * Starts the discovery and connection process and, if successful,
     * launches the listener/sender/handler threads.
     *
     * @return {@code true} if the initial connection succeeds
     */
    public boolean run() {
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_CONNECTING");
        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++) {
            discovered = discoverServer();
        }

        if (!discovered) {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_ERROR");
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_CONNECTING");
        if (!connectToServer()) {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_ERROR");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_CONNECTED);
        running = true;
        startThreads();
        return true;
    }

    /**
     * Stops the service, interrupts threads and closes connections.
     */
    public void stop() {
        running = false;

        if (tListener != null) {
            tListener.interrupt();
        }
        if (tSender != null) {
            tSender.interrupt();
        }
        if (tHandler != null) {
            tHandler.interrupt();
        }

        closeConnection();

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    /**
     * Clears authentication-related data.
     */
    public void logout() {
        setAuthenticated(false);
        setUserType(null);
        setUserEmail(null);
        setUserId(null);
        setStudentNumber(null);
        setUserName(null);
    }

    /* ==================== Server Discovery via UDP ==================== */

    /**
     * Discovers the principal server via the directory (UDP).
     *
     * @return {@code true} if the server was discovered successfully
     */
    private boolean discoverServer() {
        try {
            if (udpSocket == null || udpSocket.isClosed()) {
                udpSocket = new DatagramSocket();
            }

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
            if (!msg.startsWith("200 PRINCIPAL")) {
                return false;
            }

            String target = msg.substring("200 PRINCIPAL ".length()).trim();
            int idx = target.lastIndexOf(':');
            if (idx <= 0) {
                return false;
            }

            serverTcpHost = target.substring(0, idx);
            serverTcpPort = Integer.parseInt(target.substring(idx + 1));
            return true;

        } catch (Exception e) {
            Log.error(ClientService.class, "Discovery failed: " + e.getMessage(), e);
            return false;
        }
    }

    /* ==================== TCP Connection & Handshake ==================== */

    /**
     * Establishes a TCP connection to the server and performs the handshake.
     *
     * @return {@code true} if the handshake succeeds
     */
    private boolean connectToServer() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            // Create temporary streams for handshake
            ObjectOutputStream tmpOut = new ObjectOutputStream(tcpSocket.getOutputStream());
            tmpOut.flush();
            ObjectInputStream tmpIn = new ObjectInputStream(tcpSocket.getInputStream());

            // Temporary timeout for handshake phase
            tcpSocket.setSoTimeout(CONNECTION_TIMEOUT_MS);
            try {
                Object obj = tmpIn.readObject();
                if (!(obj instanceof TcpMessage<?>)) {
                    Log.error(ClientService.class, "Unexpected handshake object: " + obj);
                    tmpIn.close();
                    tmpOut.close();
                    tcpSocket.close();
                    return false;
                }

                TcpMessage<?> handshake = (TcpMessage<?>) obj;
                switch (handshake.getType()) {
                    case ACK -> {
                        // Handshake OK: use these streams as final
                        out = tmpOut;
                        in = tmpIn;
                        tcpSocket.setSoTimeout(0); // back to normal blocking
                        return true;
                    }
                    case NACK -> {
                        Log.error(ClientService.class,
                                "Server refused connection: " + handshake.getData());
                        tmpIn.close();
                        tmpOut.close();
                        tcpSocket.close();
                        return false;
                    }
                    default -> {
                        Log.error(ClientService.class,
                                "Unexpected handshake message: " + handshake.getType());
                        tmpIn.close();
                        tmpOut.close();
                        tcpSocket.close();
                        return false;
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.error(ClientService.class, "Handshake failed: " + e.getMessage(), e);
                tmpIn.close();
                tmpOut.close();
                tcpSocket.close();
                return false;
            }

        } catch (IOException e) {
            Log.error(ClientService.class, "Error connecting TCP: " + e.getMessage(), e);
            closeConnection();
            return false;
        }
    }

    /**
     * Closes the TCP connection and associated streams.
     */
    private void closeConnection() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
        } catch (Exception ignored) {
        }

        in = null;
        out = null;
        tcpSocket = null;
    }

    /**
     * Starts listener, sender and handler threads.
     */
    private void startThreads() {
        tListener = new Thread(new ClientListenerThread(this), "ClientListener");
        tSender = new Thread(new RequestSenderThread(this), "RequestSender");
        tHandler = new Thread(new ResponseHandlerThread(this), "ResponseHandler");

        tListener.start();
        tSender.start();
        tHandler.start();
    }

    /**
     * Attempts to stop threads gracefully.
     */
    private void stopThreadsGracefully() {
        if (tSender != null) {
            tSender.interrupt();
        }
        if (tListener != null) {
            tListener.interrupt();
        }
        if (tHandler != null) {
            tHandler.interrupt();
        }
        try {
            if (tSender != null) {
                tSender.join(1000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (tListener != null) {
                tListener.join(1000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (tHandler != null) {
                tHandler.join(1000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        tSender = null;
        tListener = null;
        tHandler = null;
    }

    /* ==================== Advanced Reconnection Flow ==================== */

    @Override
    public void handleConnectionLost() {
        synchronized (reconLock) {
            if (reconInProgress) {
                Log.info(ClientService.class, "Reconnection already in progress");
                return;
            }
            reconInProgress = true;
        }

        Thread worker = new Thread(this::doReconnectionFlow, "ReconnectionWorker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Implements the reconnection logic:
     * <ul>
     *     <li>Stops threads and closes current connection</li>
     *     <li>Rediscovers server via directory</li>
     *     <li>Decides whether server address changed</li>
     *     <li>Attempts reconnection within specific time windows</li>
     * </ul>
     * If all attempts fail, notifies permanent disconnection and stops the client.
     */
    private void doReconnectionFlow() {
        try {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_RECONNECTING);

            String oldHost = serverTcpHost;
            int oldPort = serverTcpPort;

            // Stop threads and close current connection
            stopThreadsGracefully();
            closeConnection();

            // 1) Ask directory again
            boolean discovered = discoverServer();
            if (!discovered) {
                try {
                    Thread.sleep(20_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                discovered = discoverServer();
                if (!discovered) {
                    pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                    manager.stop();
                    return;
                }
            }

            boolean hostChanged =
                    (oldHost == null) ||
                            !oldHost.equals(serverTcpHost) ||
                            oldPort != serverTcpPort;

            if (hostChanged) {
                // Server changed: try to connect to the new one for up to 17s
                if (attemptReconnectWindow(17_000)) {
                    return;
                }
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                manager.stop();
                return;
            }

            // Same server: wait 20s and try discovery again
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            discovered = discoverServer();
            if (!discovered) {
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                manager.stop();
                return;
            }

            boolean nowChanged =
                    (oldHost == null) ||
                            !oldHost.equals(serverTcpHost) ||
                            oldPort != serverTcpPort;

            if (nowChanged) {
                if (attemptReconnectWindow(17_000)) {
                    return;
                }
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                manager.stop();
                return;
            }

            // Still the same: try to connect to the same server for 17s
            if (attemptReconnectWindow(17_000)) {
                return;
            }

            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
            manager.stop();
        } finally {
            reconInProgress = false;
        }
    }

    /**
     * Attempts to reconnect within a maximum time window.
     *
     * @param maxMillis maximum time allowed for reconnection attempts
     * @return {@code true} if reconnection succeeds within the time window
     */
    private boolean attemptReconnectWindow(long maxMillis) {
        final long deadline = System.currentTimeMillis() + maxMillis;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            if (connectToServer()) {
                // Here we could attempt transparent re-authentication if supported by the server
                attemptReauth(); // current stub always returns true
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_CONNECTED);
                running = true;
                startThreads();
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    /* ==================== Re-auth Support (optimistic) ==================== */

    /**
     * Stub for future transparent re-authentication using {@code sessionIdForReauth}.
     *
     * @return {@code true} (currently a no-op)
     */
    private boolean attemptReauth() {
        // In the future this could use sessionIdForReauth to perform a transparent re-login
        return true;
    }

    /* ==================== IClientService Implementation ==================== */

    @Override
    public ObjectInputStream getInputStream() {
        return in;
    }

    @Override
    public ObjectOutputStream getOutputStream() {
        return out;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Socket getTcpSocket() {
        return tcpSocket;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public Integer getUserId() {
        return userId;
    }

    @Override
    public Integer getStudentNumber() {
        return studentNumber;
    }

    @Override
    public String getUserType() {
        return userType;
    }

    @Override
    public String getUserEmail() {
        return userEmail;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(String n) {
        String old = this.userName;
        this.userName = n;
        pcs.firePropertyChange(PROP_USER_NAME, old, n);
    }

    @Override
    public void setUserId(Integer id) {
        this.userId = id;
    }

    @Override
    public void setStudentNumber(Integer number) {
        Integer old = this.studentNumber;
        this.studentNumber = number;
        pcs.firePropertyChange(PROP_STUDENT_NUMBER, old, number);
    }

    @Override
    public void setAuthenticated(boolean auth) {
        boolean old = this.authenticated;
        this.authenticated = auth;
        pcs.firePropertyChange(PROP_AUTHENTICATED, old, auth);
    }

    @Override
    public void setUserType(String t) {
        String old = this.userType;
        this.userType = t;
        pcs.firePropertyChange(PROP_USER_TYPE, old, t);
    }

    @Override
    public void setUserEmail(String e) {
        String old = this.userEmail;
        this.userEmail = e;
        pcs.firePropertyChange(PROP_USER_EMAIL, old, e);
    }

    @Override
    public void setPropLoginOk(AuthResponseDTO dto) {
        setAuthenticated(true);
        setUserId(Integer.parseInt(dto.userId()));
        setUserType(dto.userType());
        setUserName(dto.name());
        setUserEmail(dto.email());
        if ("STUDENT".equals(dto.userType())) {
            setStudentNumber(dto.studentNumber());
        }
        // store sessionId for potential re-authentication
        sessionIdForReauth = dto.sessionId();
        pcs.firePropertyChange(PROP_LOGIN_OK, null, dto);
    }

    @Override
    public void setPropError(String s) {
        pcs.firePropertyChange(PROP_LOGIN_FAIL, null, s);
    }

    @Override
    public void setPropRegisterOk(AuthResponseDTO dto) {
        // Do not automatically authenticate; just notify the registration
        pcs.firePropertyChange(PROP_REGISTER_OK, null, dto);
    }

    /* ======= Question/Answer Events ======= */

    @Override
    public void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto) {
        pcs.firePropertyChange(PROP_CREATE_QUESTION_RESPONSE, null, dto);
    }

    @Override
    public void setPropEditQuestionResponse(String message) {
        pcs.firePropertyChange(PROP_UPDATE_QUESTION_RESPONSE, null, message);
    }

    @Override
    public void setPropListQuestionsResponse(List<Question> questions) {
        pcs.firePropertyChange(PROP_LIST_QUESTIONS_RESPONSE, null, questions);
    }

    @Override
    public void setPropJoinQuestionResponse(Question question) {
        pcs.firePropertyChange(PROP_JOIN_QUESTION_RESPONSE, null, question);
    }

    @Override
    public void setPropSubmitAnswerOk(String message) {
        pcs.firePropertyChange(PROP_SUBMIT_ANSWER_OK, null, message);
    }

    @Override
    public void setPropSubmitAnswerFail(String message) {
        pcs.firePropertyChange(PROP_SUBMIT_ANSWER_FAIL, null, message);
    }

    @Override
    public void setPropViewAnswersResponse(List<Answer> answers) {
        pcs.firePropertyChange(PROP_VIEW_ANSWERS_RESPONSE, null, answers);
    }

    @Override
    public void setPropListAnsweredResponse(List<Answer> answers) {
        pcs.firePropertyChange(PROP_LIST_ANSWERED_RESPONSE, null, answers);
    }

    @Override
    public void setPropAnswerSubmitted(Integer questionId) {
        pcs.firePropertyChange(PROP_ANSWER_SUBMITTED, null, questionId);
    }

    @Override
    public void setPropDeleteQuestionResponse(String message) {
        pcs.firePropertyChange(PROP_DELETE_QUESTION_RESPONSE, null, message);
    }

    @Override
    public void setPropUpdateProfileOk(AuthResponseDTO dto) {
        // Update internal state
        setUserId(Integer.parseInt(dto.userId()));
        setUserType(dto.userType());
        setUserName(dto.name());
        setUserEmail(dto.email());
        if ("STUDENT".equals(dto.userType())) {
            setStudentNumber(dto.studentNumber());
        }
        // Fire the event
        pcs.firePropertyChange(PROP_UPDATE_PROFILE_OK, null, dto);
    }

    @Override
    public void setPropUpdateProfileFail(String message) {
        pcs.firePropertyChange(PROP_UPDATE_PROFILE_FAIL, null, message);
    }
}
