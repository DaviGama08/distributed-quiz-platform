package pt.isec.client.services;

import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.server.model.question.Question;
import pt.isec.server.model.question.Answer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Interface de acesso aos recursos de rede do cliente.
 * Define métodos para gerir a ligação TCP, as filas de pedidos e respostas,
 * o estado de autenticação e os eventos de UI (property changes).
 */
public interface IClientService {
    // Lida com uma perda de ligação
    void handleConnectionLost();

    // Streams e socket TCP
    ObjectOutputStream getOutputStream();
    ObjectInputStream getInputStream();
    Socket getTcpSocket();

    // Filas de pedidos e respostas
    BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue();
    BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue();

    // Estado de execução
    boolean isRunning();

    // Estado de autenticação
    void setUserId(Integer id);
    void setUserType(String t);
    void setUserEmail(String e);
    void setAuthenticated(boolean authenticated);

    Integer getUserId();
    String getUserType();
    String getUserEmail();
    boolean isAuthenticated();

    // Eventos (property changes) para autenticação
    void setPropLoginOk(AuthResponseDTO dto);
    void setPropError(String s);
    void setPropRegisterOk(AuthResponseDTO dto);

    // Eventos para perguntas e respostas
    void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto);
    void setPropListQuestionsResponse(List<Question> questions);
    void setPropJoinQuestionResponse(Question question);
    void setPropSubmitAnswerOk(String message);
    void setPropSubmitAnswerFail(String message);
    void setPropViewAnswersResponse(List<Answer> answers);
    void setPropListAnsweredResponse(List<Answer> answers);
}
