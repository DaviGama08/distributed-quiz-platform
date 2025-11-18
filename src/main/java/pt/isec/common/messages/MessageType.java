package pt.isec.common.messages;

/**
 * Define os tipos de mensagens trocadas entre clientes e servidor.
 */
public enum MessageType {
    // Autenticação (já existentes)
    LOGIN,
    LOGIN_OK,
    LOGIN_FAIL,
    REGISTER_STUDENT,
    REGISTER_TEACHER,
    ACK,
    NACK,
    ERROR,
    LOGOUT,
    PONG,

    // Perguntas – professor
    CREATE_QUESTION,
    CREATE_QUESTION_RESPONSE,
    EDIT_QUESTION,
    DELETE_QUESTION,
    LIST_QUESTIONS,
    LIST_QUESTIONS_RESPONSE,
    // Pergunta – aluno
    JOIN_QUESTION,
    QUESTION_DETAILS,

    // Respostas
    SUBMIT_ANSWER,
    SUBMIT_OK,
    SUBMIT_FAIL,
    VIEW_ANSWERS,
    VIEW_ANSWERS_RESPONSE,
    LIST_ANSWERED_QUESTIONS,
    LIST_ANSWERED_RESPONSE,

    // Replicação incremental via SQL (heartbeat)
    SQL_UPDATE,
    DB_REQUEST_COPY
}
