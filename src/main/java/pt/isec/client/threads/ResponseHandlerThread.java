package pt.isec.client.threads;

import pt.isec.client.services.IClientService;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;

import java.io.Serializable;

/**
 * Thread que processa as respostas do servidor da fila de respostas
 * e executa a lógica apropriada para cada tipo de mensagem.
 */
public class ResponseHandlerThread implements Runnable{
    private final IClientService service;

    public ResponseHandlerThread(IClientService service) {
        this.service = service;
    }

    @Override
    public void run() {
        System.out.println("[ResponseHandler] Started processing responses...");

        while(service.isRunning()) {
            try {
                Message<? extends Serializable> response = service.getResponseQueue().take();
                processResponse(response);
            } catch (InterruptedException e) {
                System.out.println("[ResponseHandler] Interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[ResponseHandler] Stopped");
    }

    private void processResponse(Message<? extends Serializable> response) {
        MessageType type = response.getType();

        System.out.println("[ResponseHandler] Processing: " + type);

        switch(type) {
            case LOGIN_OK:
                System.out.println("[ResponseHandler] Login successful!");
                break;

            case LOGIN_FAIL:
                System.err.println("[ResponseHandler] Login failed!");
                break;

            case ACK:
                System.out.println("[ResponseHandler] Operation acknowledged");
                break;

            case NACK:
                System.err.println("[ResponseHandler] Operation failed");
                break;

            case ERROR:
                System.err.println("[ResponseHandler] Server error: " + response.getData());
                break;

            case PONG:
                System.out.println("[ResponseHandler] Pong received");
                break;

            case LIST_QUESTIONS_RESPONSE:
                System.out.println("[ResponseHandler] Questions list received");
                break;

            case VIEW_ANSWERS_RESPONSE:
                System.out.println("[ResponseHandler] Answers received");
                break;

            case QUESTION_DETAILS:
                System.out.println("[ResponseHandler] Question details received");
                break;

            case SUBMIT_OK:
                System.out.println("[ResponseHandler] Answer submitted successfully");
                break;

            case SUBMIT_FAIL:
                System.err.println("[ResponseHandler] Failed to submit answer");
                break;

            case LIST_ANSWERED_RESPONSE:
                System.out.println("[ResponseHandler] Answered questions history received");
                break;

            default:
                System.out.println("[ResponseHandler] Unhandled message type: " + type);
        }
    }
}
