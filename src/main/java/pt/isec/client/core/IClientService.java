package pt.isec.client.core;

import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Question;
import pt.isec.common.model.question.Answer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Abstraction over the client's networking resources.
 * <p>
 * Defines methods to manage the TCP connection, request/response queues,
 * authentication state and observable UI events (property changes).
 */
public interface IClientService {

    /**
     * Handles a lost connection event.
     * Typically triggers a reconnection flow.
     */
    void handleConnectionLost();

    // TCP streams and socket
    ObjectOutputStream getOutputStream();
    ObjectInputStream getInputStream();
    Socket getTcpSocket();

    // Request/response queues
    BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue();
    BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue();

    // Execution state
    boolean isRunning();

    // Authentication state setters
    void setUserId(Integer id);
    void setStudentNumber(Integer number);
    void setUserType(String t);
    void setUserEmail(String e);
    void setAuthenticated(boolean authenticated);
    void setUserName(String name);

    // Authentication state getters
    Integer getUserId();
    Integer getStudentNumber();
    String getUserType();
    String getUserEmail();
    String getUserName();
    boolean isAuthenticated();

    // Authentication events
    void setPropLoginOk(AuthResponseDTO dto);
    void setPropError(String s);
    void setPropRegisterOk(AuthResponseDTO dto);

    // Question/answer events
    void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto);
    void setPropEditQuestionResponse(String message);
    void setPropListQuestionsResponse(List<Question> questions);
    void setPropJoinQuestionResponse(Question question);
    void setPropSubmitAnswerOk(String message);
    void setPropSubmitAnswerFail(String message);
    void setPropViewAnswersResponse(List<Answer> answers);
    void setPropListAnsweredResponse(List<Answer> answers);

    /**
     * Notification to the teacher that an answer was submitted.
     * Payload: questionId ({@link Integer}).
     */
    void setPropAnswerSubmitted(Integer questionId);

    /**
     * Response to question deletion (ack/nack).
     * Payload: message string.
     */
    void setPropDeleteQuestionResponse(String message);

    /**
     * Response to profile update (success).
     * Payload: {@link AuthResponseDTO}.
     */
    void setPropUpdateProfileOk(AuthResponseDTO dto);

    /**
     * Response to profile update (failure).
     * Payload: error message.
     */
    void setPropUpdateProfileFail(String message);
}
