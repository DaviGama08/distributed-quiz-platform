package pt.isec.server.core;

import pt.isec.common.messages.TcpMessage;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface IQuestionAnswerContext {

    /* ===================== SINCRONIZAÇÃO / CLUSTER ===================== */

    BlockingQueue<List<String>> queue();
    void setDbVersion(long v);


    /* ===================== GESTÃO DE UTILIZADORES E NOTIFICAÇÕES ===================== */

    boolean isUserLogged(long userId);
    void sendToUser(long userId, TcpMessage<?> msg);
}
