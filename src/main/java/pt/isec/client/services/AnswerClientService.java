package pt.isec.client.services;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.model.question.Answer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Serviço especializado em submissão e consulta de respostas.
 */
public class AnswerClientService {

    private final ClientService service;

    public AnswerClientService(ClientService service) {
        this.service = service;
    }

    /**
     * Aguarda a próxima mensagem relevante ignorando ACKs.
     */
    private Message<? extends Serializable> awaitRelevantResponse(long timeoutSecs) {
        try {
            while (true) {
                Message<? extends Serializable> resp =
                        service.waitForResponse(timeoutSecs);
                if (resp == null) {
                    return null;
                }
                if (resp.getType() == MessageType.ACK) {
                    continue;
                }
                return resp;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Submete uma resposta de um estudante.
     */
    public boolean submitAnswer(SubmitAnswerDTO dto) {
        service.sendMessage(new Message<>(MessageType.SUBMIT_ANSWER, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AnswerClientService] Timeout ao submeter resposta.");
            return false;
        }
        if (resp.getType() == MessageType.SUBMIT_OK) {
            return true;
        } else if (resp.getType() == MessageType.SUBMIT_FAIL || resp.getType() == MessageType.ERROR) {
            System.err.println("[AnswerClientService] Falha ao submeter resposta: " + resp.getData());
            return false;
        } else {
            System.err.println("[AnswerClientService] Resposta inesperada: " + resp.getType());
            return false;
        }
    }

    /**
     * Lista respostas de uma pergunta (vista de docente).
     */
    public List<Answer> viewAnswersForTeacher(ViewAnswersDTO dto) {
        service.sendMessage(new Message<>(MessageType.VIEW_ANSWERS, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AnswerClientService] Timeout ao obter respostas para docente.");
            return null;
        }
        return switch (resp.getType()) {
            case VIEW_ANSWERS_RESPONSE -> {
                Serializable data = resp.getData();
                if (data instanceof ArrayList<?> list) {
                    yield (List<Answer>) list;
                } else {
                    System.err.println("[AnswerClientService] Formato inválido em VIEW_ANSWERS_RESPONSE.");
                    yield null;
                }
            }
            case ERROR -> {
                System.err.println("[AnswerClientService] Erro ao obter respostas para docente: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[AnswerClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }

    /**
     * Lista o histórico de respostas de um estudante (perguntas expiradas).
     */
    public List<Answer> viewAnswersForStudent(Integer studentId) {
        service.sendMessage(new Message<>(MessageType.LIST_ANSWERED_QUESTIONS, studentId));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AnswerClientService] Timeout ao obter histórico de respostas.");
            return null;
        }
        return switch (resp.getType()) {
            case LIST_ANSWERED_RESPONSE -> {
                Serializable data = resp.getData();
                if (data instanceof ArrayList<?> list) {
                    yield (List<Answer>) list;
                } else {
                    System.err.println("[AnswerClientService] Formato inválido em LIST_ANSWERED_RESPONSE.");
                    yield null;
                }
            }
            case ERROR -> {
                System.err.println("[AnswerClientService] Erro ao obter histórico de respostas: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[AnswerClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }
}
