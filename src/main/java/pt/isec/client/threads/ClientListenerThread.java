package pt.isec.client.threads;

import pt.isec.client.core.IClientService;
import pt.isec.common.messages.TcpMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Thread que escuta continuamente mensagens vindas do servidor via TCP
 * e coloca-as na fila de respostas para serem processadas.
 */
public class ClientListenerThread implements Runnable {
    private final IClientService service;

    public ClientListenerThread(IClientService service) {
        this.service = service;
    }

    @Override
    public void run() {
        System.out.println("[ClientListener] A escuta de mensagens do servidor...");

        while (service.isRunning()) {
            try {
                ObjectInputStream in = service.getInputStream();
                if (in == null) {
                    // Sem stream válido (por ex. durante reconexão); aguarda um pouco
                    Thread.sleep(200);
                    continue;
                }

                @SuppressWarnings("unchecked")
                TcpMessage<? extends Serializable> response =
                        (TcpMessage<? extends Serializable>) in.readObject();

                if (response != null) {
                    System.out.println("[ClientListener] Recebido: " + response.getType());
                    service.getResponseQueue().put(response);
                }
            } catch (IOException e) {
                if (service.isRunning()) {
                    System.err.println("[ClientListener] Ligação perdida: " + e.getMessage());
                    service.handleConnectionLost();
                }
                break;
            } catch (ClassNotFoundException e) {
                System.err.println("[ClientListener] Tipo de mensagem desconhecida: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("[ClientListener] Interrompido");
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[ClientListener] Parado");
    }
}
