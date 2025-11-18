package pt.isec.client.threads;

import pt.isec.client.services.IClientService;
import pt.isec.common.messages.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Thread que envia mensagens da fila de pedidos para o servidor via TCP.
 */
public class RequestSenderThread implements Runnable{
    private final IClientService service;

    public RequestSenderThread(IClientService service) {
        this.service = service;
    }

    @Override
    public void run() {
        ObjectOutputStream out = service.getOutputStream();

        System.out.println("[RequestSender] Started sending requests...");

        while(service.isRunning()) {
            try {
                Message<? extends Serializable> request = service.getRequestQueue().take();

                System.out.println("[RequestSender] Sending: " + request.getType());
                out.writeObject(request);
                out.flush();
            } catch (IOException e) {
                if(service.isRunning()) {
                    System.err.println("[RequestSender] Failed to send: " + e.getMessage());
                    service.handleConnectionLost();
                }
                break;
            } catch (InterruptedException e) {
                System.out.println("[RequestSender] Interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[RequestSender] Stopped");
    }
}
