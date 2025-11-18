package pt.isec.client.services;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;

/**
 * Serviço especializado em operações relacionadas com respostas.
 *
 * Todos os métodos enfileiram uma mensagem de pedido. As respostas
 * serão tratadas pela ResponseHandlerThread e notificadas via
 * eventos do ClientService.
 */
public class AnswerClientService {

    private final IClientService service;

    public AnswerClientService(IClientService service) {
        this.service = service;
    }

    /** Submete a resposta de um estudante a uma pergunta. */
    public void submitAnswer(SubmitAnswerDTO dto) {
        try {
            service.getRequestQueue().put(new Message<>(MessageType.SUBMIT_ANSWER, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lista respostas submetidas a uma pergunta (vista do docente). */
    public void viewAnswersForTeacher(ViewAnswersDTO dto) {
        try {
            service.getRequestQueue().put(new Message<>(MessageType.VIEW_ANSWERS, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lista o histórico de respostas de um estudante (perguntas expiradas). */
    public void viewAnswersForStudent(Integer studentId) {
        try {
            service.getRequestQueue().put(new Message<>(MessageType.LIST_ANSWERED_QUESTIONS, studentId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
