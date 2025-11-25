package pt.isec.client.threads;

import pt.isec.client.core.IClientService;
import pt.isec.common.messages.TcpMessage;

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
        System.out.println("[RequestSender] Started sending requests...");

        while(service.isRunning()) {
            TcpMessage<? extends Serializable> request = null;
            try {
                request = service.getRequestQueue().take();

                ObjectOutputStream out = service.getOutputStream();
                if (out == null) {
                    // não temos stream válido — tenta reconectar e re-enfileirar
                    System.err.println("[RequestSender] No output stream available, requeueing request: " + request.getType());
                    service.getRequestQueue().put(request);
                    service.handleConnectionLost();
                    break;
                }

                System.out.println("[RequestSender] Sending: " + request.getType());
                out.writeObject(request);
                out.flush();
            } catch (IOException e) {
                if(service.isRunning()) {
                    System.err.println("[RequestSender] Failed to send: " + e.getMessage());
                    try {
                        if (request != null) service.getRequestQueue().put(request);
                    } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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
