package pt.isec.client.core;

import javafx.application.Platform;
import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;
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
import java.net.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central client manager and network service.
 * <p>
 * This class unifies the responsibilities of the previous
 * {@code ClientManager} and {@code ClientService}:
 * <ul>
 *     <li>Discovers the main server via the directory (UDP)</li>
 *     <li>Establishes and manages the TCP connection</li>
 *     <li>Owns the request/response queues and worker threads</li>
 *     <li>Exposes observable properties for the UI layer</li>
 *     <li>Provides access to authentication, question and answer services</li>
 *     <li>Can trigger UI shutdown via a configurable callback</li>
 * </ul>
 * It also implements:
 * <ul>
 *     <li>{@link IClientControllerContext} – API exposed to UI controllers</li>
 *     <li>{@link IClientThreadContext} – API used by networking threads
 *         (and indirectly {@link IClientServiceContext} for client services)</li>
 * </ul>
 */
public class ClientManager implements IClientControllerContext, IClientThreadContext {

    /* ======================= Observable property names ======================= */

    public static final String PROP_AUTHENTICATED     = "authenticated";
    public static final String PROP_NOTIFICATION      = "notification";
    public static final String PROP_USER_TYPE         = "userType";
    public static final String PROP_USER_EMAIL        = "userEmail";
    public static final String PROP_USER_NAME         = "userName";
    public static final String PROP_STUDENT_NUMBER    = "studentNumber";
    public static final String PROP_CONNECTION_STATUS = "connectionStatus";

    // Connection status values
    public static final String STATUS_CONNECTED              = "CONNECTED";
    public static final String STATUS_RECONNECTING           = "RECONNECTING";
    public static final String STATUS_DISCONNECTED_PERMANENT = "DISCONNECTED_PERMANENT";

    // Authentication-related events
    public static final String PROP_LOGIN_OK    = "loginOK";
    public static final String PROP_FAIL        = "fail";
    public static final String PROP_REGISTER_OK = "registerOK";

    // Question/answer events
    public static final String PROP_CREATE_QUESTION_RESPONSE = "createQuestionResponse";
    public static final String PROP_CREATE_QUESTION_FAIL     = "createQuestionFail";
    public static final String PROP_UPDATE_QUESTION_RESPONSE = "editQuestionResponse";
    public static final String PROP_UPDATE_QUESTION_FAIL     = "editQuestionFail";
    public static final String PROP_LIST_QUESTIONS_RESPONSE  = "listQuestionsResponse";
    public static final String PROP_JOIN_QUESTION_RESPONSE   = "joinQuestionResponse";
    public static final String PROP_SUBMIT_ANSWER_OK         = "submitAnswerOk";
    public static final String PROP_SUBMIT_ANSWER_FAIL       = "submitAnswerFail";
    public static final String PROP_VIEW_ANSWERS_RESPONSE    = "viewAnswersResponse";
    public static final String PROP_LIST_ANSWERED_RESPONSE   = "listAnsweredResponse";
    public static final String PROP_ANSWER_SUBMITTED         = "answerSubmitted";
    public static final String PROP_DELETE_QUESTION_RESPONSE = "deleteQuestionResponse";
    public static final String PROP_UPDATE_PROFILE_OK        = "updateProfileOk";
    public static final String PROP_UPDATE_PROFILE_FAIL      = "updateProfileFail";

    /* ============================= Internal state ============================ */

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Authentication/session state
    private volatile boolean authenticated = false;
    private String  userType;
    private String  userEmail;
    private String  userName;
    private Integer userId;
    private Integer studentNumber;

    // Directory config
    private final int    directoryUdpPort;
    private final String directoryHost;

    // Selected main server endpoint
    private int    serverTcpPort;
    private String serverTcpHost;

    // UDP/TCP low-level config
    private static final int DATAGRAM_PACKET_SIZE  = 1024;
    private static final int DISCOVERY_TIMEOUT_MS  = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    // Worker threads
    private Thread tListener;
    private Thread tSender;
    private Thread tHandler;

    // Sockets & streams
    private DatagramSocket     udpSocket;
    private Socket             tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    // Request/response queues
    private final BlockingQueue<TcpMessage<? extends Serializable>> requestQueue =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<TcpMessage<? extends Serializable>> responseQueue =
            new LinkedBlockingQueue<>();

    // Service lifecycle
    private volatile boolean running = false;

    // Advanced reconnection control
    private final Object reconLock = new Object();
    private volatile boolean reconInProgress = false;

    // Support for potential re-authentication after reconnection
    private volatile String sessionIdForReauth = null;

    // Higher-level client services
    private final AuthClientService     authService;
    private final QuestionClientService questionService;
    private final AnswerClientService   answerService;

    // Optional UI closer callback
    private Runnable uiCloser;
    private boolean  uiCloseRequested = false;

    /* =============================== Constructor ============================= */

    /**
     * Creates a new client manager and initializes the low-level
     * network configuration and high-level services.
     *
     * @param ip   directory IP address
     * @param port directory UDP port
     */
    public ClientManager(String ip, int port) {
        this.directoryHost = ip;
        this.directoryUdpPort = port;

        // This instance is used as all three contexts:
        // - IClientServiceContext (via IClientThreadContext)
        // - IClientThreadContext (threads)
        // - IClientControllerContext (controllers)
        this.authService     = new AuthClientService(this);
        this.questionService = new QuestionClientService(this);
        this.answerService   = new AnswerClientService(this);
    }

    /* ============================= Public lifecycle ========================== */

    /**
     * Starts the discovery and TCP connection process and, if successful,
     * launches the internal worker threads.
     * <p>
     * If it is not possible to contact the directory or the main server,
     * the client is stopped and the JavaFX application is terminated.
     */
    public void start() {
        if (!run()) {
            Log.error(ClientManager.class,
                    "Unable to contact directory/server. Shutting down application.");
            stop();
            Platform.exit();
        }
    }

    /**
     * Stops the client manager: closes network resources, stops worker threads
     * and optionally requests the UI to close via the registered {@link #setUiCloser(Runnable)}.
     * <p>
     * The network shutdown is idempotent; calling this method multiple times is safe.
     */
    public void stop() {
        // Stop network part
        stopNetwork();

        // Request UI close (only once)
        if (!uiCloseRequested && uiCloser != null) {
            uiCloseRequested = true;
            try {
                Platform.runLater(uiCloser);
            } catch (Exception e) {
                Log.error(ClientManager.class,
                        "Failed to execute uiCloser: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Registers a callback that will be invoked when the client manager
     * wants the UI to close (for example on permanent disconnection).
     *
     * @param uiCloser runnable to be invoked on the JavaFX Application Thread
     */
    public void setUiCloser(Runnable uiCloser) {
        this.uiCloser = uiCloser;
    }

    /* ======================= IClientControllerContext ======================== */

    /**
     * Adds a property change listener for a specific property.
     *
     * @param prop property name
     * @param l    listener to register
     */
    @Override
    public void addPropertyChangeListener(String prop, PropertyChangeListener l) {
        pcs.addPropertyChangeListener(prop, l);
    }

    /**
     * Removes a previously registered listener from a specific property.
     *
     * @param prop property name
     * @param l    listener to remove
     */
    @Override
    public void removePropertyChangeListener(String prop, PropertyChangeListener l) {
        pcs.removePropertyChangeListener(prop, l);
    }

    /**
     * Clears the current authentication data, but does not close the network.
     */
    // TODO Utilizador: logout
    @Override
    public void logout() {
        setAuthenticated(false);
        setUserType(null);
        setUserEmail(null);
        setUserId(null);
        setStudentNumber(null);
        setUserName(null);
    }

    /**
     * Returns the authentication client service.
     *
     * @return {@link AuthClientService} instance
     */
    @Override
    public AuthClientService getAuthService() {
        return authService;
    }

    /**
     * Returns the question client service.
     *
     * @return {@link QuestionClientService} instance
     */
    @Override
    public QuestionClientService getQuestionService() {
        return questionService;
    }

    /**
     * Returns the answer client service.
     *
     * @return {@link AnswerClientService} instance
     */
    @Override
    public AnswerClientService getAnswerService() {
        return answerService;
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

    /* ======================= IClientServiceContext =========================== */

    /**
     * Returns the request queue where services enqueue outgoing messages.
     *
     * @return outgoing TCP messages queue
     */
    @Override
    public BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue() {
        return requestQueue;
    }

    /* ======================= IClientThreadContext ============================ */

    /**
     * Handles a lost-connection event and starts the reconnection flow.
     */
    @Override
    public void handleConnectionLost() {

        synchronized (reconLock) {
            if (reconInProgress) {
                Log.info(ClientManager.class, "Reconnection already in progress.");
                return;
            }
            reconInProgress = true;
        }

        // TODO Aplicação cliente: recuperação automática de perda de ligação ao servidor principal / falha do servidor principal
        Thread worker = new Thread(this::doReconnectionFlow, "ReconnectionWorker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Indicates whether the client runtime is active.
     *
     * @return {@code true} if threads should keep running
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the current TCP output stream to the server.
     *
     * @return output stream or {@code null} if not connected
     */
    @Override
    public ObjectOutputStream getOutputStream() {
        return out;
    }

    /**
     * Gets the current TCP input stream from the server.
     *
     * @return input stream or {@code null} if not connected
     */
    @Override
    public ObjectInputStream getInputStream() {
        return in;
    }

    /**
     * Returns the response queue used by {@code ClientListenerThread}
     * and consumed by {@code ResponseHandlerThread}.
     *
     * @return incoming TCP messages queue
     */
    @Override
    public BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue() {
        return responseQueue;
    }

    /* ============ IClientThreadContext: auth-related events ================= */

    @Override
    public void setPropLoginOk(AuthResponseDTO dto) {
        setAuthenticated(true);
        try {
            setUserId(Integer.parseInt(dto.userId()));
        } catch (NumberFormatException ignored) {
            setUserId(null);
        }
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
        pcs.firePropertyChange(PROP_FAIL, null, s);
    }

    @Override
    public void setPropRegisterOk(AuthResponseDTO dto) {
        // Do not automatically authenticate; just notify the registration
        pcs.firePropertyChange(PROP_REGISTER_OK, null, dto);
    }

    /* ============ IClientThreadContext: question/answer events ============= */

    @Override
    public void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto) {
        pcs.firePropertyChange(PROP_CREATE_QUESTION_RESPONSE, null, dto);
    }
    @Override
    public void setPropCreateQuestionError(String message) {
        pcs.firePropertyChange(PROP_CREATE_QUESTION_FAIL, null, message);
    }
    @Override
    public void setPropEditQuestionResponse(String message) {
        pcs.firePropertyChange(PROP_UPDATE_QUESTION_RESPONSE, null, message);
    }

    @Override
    public void setPropEditQuestionError(String message){
        pcs.firePropertyChange(PROP_UPDATE_QUESTION_FAIL, null, message);
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
        try {
            setUserId(Integer.parseInt(dto.userId()));
        } catch (NumberFormatException ignored) {
            setUserId(null);
        }
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

    /* ============================ Network lifecycle ========================== */

    /**
     * Performs the initial discovery and connection process and, if successful,
     * launches the network worker threads.
     *
     * @return {@code true} if the initial connection succeeds
     */
    public boolean run() {
        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_CONNECTING");
        boolean discovered = false;
        for (int i = 0; i < 3 && !discovered; i++) {
            // TODO Aplicação cliente: ligação ao servidor principal após consultar o serviço de diretoria
            discovered = discoverServer();
        }

        if (!discovered) {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DIRECTORY_ERROR");
            return false;
        }

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "SERVER_CONNECTING");
        // TODO Aplicação cliente: ligação ao servidor principal após consultar o serviço de diretoria
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
     * Stops the network part: interrupts threads, closes connections
     * and emits a {@link #PROP_CONNECTION_STATUS} change.
     * <p>
     * This method is idempotent.
     */
    private void stopNetwork() {
        if (!running && tcpSocket == null && udpSocket == null) {
            return; // already stopped
        }

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

        tListener = null;
        tSender   = null;
        tHandler  = null;

        pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, "DISCONNECTED");
    }

    /* ========================== Server discovery (UDP) ======================= */

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
                    data,
                    data.length,
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

        } catch (SocketTimeoutException e) {
            // Discovery timeout is expected when the directory is not answering in time
            Log.warn(ClientManager.class, "Directory discovery timed out waiting for response.");
            return false;
        } catch (Exception e) {
            Log.error(ClientManager.class, "Discovery failed: " + e.getMessage(), e);
            return false;
        }
    }

    /* ======================= TCP connection & handshake ====================== */

    /**
     * Establishes a TCP connection to the main server and performs the handshake.
     *
     * @return {@code true} if the handshake succeeds
     */
    private boolean connectToServer() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            // Temporary streams for handshake
            ObjectOutputStream tmpOut = new ObjectOutputStream(tcpSocket.getOutputStream());
            tmpOut.flush();
            ObjectInputStream tmpIn = new ObjectInputStream(tcpSocket.getInputStream());

            // Timeout just for the handshake
            tcpSocket.setSoTimeout(CONNECTION_TIMEOUT_MS);
            try {
                Object obj = tmpIn.readObject();
                if (!(obj instanceof TcpMessage<?> handshake)) {
                    Log.error(ClientManager.class, "Unexpected handshake object: " + obj);
                    tmpIn.close();
                    tmpOut.close();
                    tcpSocket.close();
                    return false;
                }

                switch (handshake.getType()) {
                    case ACK -> {
                        // Handshake OK: promote temp streams to main ones
                        out = tmpOut;
                        in  = tmpIn;
                        tcpSocket.setSoTimeout(0); // back to normal blocking mode
                        return true;
                    }
                    case NACK -> {
                        Log.error(ClientManager.class,
                                "Server refused connection: " + handshake.getData());
                        tmpIn.close();
                        tmpOut.close();
                        tcpSocket.close();
                        return false;
                    }
                    default -> {
                        Log.error(ClientManager.class,
                                "Unexpected handshake message: " + handshake.getType());
                        tmpIn.close();
                        tmpOut.close();
                        tcpSocket.close();
                        return false;
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.error(ClientManager.class, "Handshake failed: " + e.getMessage(), e);
                tmpIn.close();
                tmpOut.close();
                tcpSocket.close();
                return false;
            }

        } catch (IOException e) {
            Log.error(ClientManager.class, "Error connecting TCP: " + e.getMessage(), e);
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

        in        = null;
        out       = null;
        tcpSocket = null;
    }

    /**
     * Starts listener, sender and handler threads that operate over
     * the current TCP connection and message queues.
     */
    private void startThreads() {
        tListener = new Thread(new ClientListenerThread(this), "ClientListener");
        tSender   = new Thread(new RequestSenderThread(this),   "RequestSender");
        tHandler  = new Thread(new ResponseHandlerThread(this), "ResponseHandler");

        tListener.start();
        tSender.start();
        tHandler.start();
    }

    /**
     * Attempts to stop worker threads gracefully, waiting briefly for them to finish.
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

        tSender   = null;
        tListener = null;
        tHandler  = null;
    }

    /* ====================== Advanced reconnection flow (private) ============ */

    /**
     * Implements the reconnection logic:
     * <ul>
     *     <li>Stops threads and closes current connection</li>
     *     <li>Rediscovers server via the directory</li>
     *     <li>Decides whether server address changed</li>
     *     <li>Attempts reconnection within specific time windows</li>
     * </ul>
     * If all attempts fail, notifies permanent disconnection and stops the client.
     */
    private void doReconnectionFlow() {
        // TODO Aplicação cliente: recuperação automática de perda de ligação ao servidor principal / falha do servidor principal
        try {
            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_RECONNECTING);

            String oldHost = serverTcpHost;
            int    oldPort = serverTcpPort;

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
                    stop();
                    return;
                }
            }

            boolean hostChanged =
                    (oldHost == null) ||
                            !oldHost.equals(serverTcpHost) ||
                            oldPort != serverTcpPort;

            if (hostChanged) {
                // Server changed: try to connect to the new one for up to 17 seconds
                if (attemptReconnectWindow(17_000)) {
                    return;
                }
                pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
                stop();
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
                stop();
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
                stop();
                return;
            }

            // Still the same: try to connect to the same server for up to 17 seconds
            if (attemptReconnectWindow(17_000)) {
                return;
            }

            pcs.firePropertyChange(PROP_CONNECTION_STATUS, null, STATUS_DISCONNECTED_PERMANENT);
            stop();
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
}
