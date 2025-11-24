package pt.isec.client.threads;

import pt.isec.client.services.IClientService;
import pt.isec.common.messages.TcpMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Thread que escuta continuamente mensagens vindas do servidor via TCP
 * e coloca-as na fila de respostas para serem processadas.
 */
public class ClientListenerThread implements Runnable{
    private final IClientService service;

    public ClientListenerThread(IClientService service) {
        this.service = service;
    }

    @Override
    public void run() {
        ObjectInputStream in = service.getInputStream();

        System.out.println("[ClientListener] Started listening for server messages...");

        while(service.isRunning()) {
            try {
                TcpMessage<? extends Serializable> response = (TcpMessage<? extends Serializable>) in.readObject();

                if(response != null) {
                    System.out.println("[ClientListener] Received: " + response.getType());
                    service.getResponseQueue().put(response);
                }
            } catch (IOException e) {
                if(service.isRunning()) {
                    System.err.println("[ClientListener] Connection lost: " + e.getMessage());
                    service.handleConnectionLost();
                }
                break;
            } catch (ClassNotFoundException e) {
                System.err.println("[ClientListener] Unknown message type: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("[ClientListener] Interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[ClientListener] Stopped");
    }
}
