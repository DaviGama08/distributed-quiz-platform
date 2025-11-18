package pt.isec.client.services;

import pt.isec.common.dto.question.*;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;
import pt.isec.server.model.question.Question;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Serviço especializado em operações relacionadas com perguntas.
 */
public class QuestionClientService {

    private final ClientService service;

    public QuestionClientService(ClientService service) {
        this.service = service;
    }

    /**
     * Aguarda a próxima mensagem relevante, ignorando ACKs, devolvendo-a
     * ou null em caso de timeout.
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
     * Cria uma nova pergunta.
     * Devolve CreateQuestionResponseDTO em caso de sucesso ou null se falhar.
     */
    public CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) {
        service.sendMessage(new Message<>(MessageType.CREATE_QUESTION, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[QuestionClientService] Timeout ao criar pergunta.");
            return null;
        }
        return switch (resp.getType()) {
            case CREATE_QUESTION_RESPONSE -> resp.getDataAs(CreateQuestionResponseDTO.class);
            case ERROR -> {
                System.err.println("[QuestionClientService] Erro a criar pergunta: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[QuestionClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }

    /**
     * Edita uma pergunta se ainda não existirem respostas.
     */
    public boolean editQuestion(EditQuestionDTO dto) {
        service.sendMessage(new Message<>(MessageType.EDIT_QUESTION, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[QuestionClientService] Timeout ao editar pergunta.");
            return false;
        }
        if (resp.getType() == MessageType.ACK) {
            return true;
        } else if (resp.getType() == MessageType.NACK || resp.getType() == MessageType.ERROR) {
            System.err.println("[QuestionClientService] Erro ao editar pergunta: " + resp.getData());
            return false;
        } else {
            System.err.println("[QuestionClientService] Resposta inesperada: " + resp.getType());
            return false;
        }
    }

    /**
     * Elimina uma pergunta se ainda não existirem respostas.
     */
    public boolean deleteQuestion(DeleteQuestionDTO dto) {
        service.sendMessage(new Message<>(MessageType.DELETE_QUESTION, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[QuestionClientService] Timeout ao eliminar pergunta.");
            return false;
        }
        if (resp.getType() == MessageType.ACK) {
            return true;
        } else if (resp.getType() == MessageType.NACK || resp.getType() == MessageType.ERROR) {
            System.err.println("[QuestionClientService] Erro ao eliminar pergunta: " + resp.getData());
            return false;
        } else {
            System.err.println("[QuestionClientService] Resposta inesperada: " + resp.getType());
            return false;
        }
    }

    /**
     * Lista perguntas de um docente com um filtro opcional.
     */
    public List<Question> listQuestions(ListQuestionsDTO dto) {
        service.sendMessage(new Message<>(MessageType.LIST_QUESTIONS, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[QuestionClientService] Timeout ao listar perguntas.");
            return null;
        }
        return switch (resp.getType()) {
            case LIST_QUESTIONS_RESPONSE -> {
                Serializable data = resp.getData();
                if (data instanceof ArrayList<?> list) {
                    yield (List<Question>) list;
                } else {
                    System.err.println("[QuestionClientService] Formato de dados inválido em LIST_QUESTIONS_RESPONSE.");
                    yield null;
                }
            }
            case ERROR -> {
                System.err.println("[QuestionClientService] Erro ao listar perguntas: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[QuestionClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }

    /**
     * Permite a um estudante juntar-se a uma pergunta com um código de acesso.
     */
    public Question joinQuestion(JoinQuestionDTO dto) {
        service.sendMessage(new Message<>(MessageType.JOIN_QUESTION, dto));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[QuestionClientService] Timeout ao juntar-se à pergunta.");
            return null;
        }
        return switch (resp.getType()) {
            case QUESTION_DETAILS -> resp.getDataAs(Question.class);
            case NACK -> {
                System.err.println("[QuestionClientService] Código de acesso inválido.");
                yield null;
            }
            case ERROR -> {
                System.err.println("[QuestionClientService] Erro ao juntar-se à pergunta: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[QuestionClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }
}
