package pt.isec.server.core;

import pt.isec.common.messages.TcpMessage;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Interface que expõe apenas as operações do servidor
 * necessárias aos serviços de Perguntas e Respostas:
 * - replicação SQL / versão da BD
 * - verificação de sessões ativas
 * - envio de notificações para utilizadores.
 */
public interface IQuestionAnswerContext {
    BlockingQueue<List<String>> queue();
    long dbVersion();
    void setDbVersion(long v);

    boolean isUserLogged(long userId);
    void sendToUser(long userId, TcpMessage<?> msg);
}
