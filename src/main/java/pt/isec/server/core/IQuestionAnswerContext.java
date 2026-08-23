package pt.isec.server.core;

import pt.isec.common.messages.TcpMessage;


/**
 * Minimal context shared by question/answer services to interact with the cluster and clients.
 */
public interface IQuestionAnswerContext {

    /* ===================== USER MANAGEMENT / NOTIFICATIONS ===================== */

    /**
     * Sends a message to the client associated with the given user id, if connected.
     *
     * @param userId user identifier
     * @param msg    message to send
     */
    void sendToUser(String role, long userId, TcpMessage<?> msg);
}
