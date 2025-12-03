package pt.isec.server.threads;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.*;
import pt.isec.common.dto.question.*;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread responsible for handling all communication with a single client
 * for the duration of its TCP session.
 */
public class ClientHandlerThread implements Runnable, AutoCloseable {
    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;
    private static final Duration NO_TIMEOUT = Duration.ZERO;

    private final IServerThreadContext threadInfo;
    private final NetworkTcpConnection connection;

    private Long loggerUserId = null;
    private String sessionId = null;

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
            // Initial 30-second timeout for the first client message
            connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

            // If server is not primary, refuse connection
            if (!threadInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary"));
                return;
            }

            // Connection accepted
            connection.sendMessage(new TcpMessage<>(MessageType.ACK, "ok"));
            // connection.setReadTimeout(NO_TIMEOUT);

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
            e.printStackTrace();
        } finally {
            Log.info(ClientHandlerThread.class,
                    "Client handler thread terminated (client connection closed).");
            if (loggerUserId != null) {
                try {
                    threadInfo.unregisterClientConnection(loggerUserId);
                } catch (Exception ignored) {
                }
                threadInfo.unregisterLogin(loggerUserId);
            }
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Receives and processes messages from the client according to their {@link MessageType}.
     *
     * @param tcpMessage message received from the client
     * @throws Exception if a service call fails
     */
    private void processMessage(TcpMessage<?> tcpMessage) throws Exception {
        if (tcpMessage == null) {
            return;
        }

        switch (tcpMessage.getType()) {

            /* ========= AUTH ========= */

            case REGISTER_STUDENT -> {
                try {
                    RegisterStudentDTO dto = tcpMessage.getDataAs(RegisterStudentDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().registerStudent(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.REGISTER_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case REGISTER_TEACHER -> {
                try {
                    RegisterTeacherDTO dto = tcpMessage.getDataAs(RegisterTeacherDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().registerTeacher(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.REGISTER_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LOGIN -> {
                try {
                    LoginRequestDTO dto = tcpMessage.getDataAs(LoginRequestDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().login(dto);

                    long userId = Long.parseLong(res.userId());

                    // If a session is already registered for this user, clear it but do NOT refuse login
                    try {
                        if (threadInfo.isUserLogged(userId)) {
                            threadInfo.unregisterClientConnection(userId);
                            threadInfo.unregisterLogin(userId);
                        }
                    } catch (Exception ignored) {
                    }

                    threadInfo.registerLogin(userId, res.sessionId());
                    this.loggerUserId = userId;
                    this.sessionId = res.sessionId();

                    // Also register active connection to allow server-to-client notifications
                    try {
                        threadInfo.registerClientConnection(userId, connection);
                    } catch (Exception ignored) {
                    }

                    connection.sendMessage(new TcpMessage<>(MessageType.LOGIN_OK, res, AuthResponseDTO.class));
                    connection.setReadTimeout(NO_TIMEOUT);
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.LOGIN_FAIL, e.getMessage(), String.class));
                }
            }

            case LOGOUT -> {
                if (loggerUserId != null) {
                    try {
                        threadInfo.unregisterClientConnection(loggerUserId);
                    } catch (Exception ignored) {
                    }
                    threadInfo.unregisterLogin(loggerUserId);
                    loggerUserId = null;
                    sessionId = null;
                }
                // Apply 30-second timeout again for a possible new session
                connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));
                // Send logout confirmation
                connection.sendMessage(new TcpMessage<>(MessageType.ACK, "logout-ok", String.class));
            }

            /* ========= QUESTIONS (TEACHER) ========= */

            case CREATE_QUESTION -> {
                try {
                    CreateQuestionDTO dto = tcpMessage.getDataAs(CreateQuestionDTO.class);
                    CreateQuestionResponseDTO res = threadInfo.getQuestionService().createQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.CREATE_QUESTION_RESPONSE, res,
                            CreateQuestionResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.CREATE_QUESTION_FAIL, e.getMessage(), String.class));
                }
            }

            case EDIT_QUESTION -> {
                try {
                    EditQuestionDTO dto = tcpMessage.getDataAs(EditQuestionDTO.class);
                    boolean ok = threadInfo.getQuestionService().editQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "edit-ok" : "edit-fail",
                            String.class
                    ));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.EDIT_QUESTION_FAIL, e.getMessage(), String.class));
                }
            }

            case DELETE_QUESTION -> {
                try {
                    DeleteQuestionDTO dto = tcpMessage.getDataAs(DeleteQuestionDTO.class);
                    boolean ok = threadInfo.getQuestionService().deleteQuestion(dto);
                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "delete-ok" : "delete-fail",
                            String.class
                    ));
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
                    List<?> list = threadInfo.getQuestionService().listQuestions(dto);
                    ArrayList<?> payload = new ArrayList<>(list);
                    TcpMessage<ArrayList<?>> out = new TcpMessage<>(
                            MessageType.LIST_QUESTIONS_RESPONSE,
                            payload,
                            (Class) ArrayList.class
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
                    Question q = threadInfo.getQuestionService().joinQuestion(dto);
                    if (q == null) {
                        connection.sendMessage(new TcpMessage<>(MessageType.NACK, "invalid-code", String.class));
                    } else {
                        connection.sendMessage(new TcpMessage<>(MessageType.QUESTION_DETAILS, q, Question.class));
                    }
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= ANSWERS ========= */

            case SUBMIT_ANSWER -> {
                try {
                    SubmitAnswerDTO dto = tcpMessage.getDataAs(SubmitAnswerDTO.class);
                    boolean ok = threadInfo.getAnswerService().submitAnswer(dto);

                    connection.sendMessage(new TcpMessage<>(
                            ok ? MessageType.SUBMIT_OK : MessageType.SUBMIT_FAIL,
                            ok ? "Respondido com sucesso!" : "Submissão da resposta sem sucesso!",
                            String.class
                    ));

                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case VIEW_ANSWERS -> {
                try {
                    ViewAnswersDTO dto = tcpMessage.getDataAs(ViewAnswersDTO.class);
                    List<?> list = threadInfo.getAnswerService().viewAnswers(dto);
                    ArrayList<?> payload = new ArrayList<>(list);
                    TcpMessage<ArrayList<?>> out = new TcpMessage<>(
                            MessageType.VIEW_ANSWERS_RESPONSE,
                            payload,
                            (Class) ArrayList.class
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
                    AuthResponseDTO res = threadInfo.getAuthService().updateStudent(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_FAIL, e.getMessage(), String.class));
                }
            }

            case UPDATE_TEACHER -> {
                try {
                    UpdateTeacherDTO dto = tcpMessage.getDataAs(UpdateTeacherDTO.class);
                    AuthResponseDTO res = threadInfo.getAuthService().updateTeacher(dto);
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_OK, res, AuthResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new TcpMessage<>(MessageType.UPDATE_PROFILE_FAIL, e.getMessage(), String.class));
                }
            }

            case LIST_ANSWERED_QUESTIONS -> {
                try {
                    Integer studentId = tcpMessage.getDataAs(Integer.class);
                    List<?> list = threadInfo.getAnswerService().getStudentHistory(studentId);
                    ArrayList<?> payload = new ArrayList<>(list);
                    TcpMessage<ArrayList<?>> out = new TcpMessage<>(
                            MessageType.LIST_ANSWERED_RESPONSE,
                            payload,
                            (Class) ArrayList.class
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
