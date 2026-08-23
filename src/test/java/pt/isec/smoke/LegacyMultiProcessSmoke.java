package pt.isec.smoke;

import javafx.application.Application;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import pt.isec.client.ClientApplication;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.dto.auth.RegisterStudentDTO;
import pt.isec.common.dto.auth.RegisterTeacherDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.dto.question.StudentQuestionDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;
import pt.isec.directory.core.DirectoryManager;
import pt.isec.server.core.ServerManager;
import pt.isec.server.services.auth.PasswordHasher;
import pt.isec.server.threads.NetworkTcpConnection;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit, opt-in legacy gate. Run with:
 * {@code mvn -B -Dtest=LegacyMultiProcessSmoke test}.
 *
 * <p>The class name intentionally does not end in Test/IT, so the normal unit
 * suite remains fast and independent from local multicast and JavaFX support.</p>
 */
class LegacyMultiProcessSmoke {
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(6);
    private static final String PASSWORD = "LegacyGate-2026!";
    private static final String TEACHER_CODE = "LEGACY-GATE";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(90)
    void runsDirectoryThreeServersTwoClientsJavaFxAndFreshFailover() throws Exception {
        int directoryPort = reserveUdpPort();
        List<Integer> ports = reserveTcpPorts(6);
        List<NodeProcess> processes = new ArrayList<>();

        NodeProcess directory = startProcess(
                "directory",
                DirectoryProcess.class,
                temporaryDirectory.resolve("directory.log"),
                Integer.toString(directoryPort)
        );
        processes.add(directory);

        List<ServerNode> servers = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Path data = temporaryDirectory.resolve("server-" + (index + 1));
            Files.createDirectories(data);
            NodeProcess process = startProcess(
                    "server-" + (index + 1),
                    ServerProcess.class,
                    temporaryDirectory.resolve("server-" + (index + 1) + ".log"),
                    "127.0.0.1",
                    Integer.toString(directoryPort),
                    data.toString(),
                    "AUTO",
                    Integer.toString(ports.get(index * 2)),
                    Integer.toString(ports.get(index * 2 + 1))
            );
            processes.add(process);
            servers.add(new ServerNode(ports.get(index * 2), data, process));
        }

        try {
            Endpoint firstDiscovery = awaitPrincipal(directoryPort, null, Duration.ofSeconds(20));
            Endpoint secondDiscovery = awaitPrincipal(directoryPort, null, Duration.ofSeconds(5));
            assertEquals(firstDiscovery, secondDiscovery, "both clients must discover the same primary");

            ServerNode initialPrimary = serverAt(servers, firstDiscovery.port());
            Path primaryDatabase = awaitDatabase(initialPrimary.dataDirectory(), Duration.ofSeconds(10));
            configureTeacherCode(primaryDatabase);

            AuthResponseDTO teacherAuth;
            AuthResponseDTO studentAuth;
            int teacherId;
            int studentId;
            CreateQuestionResponseDTO created;

            try (ClientActor teacher = ClientActor.connect(firstDiscovery);
                 ClientActor student = ClientActor.connect(secondDiscovery)) {
                teacher.expect(MessageType.ACK);
                student.expect(MessageType.ACK);

                teacher.send(MessageType.REGISTER_TEACHER,
                        new RegisterTeacherDTO("Legacy Teacher", "teacher@legacy.test",
                                PASSWORD, TEACHER_CODE));
                teacher.expect(MessageType.REGISTER_OK);
                teacherAuth = teacher.login("teacher@legacy.test");
                teacherId = Integer.parseInt(teacherAuth.userId());

                student.send(MessageType.REGISTER_STUDENT,
                        new RegisterStudentDTO("Legacy Student", "student@legacy.test",
                                PASSWORD, 3_000_000_001L));
                student.expect(MessageType.REGISTER_OK);
                studentAuth = student.login("student@legacy.test");
                studentId = Integer.parseInt(studentAuth.userId());

                LocalDateTime now = LocalDateTime.now();
                teacher.send(MessageType.CREATE_QUESTION, new CreateQuestionDTO(
                        "Legacy gate question",
                        teacherId,
                        List.of(new Option(OptionLetter.A, "Alpha"),
                                new Option(OptionLetter.B, "Beta")),
                        OptionLetter.A,
                        now.plusSeconds(2),
                        now.plusMinutes(10)
                ));
                created = teacher.expect(MessageType.CREATE_QUESTION_RESPONSE)
                        .getDataAs(CreateQuestionResponseDTO.class);

                Thread.sleep(2_500);
                student.send(MessageType.JOIN_QUESTION,
                        new JoinQuestionDTO(created.accessCode(), studentId));
                StudentQuestionDTO question = student.expect(MessageType.QUESTION_DETAILS)
                        .getDataAs(StudentQuestionDTO.class);
                assertEquals(created.questionId(), question.id());

                student.send(MessageType.SUBMIT_ANSWER,
                        new SubmitAnswerDTO(question.id(), studentId, OptionLetter.B));
                student.expect(MessageType.SUBMIT_OK);

                student.send(MessageType.LIST_ANSWERED_QUESTIONS, studentId);
                List<Answer> beforeFailover = answers(
                        student.expect(MessageType.LIST_ANSWERED_RESPONSE).getData());
                assertEquals(1, beforeFailover.size());
                assertFalse(beforeFailover.getFirst().isResultAvailable(),
                        "correctness must remain hidden while the question is active");
                assertEquals(null, beforeFailover.getFirst().getCorrect());
            }

            // Start two real JavaFX client JVMs and require a clean window lifecycle.
            NodeProcess fxOne = startProcess(
                    "javafx-client-1", FxClientProcess.class,
                    temporaryDirectory.resolve("javafx-client-1.log"),
                    "--directory-host=127.0.0.1", "--directory-port=" + directoryPort
            );
            NodeProcess fxTwo = startProcess(
                    "javafx-client-2", FxClientProcess.class,
                    temporaryDirectory.resolve("javafx-client-2.log"),
                    "--directory-host=127.0.0.1", "--directory-port=" + directoryPort
            );
            processes.add(fxOne);
            processes.add(fxTwo);
            assertEquals(0, fxOne.awaitExit(Duration.ofSeconds(15)), fxOne.diagnostics());
            assertEquals(0, fxTwo.awaitExit(Duration.ofSeconds(15)), fxTwo.diagnostics());

            long committedVersion = databaseVersion(primaryDatabase);
            assertTrue(committedVersion > 0, "the primary must have committed replicated state");
            awaitCondition(Duration.ofSeconds(25), () -> servers.stream().allMatch(server -> {
                try {
                    Path database = singleDatabase(server.dataDirectory());
                    return database != null && databaseVersion(database) == committedVersion;
                } catch (Exception ignored) {
                    return false;
                }
            }), "all three servers must converge to db_version=" + committedVersion);

            // Let every backup advertise the installed version before removing the primary.
            Thread.sleep(5_500);
            initialPrimary.process().destroyForcibly();
            assertTrue(initialPrimary.process().awaitExit(Duration.ofSeconds(5)) != Integer.MIN_VALUE,
                    initialPrimary.process().diagnostics());

            Endpoint elected = awaitPrincipal(directoryPort, firstDiscovery, Duration.ofSeconds(12));
            assertNotEquals(firstDiscovery.port(), elected.port(), "failover must replace the dead primary");
            assertEquals(committedVersion, elected.dbVersion(), "the elected copy must be fresh");

            // Directory election precedes the elected node learning its role in
            // the next 5-second heartbeat response.
            Thread.sleep(5_500);

            try (ClientActor teacher = ClientActor.connect(elected);
                 ClientActor student = ClientActor.connect(elected)) {
                teacher.expect(MessageType.ACK);
                student.expect(MessageType.ACK);

                teacher.send(MessageType.RESUME_SESSION, teacherAuth.sessionId());
                teacher.expect(MessageType.RESUME_SESSION_OK);
                student.send(MessageType.RESUME_SESSION, studentAuth.sessionId());
                student.expect(MessageType.RESUME_SESSION_OK);

                teacher.send(MessageType.LIST_QUESTIONS, new ListQuestionsDTO(teacherId, null));
                List<Question> questions = questions(
                        teacher.expect(MessageType.LIST_QUESTIONS_RESPONSE).getData());
                assertTrue(questions.stream().anyMatch(q -> created.questionId().equals(q.getId())),
                        "the question must survive failover");

                student.send(MessageType.LIST_ANSWERED_QUESTIONS, studentId);
                List<Answer> afterFailover = answers(
                        student.expect(MessageType.LIST_ANSWERED_RESPONSE).getData());
                assertTrue(afterFailover.stream().anyMatch(answer ->
                                created.questionId().equals(answer.getQuestionId())
                                        && answer.getSelectedOption() == OptionLetter.B),
                        "the submitted answer must survive failover");
            }

            // Remaining nodes and the directory must honor a graceful STOP and exit.
            for (ServerNode server : servers) {
                if (server.process() != initialPrimary.process()) {
                    server.process().stopGracefully();
                    assertEquals(0, server.process().awaitExit(Duration.ofSeconds(10)),
                            server.process().diagnostics());
                }
            }
            directory.stopGracefully();
            assertEquals(0, directory.awaitExit(Duration.ofSeconds(5)), directory.diagnostics());

            System.out.printf(
                    "LEGACY_SMOKE_PASS directory=%d servers=%s initial=%s:%d elected=%s:%d dbVersion=%d%n",
                    directoryPort,
                    servers.stream().map(ServerNode::clientPort).toList(),
                    firstDiscovery.host(), firstDiscovery.port(),
                    elected.host(), elected.port(),
                    committedVersion
            );
        } finally {
            for (NodeProcess process : processes) {
                process.destroyForcibly();
            }
            for (NodeProcess process : processes) {
                process.awaitExit(Duration.ofSeconds(3));
            }
        }
    }

    public static final class DirectoryProcess {
        public static void main(String[] args) throws Exception {
            DirectoryManager directory = new DirectoryManager(
                    Integer.parseInt(args[0]), 1_024, 65_507, 4,
                    8_000, 250, 10_000
            );
            directory.run();
            System.in.read();
            directory.stop();
        }
    }

    public static final class ServerProcess {
        public static void main(String[] args) throws Exception {
            Class.forName("org.sqlite.JDBC");
            ServerManager server = new ServerManager(
                    args[0], Integer.parseInt(args[1]), args[3],
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]), Path.of(args[2])
            );
            server.run();
            System.in.read();
            server.close();
        }
    }

    public static final class FxClientProcess {
        public static void main(String[] args) {
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(4_000);
                    Platform.exit();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "javafx-smoke-closer");
            closer.setDaemon(true);
            closer.start();
            Application.launch(ClientApplication.class, args);
        }
    }

    private NodeProcess startProcess(String name, Class<?> mainClass, Path log, String... args)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        return new NodeProcess(name, process, log);
    }

    private static Endpoint awaitPrincipal(int directoryPort, Endpoint differentFrom, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        String last = "no response";
        while (System.nanoTime() < deadline) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(500);
                byte[] request = "TYPE=LOGIN".getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(request, request.length,
                        InetAddress.getLoopbackAddress(), directoryPort));
                byte[] data = new byte[4_096];
                DatagramPacket response = new DatagramPacket(data, data.length);
                socket.receive(response);
                last = new String(response.getData(), response.getOffset(), response.getLength(),
                        StandardCharsets.UTF_8);
                Endpoint endpoint = parseEndpoint(last);
                if (endpoint != null
                        && (differentFrom == null || endpoint.port() != differentFrom.port())) {
                    return endpoint;
                }
            } catch (SocketTimeoutException ignored) {
                last = "UDP timeout";
            }
            Thread.sleep(100);
        }
        throw new AssertionError("directory did not expose the expected primary: " + last);
    }

    private static Endpoint parseEndpoint(String response) {
        if (!response.startsWith("200 PRINCIPAL ")) {
            return null;
        }
        String body = response.substring("200 PRINCIPAL ".length());
        int versionSeparator = body.lastIndexOf("|DBV=");
        if (versionSeparator < 0) {
            return null;
        }
        long version = Long.parseLong(body.substring(versionSeparator + 5));
        String hostPort = body.substring(0, versionSeparator);
        int portSeparator = hostPort.lastIndexOf(':');
        return new Endpoint(
                hostPort.substring(0, portSeparator),
                Integer.parseInt(hostPort.substring(portSeparator + 1)),
                version
        );
    }

    private static void configureTeacherCode(Path database) throws Exception {
        String hash = PasswordHasher.hash(TEACHER_CODE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                     "UPDATE config SET teacher_code_hash = ? WHERE id = 1")) {
            statement.setString(1, hash);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static Path awaitDatabase(Path directory, Duration timeout) throws Exception {
        final Path[] result = new Path[1];
        awaitCondition(timeout, () -> {
            try {
                result[0] = singleDatabase(directory);
                return result[0] != null;
            } catch (Exception ignored) {
                return false;
            }
        }, "database was not created in " + directory);
        return result[0];
    }

    private static Path singleDatabase(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".db"))
                    .findFirst().orElse(null);
        }
    }

    private static long databaseVersion(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT db_version FROM config WHERE id = 1")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void awaitCondition(Duration timeout, BooleanSupplier condition, String failure)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(failure);
    }

    private static ServerNode serverAt(List<ServerNode> servers, int port) {
        return servers.stream().filter(server -> server.clientPort() == port).findFirst()
                .orElseThrow(() -> new AssertionError("unknown server port " + port));
    }

    private static int reserveUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static List<Integer> reserveTcpPorts(int count) throws Exception {
        Set<Integer> ports = new HashSet<>();
        while (ports.size() < count) {
            try (ServerSocket socket = new ServerSocket(0)) {
                ports.add(socket.getLocalPort());
            }
        }
        return List.copyOf(ports);
    }

    @SuppressWarnings("unchecked")
    private static List<Answer> answers(Object value) {
        assertTrue(value instanceof List<?>);
        return (List<Answer>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Question> questions(Object value) {
        assertTrue(value instanceof List<?>);
        return (List<Question>) value;
    }

    private record Endpoint(String host, int port, long dbVersion) {
    }

    private record ServerNode(int clientPort, Path dataDirectory, NodeProcess process) {
    }

    private record NodeProcess(String name, Process process, Path log) {
        void stopGracefully() throws Exception {
            if (process.isAlive()) {
                process.getOutputStream().write('\n');
                process.getOutputStream().flush();
            }
        }

        void destroyForcibly() {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        int awaitExit(Duration timeout) throws Exception {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return Integer.MIN_VALUE;
            }
            return process.exitValue();
        }

        String diagnostics() {
            try {
                String content = Files.exists(log) ? Files.readString(log) : "log unavailable";
                int start = Math.max(0, content.length() - 4_000);
                return name + " log:\n" + content.substring(start);
            } catch (Exception e) {
                return name + " diagnostics unavailable: " + e.getMessage();
            }
        }
    }

    private static final class ClientActor implements AutoCloseable {
        private final NetworkTcpConnection connection;

        private ClientActor(NetworkTcpConnection connection) throws Exception {
            this.connection = connection;
            connection.setReadTimeout(RECEIVE_TIMEOUT);
        }

        static ClientActor connect(Endpoint endpoint) throws Exception {
            return new ClientActor(NetworkTcpConnection.connect(
                    endpoint.host(), endpoint.port(), Duration.ofSeconds(5)));
        }

        AuthResponseDTO login(String email) throws Exception {
            send(MessageType.LOGIN, new LoginRequestDTO(email, PASSWORD));
            return expect(MessageType.LOGIN_OK).getDataAs(AuthResponseDTO.class);
        }

        <T extends java.io.Serializable> void send(MessageType type, T value) throws Exception {
            connection.sendMessage(new TcpMessage<>(type, value));
        }

        TcpMessage<?> expect(MessageType expected) throws Exception {
            for (int attempts = 0; attempts < 4; attempts++) {
                TcpMessage<?> message = connection.receiveMessage();
                assertNotNull(message);
                if (message.getType() == expected) {
                    return message;
                }
                if (message.getType() != MessageType.ANSWER_SUBMITTED) {
                    throw new AssertionError("expected " + expected + " but received "
                            + message.getType() + ": " + message.getData());
                }
            }
            throw new AssertionError("expected " + expected + " after notifications");
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }
    }
}
