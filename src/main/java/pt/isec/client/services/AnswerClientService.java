package pt.isec.client.services;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.model.answer.Answer;
import pt.isec.server.model.common.OptionLetter;

import java.util.List;

/**
 * Serviço para gestão de respostas (submeter e visualizar)
 */
public class AnswerClientService {
    private final ClientService clientService;

    public AnswerClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Submete resposta de um estudante
     */
    public boolean submitAnswer(Integer questionId, Integer studentId, OptionLetter selectedOption) {
        SubmitAnswerDTO dto = new SubmitAnswerDTO(questionId, studentId, selectedOption);
        Message<SubmitAnswerDTO> message = new Message<>(MessageType.SUBMIT_ANSWER, dto);

        System.out.println("[AnswerClient] Submitting answer for question " + questionId);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.SUBMIT_OK) {
                System.out.println("[AnswerClient] Answer submitted successfully");
                return true;
            } else if(response.getType() == MessageType.SUBMIT_FAIL) {
                System.err.println("[AnswerClient] Failed to submit answer: " + response.getData());
                return false;
            } else {
                System.err.println("[AnswerClient] Unexpected response: " + response.getType());
                return false;
            }
        } catch (InterruptedException e) {
            System.err.println("[AnswerClient] Submit interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Visualiza respostas de uma questão (apenas professor e questão expirada)
     */
    public List<Answer> viewAnswers(Integer questionId, Integer teacherId) {
        ViewAnswersDTO dto = new ViewAnswersDTO(questionId, teacherId);
        Message<ViewAnswersDTO> message = new Message<>(MessageType.VIEW_ANSWERS, dto);

        System.out.println("[AnswerClient] Requesting answers for question " + questionId);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.VIEW_ANSWERS_RESPONSE) {
                @SuppressWarnings("unchecked")
                List<Answer> answers = (List<Answer>) response.getData();
                System.out.println("[AnswerClient] Received " + answers.size() + " answers");
                return answers;
            } else {
                System.err.println("[AnswerClient] Failed to get answers: " + response.getType());
                return List.of();
            }
        } catch (InterruptedException e) {
            System.err.println("[AnswerClient] View interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * Obtém histórico de questões respondidas (estudante)
     */
    public List<Answer> getStudentHistory(Integer studentId) {
        Message<Integer> message = new Message<>(MessageType.LIST_ANSWERED_QUESTIONS, studentId);

        System.out.println("[AnswerClient] Requesting history for student " + studentId);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.LIST_ANSWERED_RESPONSE) {
                @SuppressWarnings("unchecked")
                List<Answer> history = (List<Answer>) response.getData();
                System.out.println("[AnswerClient] Received history with " + history.size() + " entries");
                return history;
            } else {
                System.err.println("[AnswerClient] Failed to get history: " + response.getType());
                return List.of();
            }
        } catch (InterruptedException e) {
            System.err.println("[AnswerClient] History request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * Exporta resultados para CSV (professor)
     */
    public boolean exportResultsToCSV(Integer questionId, Integer teacherId, String filePath) {
        // TODO: Implementar quando servidor suportar
        System.out.println("[AnswerClient] Export to CSV not yet implemented");
        return false;
    }
}

