package pt.isec.common.messages;

public enum MessageType {
    // handshake / util
    ACK, NACK, PING, PONG, MESSAGE, ERROR,

    // auth
    REGISTER_STUDENT, REGISTER_TEACHER, LOGIN, LOGIN_OK,

    // DB mgmt (servidor primário)
    DB_CREATE, DB_OK,DB_REQUEST_COPY,

    // backup push (cliente -> servidor)
    BACKUP_PUSH_BEGIN,   // meta (nome, tamanho, checksum)
    BACKUP_PUSH_STREAM,  // stream “bruto” (ver nota abaixo)
    BACKUP_PUSH_END,     // finalizar/confirmar
    BACKUP_OK
}
