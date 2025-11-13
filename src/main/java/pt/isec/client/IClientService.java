package pt.isec.client;

import pt.isec.common.messages.Message;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.BlockingQueue;

public interface IClientService {

    ObjectInputStream getInputStream();

    boolean isRunning();

    BlockingQueue<Message<? extends Serializable>> getResponseQueue();

    void handleConnectionLost();
}
