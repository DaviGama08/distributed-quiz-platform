package pt.isec.common.messages;

public enum MessageType {
    // handshake / util
    ACK, NACK, PING, PONG, MESSAGE, ERROR,

    // auth
    REGISTER_STUDENT, REGISTER_TEACHER, LOGIN, LOGIN_OK, LOGIN_FAIL, LOGOUT,

    // teacher messages
    CREATE_QUESTION, EDIT_QUESTION, DELETE_QUESTION,
    LIST_QUESTIONS, LIST_QUESTIONS_RESPONSE,
    VIEW_ANSWERS, VIEW_ANSWERS_RESPONSE,
    EXPORT_RESULTS_CSV, EXPORT_RESULTS_OK, EXPORT_RESULTS_FAIL,

    //student messages
    JOIN_QUESTION, QUESTION_DETAILS,
    SUBMIT_ANSWER, SUBMIT_OK, SUBMIT_FAIL,
    LIST_ANSWERED_QUESTIONS, LIST_ANSWERED_RESPONSE,


    // DB mgmt (servidor primário)
    DB_CREATE, DB_OK,DB_REQUEST_COPY,

    // backup push (cliente -> servidor)
    BACKUP_PUSH_BEGIN,   // meta (nome, tamanho, checksum)
    BACKUP_PUSH_STREAM,  // stream “bruto” (ver nota abaixo)
    BACKUP_PUSH_END,     // finalizar/confirmar
    BACKUP_OK


}
