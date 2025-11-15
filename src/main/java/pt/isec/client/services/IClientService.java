package pt.isec.client.services;

import pt.isec.common.messages.Message;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public interface IClientService {
    void handleConnectionLost();
    ObjectOutputStream getOutputStream();
    ObjectInputStream getInputStream();
    BlockingQueue<Message<? extends Serializable>> getRequestQueue();
    BlockingQueue<Message<? extends Serializable>> getResponseQueue();
    boolean isRunning();
    Socket getTcpSocket();
}
