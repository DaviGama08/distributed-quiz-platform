package pt.isec.server.core;

import pt.isec.common.messages.TcpMessage;

/**
 * Interface que expõe apenas as operações do servidor
 * necessárias aos serviços de Perguntas e Respostas:
 * - replicação SQL / versão da BD
 * - verificação de sessões ativas
 * - envio de notificações para utilizadores.
 */
public interface IQuestionAnswerContext {
    void recordSqlUpdate(String sql);
    long dbVersion();
    void setDbVersion(long v);

    boolean isUserLogged(long userId);
    void sendToUser(long userId, TcpMessage<?> msg);
}
