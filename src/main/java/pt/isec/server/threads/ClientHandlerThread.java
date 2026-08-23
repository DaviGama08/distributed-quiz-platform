package pt.isec.server.threads;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.dto.auth.RegisterStudentDTO;
import pt.isec.common.dto.auth.RegisterTeacherDTO;
import pt.isec.common.dto.auth.UpdateStudentDTO;
import pt.isec.common.dto.auth.UpdateTeacherDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.EditQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.dto.question.StudentQuestionDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;
import pt.isec.server.core.IServerThreadContext;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;

/**
 * Thread responsible for handling all communication with a single client
 * for the duration of its TCP session.
 * <p>
 * The thread:
 * <ul>
 *     <li>Applies an initial timeout for the first message</li>
 *     <li>Refuses the connection when this node is not the primary server</li>
 *     <li>Processes all incoming {@link TcpMessage} instances until shutdown</li>
 * </ul>
 */
public class ClientHandlerThread implements Runnable, AutoCloseable {

    /** Timeout, in seconds, for the very first message received from the client. */
    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;

    /** Special value used to disable the read timeout on the socket. */
    private static final Duration NO_TIMEOUT = Duration.ZERO;

    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_STUDENT = "STUDENT";

    /** Operations that may be requested before a connection is authenticated. */
    private static final Set<MessageType> PUBLIC_AUTH_OPERATIONS = Set.of(
            MessageType.LOGIN,
            MessageType.REGISTER_STUDENT,
            MessageType.REGISTER_TEACHER,
            MessageType.RESUME_SESSION
    );

    /** Protected operations reserved for authenticated teachers. */
    private static final Set<MessageType> TEACHER_OPERATIONS = Set.of(
            MessageType.CREATE_QUESTION,
            MessageType.EDIT_QUESTION,
            MessageType.DELETE_QUESTION,
            MessageType.LIST_QUESTIONS,
            MessageType.VIEW_ANSWERS,
            MessageType.UPDATE_TEACHER
    );

    /** Protected operations reserved for authenticated students. */
    private static final Set<MessageType> STUDENT_OPERATIONS = Set.of(
            MessageType.JOIN_QUESTION,
            MessageType.SUBMIT_ANSWER,
            MessageType.LIST_ANSWERED_QUESTIONS,
            MessageType.UPDATE_STUDENT
    );

    private final IServerThreadContext threadInfo;
    private final NetworkTcpConnection connection;

    /** ID of the currently logged-in user for this connection, or {@code null}. */
    private Long loggerUserId = null;

    /** Current session id associated with this connection, or {@code null} if not authenticated. */
    private String currentSessionId = null;

    /** Extra info about current logged user, for session invalidation/logging. */
    private String currentUserType = null;
    private String currentUserName = null;
    private String currentUserEmail = null;


    /**
     * Creates a new handler for a given client connection.
     *
     * @param threadInfo server manager context
     * @param connection TCP connection with the client
     */
    public ClientHandlerThread(IServerThreadContext threadInfo, NetworkTcpConnection connection) {
        this.threadInfo = threadInfo;
        this.connection = connection;
    }

    /**
     * Main loop:
     * <ul>
     *     <li>Applies an initial timeout for the first message</li>
     *     <li>Refuses the connection if this node is not the primary</li>
     *     <li>Otherwise, sends an ACK and processes incoming messages</li>
     * </ul>
     * Handles timeouts, socket errors and closes the connection on exit.
     */
    @Override
    public void run() {
        try {
            // TODO Aplicação cliente: ligação encerrada pelo servidor após 30 segundos (pode ser aumentado) sem tentativa de registo ou autenticação
            // Initial 30-second timeout for the first client message
            connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

            // If server is not primary, refuse connection
            if (!threadInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary"));
                return;
            }

            // Connection accepted
            connection.sendMessage(new TcpMessage<>(MessageType.ACK, "ok"));

            // TODO Servidor principal e secundários: thread para comunicação com cada cliente ligado via TCP (pedido e resposta)
            while (threadInfo.isRunning()) {
                TcpMessage<?> msg = connection.receiveMessage();
                if (msg == null) {
                    break;
                }
                processMessage(msg);
            }
        } catch (SocketTimeoutException e) {
            if (!threadInfo.isRunning()) {
                // Timeout during shutdown – expected behavior
                Log.info(ClientHandlerThread.class,
                        "[TCP] Client connection terminated due to server shutdown.");
            } else {
                Log.error(ClientHandlerThread.class,
                        "[TCP] Read timeout on client connection: %s", e.getMessage());
            }
        } catch (SocketException e) {
            if (!threadInfo.isRunning()) {
                // Socket closed during shutdown
                Log.info(ClientHandlerThread.class,
                        "[TCP] Client socket closed during server shutdown.");
            } else {
                Log.error(ClientHandlerThread.class,
                        "[TCP] Client connection closed due to socket error: %s", e.getMessage());
            }
        } catch (Exception e) {
            Log.error(ClientHandlerThread.class,
                    "[TCP] Client connection closed due to unexpected exception: %s", e.getMessage());
        } finally {
            Log.info(ClientHandlerThread.class,
                    "Client handler thread terminated (client connection closed).");

            if (loggerUserId != null) {
                try {
                    threadInfo.unregisterClientConnection(currentUserType, loggerUserId, connection);
                } catch (Exception ignored) { }

                loggerUserId = null;
                currentUserType = null;
                currentUserName = null;
                currentUserEmail = null;
                currentSessionId = null;
            }

            try {
                connection.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Receives and processes messages from the client according to their {@link MessageType}.
     * <p>
     * Every case in the {@code switch} delegates to the appropriate service method and
     * sends back the corresponding response message to the client.
     *
     * @param tcpMessage message received from the client
     * @throws Exception if a service call fails
     */
    //TODO thread para comunicação com cada cliente ligado via TCP (pedido e resposta)
    private void processMessage(TcpMessage<?> tcpMessage) throws Exception {
        if (tcpMessage == null) {
            return;
        }

        if (tcpMessage.getType() == null) {
            sendAuthorizationError("invalid-message-type");
            return;
        }

        try {
            authorizeOperation(tcpMessage.getType());
        } catch (SecurityException e) {
            sendAuthorizationError(e.getMessage());
            return;
        }

        switch (tcpMessage.getType()) {

            /* ========= AUTH ========= */

            case REGISTER_STUDENT -> {
                try {
                    RegisterStudentDTO dto = tcpMessage.getDataAs(RegisterStudentDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().registerStudent(dto);

                    long userId = establishAuthenticatedSession(res, ROLE_STUDENT);

                    try {
                        threadInfo.registerClientConnection(currentUserType, userId, connection);
                    } catch (Exception ignored) {
                    }

                    connection.sendMessage(new TcpMessage<>(MessageType.REGISTER_OK, res, AuthResponseDTO.class));
                    connection.setReadTimeout(NO_TIMEOUT);
                    Log.info(ClientHandlerThread.class, "[TCP] Student registered successfully.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case REGISTER_TEACHER -> {
                try {
                    RegisterTeacherDTO dto = tcpMessage.getDataAs(RegisterTeacherDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().registerTeacher(dto);

                    long userId = establishAuthenticatedSession(res, ROLE_TEACHER);

                    try {
                        threadInfo.registerClientConnection(currentUserType, userId, connection);
                    } catch (Exception ignored) {
                    }

                    connection.sendMessage(new TcpMessage<>(MessageType.REGISTER_OK, res, AuthResponseDTO.class));
                    connection.setReadTimeout(NO_TIMEOUT);
                    Log.info(ClientHandlerThread.class, "[TCP] Teacher registered successfully.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LOGIN -> {
                try {
                    LoginRequestDTO dto = tcpMessage.getDataAs(LoginRequestDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().login(dto);

                    long userId = establishAuthenticatedSession(res, null);

                    // Register active connection to allow server-to-client notifications
                    try {
                        threadInfo.registerClientConnection(currentUserType, userId, connection);
                    } catch (Exception ignored) {
                    }

                    connection.sendMessage(new TcpMessage<>(MessageType.LOGIN_OK, res, AuthResponseDTO.class));
                    connection.setReadTimeout(NO_TIMEOUT);
                    Log.info(ClientHandlerThread.class, "[TCP] Login successfully.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.LOGIN_FAIL, e.getMessage(), String.class));
                }
            }

            case LOGOUT -> {
                if (loggerUserId != null && currentSessionId != null) {
                    try {
                        threadInfo.getAuthService().invalidateSession(
                                loggerUserId,
                                currentSessionId,
                                currentUserType
                        );
                    } catch (Exception e) {
                        Log.error(ClientHandlerThread.class,
                                "[TCP] Failed to invalidate session in database: %s", e.getMessage());
                    }

                    try {
                        threadInfo.unregisterClientConnection(currentUserType, loggerUserId, connection);
                    } catch (Exception ignored) {
                    }

                    loggerUserId = null;
                    currentSessionId = null;
                    currentUserType = null;
                    currentUserName = null;
                    currentUserEmail = null;
                }

                connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));
                connection.sendMessage(new TcpMessage<>(MessageType.ACK, "logout-ok", String.class));
                Log.info(ClientHandlerThread.class, "[TCP] Logout successfully. Good bye.");
            }
            case RESUME_SESSION -> {
                try {
                    String sessionId = tcpMessage.getDataAs(String.class);

                    AuthResponseDTO res = threadInfo.getAuthService().resumeSession(sessionId);

                    long userId = establishAuthenticatedSession(res, null);

                    // volta a registar a ligação para notificações server→cliente
                    try {
                        threadInfo.registerClientConnection(currentUserType, userId, connection);
                    } catch (Exception ignored) { }

                    // a partir daqui já não queremos timeout de primeira mensagem
                    connection.setReadTimeout(NO_TIMEOUT);

                    connection.sendMessage(new TcpMessage<>(MessageType.RESUME_SESSION_OK, res, AuthResponseDTO.class));
                    Log.info(ClientHandlerThread.class, "[TCP] Session resumed successfully.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.RESUME_SESSION_FAIL, e.getMessage(), String.class));
                    Log.error(ClientHandlerThread.class,
                            "[TCP] Failed to resume session: %s", e.getMessage());
                }
            }

            /* ========= QUESTIONS (TEACHER) ========= */

            case CREATE_QUESTION -> {
                try {
                    CreateQuestionDTO dto = tcpMessage.getDataAs(CreateQuestionDTO.class);
                    requireSameUser(dto.teacherId());
                    CreateQuestionResponseDTO res = threadInfo.getQuestionService().createQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.CREATE_QUESTION_RESPONSE, res,
                            CreateQuestionResponseDTO.class));
                    Log.info(ClientHandlerThread.class, "[TCP] Question created successfully.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.CREATE_QUESTION_FAIL, e.getMessage(), String.class));
                }
            }

            case EDIT_QUESTION -> {
                try {
                    EditQuestionDTO dto = tcpMessage.getDataAs(EditQuestionDTO.class);
                    requireSameUser(dto.teacherId());
                    boolean ok = threadInfo.getQuestionService().editQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "edit-ok" : "edit-fail",
                            String.class
                    ));
                    if(ok)
                        Log.info(ClientHandlerThread.class, "[TCP] Question edited successfully.");
                    else
                        Log.error(ClientHandlerThread.class, "[TCP] Question edited failed.");
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.EDIT_QUESTION_FAIL, e.getMessage(), String.class));
                }
            }

            case DELETE_QUESTION -> {
                try {
                    DeleteQuestionDTO dto = tcpMessage.getDataAs(DeleteQuestionDTO.class);
                    requireSameUser(dto.teacherId());
                    boolean ok = threadInfo.getQuestionService().deleteQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "delete-ok" : "delete-fail",
                            String.class
                    ));
                    if(ok)
                        Log.info(ClientHandlerThread.class, "[TCP] Question deleted successfully.");
                    else
                        Log.error(ClientHandlerThread.class, "[TCP] Question delete failed.");
                } catch (IllegalStateException e) {
                    // question with answers, etc.
                    connection.sendMessage(new TcpMessage<>(MessageType.NACK, e.getMessage(), String.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LIST_QUESTIONS -> {
                try {
                    ListQuestionsDTO dto = tcpMessage.getDataAs(ListQuestionsDTO.class);
                    requireSameUser(dto.teacherId());
                    List<Question> list = threadInfo.getQuestionService().listQuestions(dto);
                    // Use ArrayList as payload type because it is Serializable
                    TcpMessage<ArrayList<Question>> out = new TcpMessage<>(
                            MessageType.LIST_QUESTIONS_RESPONSE,
                            new ArrayList<>(list)
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= QUESTIONS (STUDENT) ========= */

            case JOIN_QUESTION -> {
                try {
                    JoinQuestionDTO dto = tcpMessage.getDataAs(JoinQuestionDTO.class);
                    requireSameUser(dto.studentId());
                    StudentQuestionDTO q = threadInfo.getQuestionService().joinQuestion(dto);
                    if (q == null) {
                        connection.sendMessage(new TcpMessage<>(MessageType.NACK, "invalid-code", String.class));
                    } else {
                        connection.sendMessage(new TcpMessage<>(
                                MessageType.QUESTION_DETAILS,
                                q,
                                StudentQuestionDTO.class
                        ));
                    }
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= ANSWERS ========= */

            case SUBMIT_ANSWER -> {
                try {
                    SubmitAnswerDTO dto = tcpMessage.getDataAs(SubmitAnswerDTO.class);
                    requireSameUser(dto.studentId());
                    boolean ok = threadInfo.getAnswerService().submitAnswer(dto);

                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.SUBMIT_OK : MessageType.SUBMIT_FAIL,
                            ok ? "Respondido com sucesso!" : "Submissão da resposta sem sucesso!",
                            String.class
                    ));

                } catch (IllegalStateException e) {
                    connection.sendMessage(new TcpMessage<>(
                            MessageType.SUBMIT_FAIL,
                            e.getMessage(),
                            String.class
                    ));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(
                            MessageType.ERROR,
                            e.getMessage(),
                            String.class
                    ));
                }
            }

            case VIEW_ANSWERS -> {
                try {
                    ViewAnswersDTO dto = tcpMessage.getDataAs(ViewAnswersDTO.class);
                    requireSameUser(dto.teacherId());
                    List<Answer> list = threadInfo.getAnswerService().viewAnswers(dto);
                    // Use ArrayList as payload type because it is Serializable
                    TcpMessage<ArrayList<Answer>> out = new TcpMessage<>(
                            MessageType.VIEW_ANSWERS_RESPONSE,
                            new ArrayList<>(list)
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= PROFILE ========= */

            case UPDATE_STUDENT -> {
                try {
                    UpdateStudentDTO dto = tcpMessage.getDataAs(UpdateStudentDTO.class);
                    requireSameUser(dto.userId());
                    AuthResponseDTO res = threadInfo.getAuthService().updateStudent(dto);
                    updateAuthenticatedProfile(res);
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_FAIL, e.getMessage(), String.class));
                }
            }

            case UPDATE_TEACHER -> {
                try {
                    UpdateTeacherDTO dto = tcpMessage.getDataAs(UpdateTeacherDTO.class);
                    requireSameUser(dto.userId());
                    requireSameUser(dto.teacherId());
                    AuthResponseDTO res = threadInfo.getAuthService().updateTeacher(dto);
                    updateAuthenticatedProfile(res);
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_FAIL, e.getMessage(), String.class));
                }
            }

            case LIST_ANSWERED_QUESTIONS -> {
                try {
                    Integer studentId = tcpMessage.getDataAs(Integer.class);
                    requireSameUser(studentId);
                    List<Answer> list = threadInfo.getAnswerService().getStudentHistory(studentId);
                    // Use ArrayList as payload type because it is Serializable
                    TcpMessage<ArrayList<Answer>> out = new TcpMessage<>(
                            MessageType.LIST_ANSWERED_RESPONSE,
                            new ArrayList<>(list)
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= DEFAULT ========= */

            default -> connection.sendMessage(
                    new TcpMessage<>(MessageType.ERROR, "Tipo de mensagem não suportado", String.class)
            );
        }
    }

    /**
     * Applies the connection-level authentication and role policy before dispatching a request.
     */
    private void authorizeOperation(MessageType messageType) {
        if (PUBLIC_AUTH_OPERATIONS.contains(messageType)) {
            requireUnauthenticated();
            return;
        }

        requireAuthenticated();

        if (TEACHER_OPERATIONS.contains(messageType)) {
            requireRole(ROLE_TEACHER);
        } else if (STUDENT_OPERATIONS.contains(messageType)) {
            requireRole(ROLE_STUDENT);
        }
    }

    private void requireAuthenticated() {
        if (loggerUserId == null || currentUserType == null) {
            throw new SecurityException("authentication-required");
        }
    }

    private void requireUnauthenticated() {
        if (loggerUserId != null || currentUserType != null) {
            throw new SecurityException("already-authenticated");
        }
    }

    private void requireRole(String expectedRole) {
        requireAuthenticated();
        if (!expectedRole.equals(currentUserType)) {
            throw new SecurityException("forbidden-role");
        }
    }

    private void requireSameUser(Integer claimedUserId) {
        requireAuthenticated();
        if (claimedUserId == null || loggerUserId.longValue() != claimedUserId.longValue()) {
            throw new SecurityException("identity-mismatch");
        }
    }

    /**
     * Validates an authentication response before making it authoritative for this connection.
     *
     * @return the authenticated user id
     */
    private long establishAuthenticatedSession(AuthResponseDTO response, String expectedRole) {
        requireUnauthenticated();
        if (response == null || response.userId() == null || response.userType() == null) {
            throw new SecurityException("invalid-authentication-response");
        }

        final long userId;
        try {
            userId = Long.parseLong(response.userId());
        } catch (NumberFormatException e) {
            throw new SecurityException("invalid-authentication-response");
        }

        String role = response.userType().trim().toUpperCase(Locale.ROOT);
        if (userId <= 0 || (!ROLE_TEACHER.equals(role) && !ROLE_STUDENT.equals(role))) {
            throw new SecurityException("invalid-authentication-response");
        }
        if (expectedRole != null && !expectedRole.equals(role)) {
            throw new SecurityException("invalid-authentication-response");
        }

        this.loggerUserId = userId;
        this.currentSessionId = response.sessionId();
        this.currentUserType = role;
        this.currentUserName = response.name();
        this.currentUserEmail = response.email();
        return userId;
    }

    private void updateAuthenticatedProfile(AuthResponseDTO response) {
        if (response != null) {
            this.currentUserName = response.name();
            this.currentUserEmail = response.email();
        }
    }

    private void sendAuthorizationError(String reason) throws IOException {
        connection.sendMessage(new TcpMessage<>(MessageType.ERROR, reason, String.class));
    }

    /**
     * Closes the underlying TCP connection of this handler.
     */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }
}
