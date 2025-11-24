package pt.isec.client.threads;

import pt.isec.client.core.IClientService;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread que processa as respostas do servidor da fila de respostas
 * e executa a lógica apropriada para cada tipo de mensagem.
 *
 * Esta classe delega no IClientService a emissão de eventos (propriedades)
 * para que os controladores da UI actualizem a interface conforme necessário.
 */
public class ResponseHandlerThread implements Runnable{
    private final IClientService tInfo;

    public ResponseHandlerThread(IClientService tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        System.out.println("[ResponseHandler] Started processing responses...");

        while(tInfo.isRunning()) {
            try {
                TcpMessage<? extends Serializable> response = tInfo.getResponseQueue().take();
                processResponse(response);
            } catch (InterruptedException e) {
                System.out.println("[ResponseHandler] Interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[ResponseHandler] Stopped");
    }

    private void processResponse(TcpMessage<? extends Serializable> response) {
        MessageType type = response.getType();

        System.out.println("[ResponseHandler] Processing: " + type);

        switch(type) {
            /* ===== AUTENTICAÇÃO ===== */
            case LOGIN_OK -> {
                System.out.println("[ResponseHandler] Login successful!");
                if(response.getData() instanceof AuthResponseDTO dto)
                    tInfo.setPropLoginOk(dto);
            }
            case LOGIN_FAIL -> {
                System.err.println("[ResponseHandler] Login fail: " + response.getData());
                if(response.getData() instanceof String s)
                    tInfo.setPropError(s);
            }
            case REGISTER_OK -> {
                System.out.println("[ResponseHandler] Register successful!");
                if(response.getData() instanceof AuthResponseDTO dto)
                    tInfo.setPropRegisterOk(dto);
            }

            /* ===== ACK/NACK/ERROR genéricos ===== */
            case ACK -> {
                System.out.println("[ResponseHandler] Operation acknowledged");
                // detectar respostas específicas a operações (por convenção: edit-ok, delete-ok)
                if (response.getData() instanceof String s) {
                    if ("edit-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropEditQuestionResponse("edit-ok");
                    } else if ("delete-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-ok");
                    } else if ("update-ok".equalsIgnoreCase(s) || "update-profile-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropUpdateProfileResponse("ok");
                    }
                }
            }
            case NACK -> {
                System.err.println("[ResponseHandler] Operation failed: " + response.getData());
                if (response.getData() instanceof String s) {
                    if ("invalid-code".equalsIgnoreCase(s)) {
                        // notifica especificamente o join de pergunta falhado
                        tInfo.setPropJoinQuestionResponse(null);
                    } else if ("edit-fail".equalsIgnoreCase(s)) {
                        // notifica falha na edição
                        tInfo.setPropEditQuestionResponse("edit-fail");
                    } else if ("delete-fail".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-fail");
                    } else {
                        // fallback: trata como resposta de update profile fail
                        tInfo.setPropUpdateProfileResponse(s);
                    }
                }
            }
            case ERROR -> {
                System.err.println("[ResponseHandler] Server error: " + response.getData());
                if(response.getData() instanceof String s)
                    tInfo.setPropError(s);
            }

            case PONG -> System.out.println("[ResponseHandler] Pong received");

            /* ===== OPERAÇÕES DE PERGUNTAS ===== */

            case CREATE_QUESTION_RESPONSE -> {
                System.out.println("[ResponseHandler] New question created");
                CreateQuestionResponseDTO dto = response.getDataAs(CreateQuestionResponseDTO.class);
                tInfo.setPropCreateQuestionResponse(dto);
            }

            case LIST_QUESTIONS_RESPONSE -> {
                System.out.println("[ResponseHandler] Questions list received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    // suprime warning de cast inseguro
                    List<Question> qList = (List<Question>) list;
                    tInfo.setPropListQuestionsResponse(qList);
                }
            }

            case QUESTION_DETAILS -> {
                System.out.println("[ResponseHandler] Question details received");
                Question q = response.getDataAs(Question.class);
                tInfo.setPropJoinQuestionResponse(q);
            }

            /* ===== OPERAÇÕES DE RESPOSTAS ===== */

            case SUBMIT_OK -> {
                System.out.println("[ResponseHandler] Answer submitted successfully");
                String msg = response.getData() instanceof String s ? s : "Resposta submetida com sucesso!";
                tInfo.setPropSubmitAnswerOk(msg);
            }

            case SUBMIT_FAIL -> {
                System.err.println("[ResponseHandler] Failed to submit answer");
                String msg = response.getData() instanceof String s ? s : "Submissão da resposta sem sucesso!";
                tInfo.setPropSubmitAnswerFail(msg);
            }

            case ANSWER_SUBMITTED -> {
                System.out.println("[ResponseHandler] Notification: answer submitted");
                // payload expected to be Integer questionId
                Object d = response.getData();
                if (d instanceof Integer qid) {
                    tInfo.setPropAnswerSubmitted(qid);
                } else if (d instanceof String s) {
                    try { tInfo.setPropAnswerSubmitted(Integer.parseInt(s)); } catch (Exception ignored) {}
                }
            }

            case VIEW_ANSWERS_RESPONSE -> {
                System.out.println("[ResponseHandler] Answers received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    List<Answer> answers = (List<Answer>) list;
                    tInfo.setPropViewAnswersResponse(answers);
                }
            }

            case LIST_ANSWERED_RESPONSE -> {
                System.out.println("[ResponseHandler] Answered questions history received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    List<Answer> answers = (List<Answer>) list;
                    tInfo.setPropListAnsweredResponse(answers);
                }
            }

            default -> System.out.println("[ResponseHandler] Unhandled message type: " + type);
        }
    }
}
