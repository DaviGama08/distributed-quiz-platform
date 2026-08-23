package pt.isec.client.services;

import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.core.IClientServiceContext;
import pt.isec.common.dto.question.*;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;

/**
 * Client-side service for question-related operations.
 * <p>
 * All methods enqueue a request message on the {@link IClientControllerContext} request queue
 * and return immediately. Responses are processed by {@code ResponseHandlerThread}
 * and forwarded via property change events in {@code ClientService}.
 */
@SuppressWarnings("ClassCanBeRecord")
public class QuestionClientService {

    private final IClientServiceContext service;

    /**
     * Creates a new question client service.
     *
     * @param service underlying client service
     */
    public QuestionClientService(IClientServiceContext service) {
        this.service = service;
    }

    /**
     * Sends a request to create a new question.
     *
     * @param dto create question payload
     */
    public void createQuestion(CreateQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.CREATE_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a request to edit a question.
     *
     * @param dto edit question payload
     */
    public void editQuestion(EditQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.EDIT_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a request to delete a question.
     *
     * @param dto delete question payload
     */
    public void deleteQuestion(DeleteQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.DELETE_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a request to list questions for a given teacher.
     *
     * @param dto list questions payload
     */
    public void listQuestions(ListQuestionsDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.LIST_QUESTIONS, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a request for a student to join a question by access code.
     *
     * @param dto join question payload
     */
    public void joinQuestion(JoinQuestionDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.JOIN_QUESTION, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
