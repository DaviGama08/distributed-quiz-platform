package pt.isec.client.services;

import pt.isec.common.dto.question.*;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;

/**
 * Serviço especializado em operações relacionadas com perguntas.
 *
 * Todos os métodos enfileiram uma mensagem de pedido e retornam
 * imediatamente. As respostas serão processadas pela ResponseHandlerThread
 * e notificadas via eventos (propriedades) do ClientService.
 */
public class QuestionClientService {

    private final IClientService service;

    public QuestionClientService(IClientService service) {
        this.service = service;
    }

    /** Envia um pedido para criar uma nova pergunta. */
    public void createQuestion(CreateQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.CREATE_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Envia um pedido para editar uma pergunta. */
    public void editQuestion(EditQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.EDIT_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Envia um pedido para eliminar uma pergunta. */
    public void deleteQuestion(DeleteQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.DELETE_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Envia um pedido para listar as perguntas de um docente. */
    public void listQuestions(ListQuestionsDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.LIST_QUESTIONS, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Envia um pedido para um estudante aderir a uma pergunta através de código. */
    public void joinQuestion(JoinQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.JOIN_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
