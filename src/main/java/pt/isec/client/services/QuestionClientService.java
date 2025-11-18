package pt.isec.client.services;

import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.EditQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.model.question.Option;
import pt.isec.server.model.question.OptionLetter;
import pt.isec.server.model.question.Question;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço de gestão de questões para professores e acesso de estudantes.
 */
public class QuestionClientService {
    private final ClientService clientService;
    public QuestionClientService(ClientService clientService) { this.clientService = clientService; }

    /**
     * Cria uma nova questão (apenas professores).
     */
    public CreateQuestionResponseDTO createQuestion(Integer teacherId, String statement,
                                                    List<Option> options,
                                                    OptionLetter correctOption,
                                                    LocalDateTime startAt,
                                                    LocalDateTime endAt) {
        // Constrói e envia a mensagem
        CreateQuestionDTO dto = new CreateQuestionDTO(statement, teacherId, options, correctOption, startAt, endAt);
        Message<CreateQuestionDTO> message = new Message<>(MessageType.CREATE_QUESTION, dto);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if (response.getType() == MessageType.CREATE_QUESTION_RESPONSE) {
                // servidor devolve o código de acesso aqui
                return response.getDataAs(CreateQuestionResponseDTO.class);
            } else if (response.getType() == MessageType.ERROR) {
                System.err.println("[QuestionClient] Falha ao criar pergunta: " + response.getData());
                return null;
            } else {
                System.err.println("[QuestionClient] Resposta inesperada: " + response.getType());
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }


    /**
     * Edita uma questão existente (apenas sem respostas).
     */
    public boolean editQuestion(Integer questionId, Integer teacherId, String statement, List<Option> options,
                                OptionLetter correctOption, LocalDateTime startAt, LocalDateTime endAt) {
        EditQuestionDTO dto = new EditQuestionDTO(questionId, teacherId, statement, options, correctOption, startAt, endAt);
        Message<EditQuestionDTO> message = new Message<>(MessageType.EDIT_QUESTION, dto);
        System.out.println("[QuestionClient] Editing question " + questionId);
        clientService.sendMessage(message);
        try {
            Message<?> response = clientService.waitForResponse();
            if (response.getType() == MessageType.ACK) {
                System.out.println("[QuestionClient] Question edited successfully");
                return true;
            } else {
                System.err.println("[QuestionClient] Failed to edit: " + response.getType());
                return false;
            }
        } catch (InterruptedException e) {
            System.err.println("[QuestionClient] Edit interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Elimina uma questão (apenas sem respostas).
     */
    public boolean deleteQuestion(Integer questionId, Integer teacherId) {
        DeleteQuestionDTO dto = new DeleteQuestionDTO(questionId, teacherId);
        Message<DeleteQuestionDTO> message = new Message<>(MessageType.DELETE_QUESTION, dto);
        System.out.println("[QuestionClient] Deleting question " + questionId);
        clientService.sendMessage(message);
        try {
            Message<?> response = clientService.waitForResponse();
            if (response.getType() == MessageType.ACK) {
                System.out.println("[QuestionClient] Question deleted successfully");
                return true;
            } else {
                System.err.println("[QuestionClient] Failed to delete: " + response.getType());
                return false;
            }
        } catch (InterruptedException e) {
            System.err.println("[QuestionClient] Delete interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Lista questões de um professor.
     * @param filter "active", "future", "expired" ou null para todas
     */
    public List<Question> listQuestions(Integer teacherId, String filter) {
        ListQuestionsDTO dto = new ListQuestionsDTO(teacherId, filter);
        Message<ListQuestionsDTO> message = new Message<>(MessageType.LIST_QUESTIONS, dto);
        System.out.println("[QuestionClient] Listing questions with filter: " + filter);
        clientService.sendMessage(message);
        try {
            Message<?> response = clientService.waitForResponse();
            if (response.getType() == MessageType.LIST_QUESTIONS_RESPONSE) {
                @SuppressWarnings("unchecked")
                List<Question> questions = (List<Question>) response.getData();
                System.out.println("[QuestionClient] Received " + questions.size() + " questions");
                return questions;
            } else {
                System.err.println("[QuestionClient] Failed to list questions: " + response.getType());
                return List.of();
            }
        } catch (InterruptedException e) {
            System.err.println("[QuestionClient] List interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * Acessa uma questão pelo código (estudantes).
     */
    public Question joinQuestion(String accessCode, Integer studentId) {
        JoinQuestionDTO dto = new JoinQuestionDTO(accessCode, studentId);
        Message<JoinQuestionDTO> message = new Message<>(MessageType.JOIN_QUESTION, dto);
        System.out.println("[QuestionClient] Joining question with code: " + accessCode);
        clientService.sendMessage(message);
        try {
            Message<?> response = clientService.waitForResponse();
            if (response.getType() == MessageType.QUESTION_DETAILS) {
                Question question = response.getDataAs(Question.class);
                System.out.println("[QuestionClient] Question accessed: " + question.getStatement());
                return question;
            } else {
                System.err.println("[QuestionClient] Failed to join question: " + response.getType());
                return null;
            }
        } catch (InterruptedException e) {
            System.err.println("[QuestionClient] Join interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
