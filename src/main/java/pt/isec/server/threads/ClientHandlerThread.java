package pt.isec.server.threads;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.*;
import pt.isec.common.dto.question.*;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.IQuizServer;
import pt.isec.server.NetworkConnection;
import pt.isec.server.model.question.Question;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread responsável por tratar a comunicação com um cliente.
 */
public class ClientHandlerThread implements Runnable {

    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;
    private static final Duration NO_TIMEOUT = Duration.ZERO;

    private final IQuizServer tInfo;
    private final NetworkConnection connection;

    public ClientHandlerThread(IQuizServer tInfo, NetworkConnection connection) {
        this.tInfo = tInfo;
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

            // se o servidor não for primário, rejeita
            if (!tInfo.isPrimary()) {
                connection.sendMessage(new Message<>(MessageType.NACK, "not-primary"));
                return;
            }

            // ligação aceite
            connection.sendMessage(new Message<>(MessageType.ACK, "ok"));
            connection.setReadTimeout(NO_TIMEOUT);

            while (tInfo.isRunning()) {
                Message<?> msg = connection.receiveMessage();
                if (msg == null)
                    break;
                processMessage(msg);
            }
        } catch (Exception e) {
            System.err.println("Client connection closed with exception: " + e.getMessage());
            e.printStackTrace(); // <-- Adicione isto para ver a stack trace completa
        }finally{
            try {
                connection.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processMessage(Message<?> message) throws Exception {
        if (message == null) return;

        switch (message.getType()) {

            /* ========= AUTENTICAÇÃO ========= */

            case REGISTER_STUDENT -> {
                try {
                    RegisterStudentDTO dto = message.getDataAs(RegisterStudentDTO.class);
                    LoginResponseDTO res = tInfo.getAuthService().registerStudent(dto);
                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case REGISTER_TEACHER -> {
                try {
                    RegisterTeacherDTO dto = message.getDataAs(RegisterTeacherDTO.class);
                    LoginResponseDTO res = tInfo.getAuthService().registerTeacher(dto);
                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LOGIN -> {
                try {
                    LoginRequestDTO dto = message.getDataAs(LoginRequestDTO.class);
                    LoginResponseDTO res = tInfo.getAuthService().login(dto);
                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LOGOUT -> {
                // sem gestão real de sessão para já
                connection.sendMessage(new Message<>(MessageType.ACK, "logout-ok", String.class));
            }

            /* ========= PERGUNTAS (DOCENTE) ========= */

            case CREATE_QUESTION -> {
                try {
                    CreateQuestionDTO dto = message.getDataAs(CreateQuestionDTO.class);
                    CreateQuestionResponseDTO res = tInfo.getQuestionService().createQuestion(dto);
                    connection.sendMessage(new Message<>(MessageType.CREATE_QUESTION_RESPONSE, res,
                            CreateQuestionResponseDTO.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case EDIT_QUESTION -> {
                try {
                    EditQuestionDTO dto = message.getDataAs(EditQuestionDTO.class);
                    boolean ok = tInfo.getQuestionService().editQuestion(dto);
                    connection.sendMessage(new Message<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "edit-ok" : "edit-fail",
                            String.class
                    ));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case DELETE_QUESTION -> {
                try {
                    DeleteQuestionDTO dto = message.getDataAs(DeleteQuestionDTO.class);
                    boolean ok = tInfo.getQuestionService().deleteQuestion(dto);
                    connection.sendMessage(new Message<>(
                            ok ? MessageType.ACK : MessageType.NACK,
                            ok ? "delete-ok" : "delete-fail",
                            String.class
                    ));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LIST_QUESTIONS -> {
                try {
                    ListQuestionsDTO dto = message.getDataAs(ListQuestionsDTO.class);
                    List<?> list = tInfo.getQuestionService().listQuestions(dto);
                    ArrayList<?> payload = new ArrayList<>(list);
                    Message<ArrayList<?>> out = new Message<>(
                            MessageType.LIST_QUESTIONS_RESPONSE,
                            payload,
                            (Class) ArrayList.class
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= PERGUNTAS (ALUNO) ========= */

            case JOIN_QUESTION -> {
                try {
                    JoinQuestionDTO dto = message.getDataAs(JoinQuestionDTO.class);
                    Question q = tInfo.getQuestionService().joinQuestion(dto);
                    if (q == null)
                        connection.sendMessage(new Message<>(MessageType.NACK, "invalid-code", String.class));
                    else
                        connection.sendMessage(new Message<>(MessageType.QUESTION_DETAILS, q, Question.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= RESPOSTAS ========= */

            case SUBMIT_ANSWER -> {
                try {
                    SubmitAnswerDTO dto = message.getDataAs(SubmitAnswerDTO.class);
                    boolean ok = tInfo.getAnswerService().submitAnswer(dto);
                    connection.sendMessage(new Message<>(
                            ok ? MessageType.SUBMIT_OK : MessageType.SUBMIT_FAIL,
                            ok ? "answer-ok" : "answer-fail",
                            String.class
                    ));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case VIEW_ANSWERS -> {
                try {
                    ViewAnswersDTO dto = message.getDataAs(ViewAnswersDTO.class);
                    List<?> list = tInfo.getAnswerService().viewAnswers(dto);
                    ArrayList<?> payload = new ArrayList<>(list);
                    Message<ArrayList<?>> out = new Message<>(
                            MessageType.VIEW_ANSWERS_RESPONSE,
                            payload,
                            (Class) ArrayList.class
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            case LIST_ANSWERED_QUESTIONS -> {
                try {
                    Integer studentId = message.getDataAs(Integer.class);
                    List<?> list = tInfo.getAnswerService().getStudentHistory(studentId);
                    ArrayList<?> payload = new ArrayList<>(list);
                    Message<ArrayList<?>> out = new Message<>(
                            MessageType.LIST_ANSWERED_RESPONSE,
                            payload,
                            (Class) ArrayList.class
                    );
                    connection.sendMessage(out);
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }

            /* ========= DEFAULT ========= */

            default -> connection.sendMessage(
                    new Message<>(MessageType.ERROR, "Tipo de mensagem não suportado", String.class)
            );
        }
    }
}
