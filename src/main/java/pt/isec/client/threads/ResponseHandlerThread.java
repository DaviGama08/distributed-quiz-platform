package pt.isec.client.threads;

import pt.isec.client.core.IClientService;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread that processes responses from the server taken from the response queue
 * and executes the appropriate logic for each message type.
 * <p>
 * This class delegates to {@link IClientService} to fire property change events
 * so that UI controllers can update the interface accordingly.
 */
public class ResponseHandlerThread implements Runnable {

    private final IClientService tInfo;

    /**
     * Creates a new response handler thread.
     *
     * @param tInfo client service interface
     */
    public ResponseHandlerThread(IClientService tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        Log.info(ResponseHandlerThread.class, "Started processing responses...");

        while (tInfo.isRunning()) {
            try {
                TcpMessage<? extends Serializable> response = tInfo.getResponseQueue().take();
                processResponse(response);
            } catch (InterruptedException e) {
                Log.warn(ResponseHandlerThread.class, "Interrupted while processing responses");
                Thread.currentThread().interrupt();
                break;
            }
        }

        Log.info(ResponseHandlerThread.class, "Stopped processing responses");
    }

    /**
     * Processes a single response, delegating to {@link IClientService}
     * to emit the proper property events.
     *
     * @param response TCP message received from the server
     */
    @SuppressWarnings("unchecked")
    private void processResponse(TcpMessage<? extends Serializable> response) {
        MessageType type = response.getType();

        Log.info(ResponseHandlerThread.class, "Processing: " + type);

        switch (type) {
            /* ===== AUTHENTICATION ===== */
            case LOGIN_OK -> {
                Log.info(ResponseHandlerThread.class, "Login successful");
                if (response.getData() instanceof AuthResponseDTO dto) {
                    tInfo.setPropLoginOk(dto);
                }
            }
            case LOGIN_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Login failed: " + response.getData());
                if (response.getData() instanceof String s) {
                    tInfo.setPropError(s);
                }
            }
            case REGISTER_OK -> {
                Log.info(ResponseHandlerThread.class, "Register successful");
                if (response.getData() instanceof AuthResponseDTO dto) {
                    tInfo.setPropRegisterOk(dto);
                }
            }

            /* ===== ACK/NACK/ERROR generic ===== */
            case ACK -> {
                Log.info(ResponseHandlerThread.class, "Operation acknowledged");
                if (response.getData() instanceof String s) {
                    if ("edit-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropEditQuestionResponse("edit-ok");
                    } else if ("delete-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-ok");
                    }
                }
            }
            case NACK -> {
                Log.error(ResponseHandlerThread.class, "Operation failed: " + response.getData());
                if (response.getData() instanceof String s) {
                    if ("invalid-code".equalsIgnoreCase(s)) {
                        tInfo.setPropJoinQuestionResponse(null);
                    } else if ("edit-fail".equalsIgnoreCase(s)) {
                        tInfo.setPropEditQuestionResponse("edit-fail");
                    } else if ("delete-fail".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-fail");
                    }
                }
            }
            case ERROR -> {
                Log.error(ResponseHandlerThread.class, "Server error: " + response.getData());
                if (response.getData() instanceof String s) {
                    tInfo.setPropError(s);
                }
            }

            case PONG -> Log.info(ResponseHandlerThread.class, "Pong received");

            /* ===== QUESTION OPERATIONS ===== */
            case CREATE_QUESTION_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "New question created");
                CreateQuestionResponseDTO dto = response.getDataAs(CreateQuestionResponseDTO.class);
                tInfo.setPropCreateQuestionResponse(dto);
            }

            case LIST_QUESTIONS_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Questions list received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    List<Question> qList = (List<Question>) list;
                    tInfo.setPropListQuestionsResponse(qList);
                }
            }

            case QUESTION_DETAILS -> {
                Log.info(ResponseHandlerThread.class, "Question details received");
                Question q = response.getDataAs(Question.class);
                tInfo.setPropJoinQuestionResponse(q);
            }

            /* ===== ANSWER OPERATIONS ===== */
            case SUBMIT_OK -> {
                Log.info(ResponseHandlerThread.class, "Answer submitted successfully");
                String msg = response.getData() instanceof String s ? s : "Resposta submetida com sucesso!";
                tInfo.setPropSubmitAnswerOk(msg);
            }

            case SUBMIT_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Failed to submit answer");
                String msg = response.getData() instanceof String s ? s : "Submissão da resposta sem sucesso!";
                tInfo.setPropSubmitAnswerFail(msg);
            }

            case ANSWER_SUBMITTED -> {
                Log.info(ResponseHandlerThread.class, "Notification: answer submitted");
                Object d = response.getData();
                if (d instanceof Integer qid) {
                    tInfo.setPropAnswerSubmitted(qid);
                } else if (d instanceof String s) {
                    try {
                        tInfo.setPropAnswerSubmitted(Integer.parseInt(s));
                    } catch (Exception ignored) {
                    }
                }
            }

            case VIEW_ANSWERS_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Answers received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    List<Answer> answers = (List<Answer>) list;
                    tInfo.setPropViewAnswersResponse(answers);
                }
            }

            case LIST_ANSWERED_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Answered questions history received");
                Serializable data = response.getData();
                if (data instanceof ArrayList<?> list) {
                    List<Answer> answers = (List<Answer>) list;
                    tInfo.setPropListAnsweredResponse(answers);
                }
            }

            /* ===== PROFILE ===== */
            case UPDATE_PROFILE_OK -> {
                Log.info(ResponseHandlerThread.class, "Profile updated successfully");
                if (response.getData() instanceof AuthResponseDTO dto) {
                    tInfo.setPropUpdateProfileOk(dto);
                } else {
                    tInfo.setPropUpdateProfileOk(new AuthResponseDTO(null, null, null, null, "ok", null));
                }
            }

            case UPDATE_PROFILE_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Failed to update profile");
                String msg = response.getData() instanceof String s ? s : "Erro ao atualizar perfil";
                tInfo.setPropUpdateProfileFail(msg);
            }

            default -> Log.info(ResponseHandlerThread.class, "Unhandled message type: " + type);
        }
    }
}
