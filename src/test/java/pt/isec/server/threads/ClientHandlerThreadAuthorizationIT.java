package pt.isec.server.threads;

import org.junit.jupiter.api.Test;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.ChangePasswordDTO;
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
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.db.DbCommands;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP-level integration tests for the authentication boundary owned by
 * {@link ClientHandlerThread}.
 */
class ClientHandlerThreadAuthorizationIT {

    @Test
    void rejectsUnauthenticatedTeacherOperation() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.send(new TcpMessage<>(MessageType.CREATE_QUESTION, createQuestion(101)));

            assertError(harness.receive(), "authentication-required");
            assertEquals(0, harness.context.questionService.createCalls.get());
        }
    }

    @Test
    void rejectsStudentUsingTeacherOperation() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginStudent();
            harness.send(new TcpMessage<>(MessageType.CREATE_QUESTION, createQuestion(202)));

            assertError(harness.receive(), "forbidden-role");
            assertEquals(0, harness.context.questionService.createCalls.get());
        }
    }

    @Test
    void rejectsTeacherUsingStudentOperation() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginTeacher();
            harness.send(new TcpMessage<>(
                    MessageType.SUBMIT_ANSWER,
                    new SubmitAnswerDTO(1, 101, OptionLetter.A)
            ));

            assertError(harness.receive(), "forbidden-role");
            assertEquals(0, harness.context.answerService.submitCalls.get());
        }
    }

    @Test
    void rejectsTeacherIdImpersonation() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginTeacher();
            harness.send(new TcpMessage<>(MessageType.CREATE_QUESTION, createQuestion(999)));

            TcpMessage<?> response = harness.receive();
            assertEquals(MessageType.CREATE_QUESTION_FAIL, response.getType());
            assertEquals("identity-mismatch", response.getDataAs(String.class));
            assertEquals(0, harness.context.questionService.createCalls.get());
        }
    }

    @Test
    void rejectsStudentIdImpersonationOnAnswerSubmission() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginStudent();
            harness.send(new TcpMessage<>(
                    MessageType.SUBMIT_ANSWER,
                    new SubmitAnswerDTO(1, 999, OptionLetter.A)
            ));

            assertError(harness.receive(), "identity-mismatch");
            assertEquals(0, harness.context.answerService.submitCalls.get());
        }
    }

    @Test
    void rejectsAnotherStudentsHistory() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginStudent();
            harness.send(new TcpMessage<>(MessageType.LIST_ANSWERED_QUESTIONS, 999));

            assertError(harness.receive(), "identity-mismatch");
            assertEquals(0, harness.context.answerService.historyCalls.get());
        }
    }

    @Test
    void rejectsSecondLoginOnAuthenticatedConnection() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginTeacher();
            harness.send(new TcpMessage<>(
                    MessageType.LOGIN,
                    new LoginRequestDTO("student@example.test", "password")
            ));

            assertError(harness.receive(), "already-authenticated");
            assertEquals(1, harness.context.authService.loginCalls.get());
        }
    }

    @Test
    void allowsTeacherOperationOnlyForAuthenticatedTeacherIdentity() throws Exception {
        try (HandlerHarness harness = new HandlerHarness()) {
            harness.loginTeacher();
            harness.send(new TcpMessage<>(MessageType.CREATE_QUESTION, createQuestion(101)));

            TcpMessage<?> response = harness.receive();
            assertEquals(MessageType.CREATE_QUESTION_RESPONSE, response.getType());
            assertEquals(1, harness.context.questionService.createCalls.get());
        }
    }

    private static CreateQuestionDTO createQuestion(int teacherId) {
        return new CreateQuestionDTO("Question", teacherId, null, null, null, null);
    }

    private static void assertError(TcpMessage<?> response, String reason) {
        assertEquals(MessageType.ERROR, response.getType());
        assertEquals(reason, response.getDataAs(String.class));
    }

    private static final class HandlerHarness implements AutoCloseable {
        private final FakeServerContext context = new FakeServerContext();
        private final ExecutorService connector = Executors.newSingleThreadExecutor();
        private final ServerSocket listener;
        private final NetworkTcpConnection client;
        private final Thread handlerThread;

        private HandlerHarness() throws Exception {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            listener = new ServerSocket(0, 1, loopback);
            Future<NetworkTcpConnection> serverConnection = connector.submit(
                    () -> new NetworkTcpConnection(listener.accept())
            );

            client = NetworkTcpConnection.connect(
                    loopback.getHostAddress(),
                    listener.getLocalPort(),
                    Duration.ofSeconds(2)
            );
            NetworkTcpConnection acceptedConnection = serverConnection.get(2, TimeUnit.SECONDS);
            listener.close();
            client.setReadTimeout(Duration.ofSeconds(2));

            handlerThread = new Thread(
                    new ClientHandlerThread(context, acceptedConnection),
                    "client-handler-authorization-test"
            );
            handlerThread.setDaemon(true);
            handlerThread.start();

            TcpMessage<?> handshake = receive();
            assertEquals(MessageType.ACK, handshake.getType());
        }

        private void loginTeacher() throws Exception {
            login("teacher@example.test", "TEACHER");
        }

        private void loginStudent() throws Exception {
            login("student@example.test", "STUDENT");
        }

        private void login(String email, String expectedRole) throws Exception {
            send(new TcpMessage<>(MessageType.LOGIN, new LoginRequestDTO(email, "password")));
            TcpMessage<?> response = receive();
            assertEquals(MessageType.LOGIN_OK, response.getType());
            AuthResponseDTO auth = response.getDataAs(AuthResponseDTO.class);
            assertEquals(expectedRole, auth.userType());
        }

        private void send(TcpMessage<?> message) throws Exception {
            client.sendMessage(message);
        }

        private TcpMessage<?> receive() throws Exception {
            return client.receiveMessage();
        }

        @Override
        public void close() throws Exception {
            context.running.set(false);
            client.close();
            handlerThread.join(2_000);
            assertTrue(!handlerThread.isAlive(), "handler thread did not terminate");
            listener.close();
            connector.shutdownNow();
        }
    }

    private static final class FakeServerContext implements IServerThreadContext {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final FakeAuthService authService = new FakeAuthService();
        private final FakeQuestionService questionService = new FakeQuestionService();
        private final FakeAnswerService answerService = new FakeAnswerService();
        private final BlockingQueue<List<String>> replicationQueue = new LinkedBlockingQueue<>();

        @Override public String id() { return "test-server"; }
        @Override public String serverTcpIp() { return InetAddress.getLoopbackAddress().getHostAddress(); }
        @Override public int serverTcpPort() { return 0; }
        @Override public int dbCopyPort() { return 0; }
        @Override public String directoryHost() { return InetAddress.getLoopbackAddress().getHostAddress(); }
        @Override public int directoryPort() { return 0; }
        @Override public String multicastGroup() { return ""; }
        @Override public int multicastPort() { return 0; }
        @Override public NetworkInterface multicastInterface() { return null; }
        @Override public BlockingQueue<List<String>> queue() { return replicationQueue; }
        @Override public boolean isRunning() { return running.get(); }
        @Override public void shutdownServer() { running.set(false); }
        @Override public boolean isPrimary() { return true; }
        @Override public void setPrimary(String ip, int port) { }
        @Override public long dbVersion() { return 0; }
        @Override public void setDbVersion(long v) { }
        @Override public Path dbPath() { return null; }
        @Override public void initDatabaseLayerIfNeeded() { }
        @Override public DbCommands getDb() { return null; }
        @Override public boolean tryLockCopy() { return true; }
        @Override public void unlockCopy() { }
        @Override public IAuthService getAuthService() { return authService; }
        @Override public IQuestionService getQuestionService() { return questionService; }
        @Override public IAnswerService getAnswerService() { return answerService; }
        @Override public void registerClientConnection(long userId, NetworkTcpConnection conn) { }
        @Override public void unregisterClientConnection(long userId) { }
    }

    private static final class FakeAuthService implements IAuthService {
        private final AtomicInteger loginCalls = new AtomicInteger();

        @Override public void invalidateSession(long userId, String sessionId, String typeUser,
                                                String name, String email) { }

        @Override
        public AuthResponseDTO registerTeacher(RegisterTeacherDTO dto) {
            return auth(101, "TEACHER");
        }

        @Override
        public AuthResponseDTO registerStudent(RegisterStudentDTO dto) {
            return auth(202, "STUDENT");
        }

        @Override
        public AuthResponseDTO login(LoginRequestDTO dto) {
            loginCalls.incrementAndGet();
            return dto.email().startsWith("teacher")
                    ? auth(101, "TEACHER")
                    : auth(202, "STUDENT");
        }

        @Override public AuthResponseDTO updateStudent(UpdateStudentDTO dto) { return auth(202, "STUDENT"); }
        @Override public AuthResponseDTO updateTeacher(UpdateTeacherDTO dto) { return auth(101, "TEACHER"); }
        @Override public AuthResponseDTO resumeSession(String sessionId) { return auth(101, "TEACHER"); }
        @Override public void changePassword(ChangePasswordDTO dto) { }

        private static AuthResponseDTO auth(long id, String role) {
            return new AuthResponseDTO(
                    "session-" + id,
                    Long.toString(id),
                    "STUDENT".equals(role) ? 202_000L : null,
                    role,
                    role + " name",
                    role.toLowerCase() + "@example.test"
            );
        }
    }

    private static final class FakeQuestionService implements IQuestionService {
        private final AtomicInteger createCalls = new AtomicInteger();

        @Override
        public CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) {
            createCalls.incrementAndGet();
            return new CreateQuestionResponseDTO(1, "ABC123");
        }

        @Override public boolean editQuestion(EditQuestionDTO dto) { return true; }
        @Override public boolean deleteQuestion(DeleteQuestionDTO dto) { return true; }
        @Override public List<Question> listQuestions(ListQuestionsDTO dto) { return List.of(); }
        @Override public StudentQuestionDTO joinQuestion(JoinQuestionDTO dto) { return null; }
    }

    private static final class FakeAnswerService implements IAnswerService {
        private final AtomicInteger submitCalls = new AtomicInteger();
        private final AtomicInteger historyCalls = new AtomicInteger();

        @Override
        public boolean submitAnswer(SubmitAnswerDTO dto) {
            submitCalls.incrementAndGet();
            return true;
        }

        @Override public List<Answer> viewAnswers(ViewAnswersDTO dto) { return List.of(); }

        @Override
        public List<Answer> getStudentHistory(Integer studentId) {
            historyCalls.incrementAndGet();
            return List.of();
        }
    }
}
