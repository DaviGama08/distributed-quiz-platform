package pt.isec.directory.protocol;

/**
 * Tipos de mensagens UDP suportadas pela diretoria.
 *
 * Mensagens de SERVIDOR (requerem VER=1):
 * - REGISTER: Servidor regista-se na diretoria
 * - HEARTBEAT: Servidor envia sinal de vida
 * - DEREGISTER: Servidor remove-se da diretoria
 *
 * Mensagens de CLIENTE (sem VER):
 * - LOGIN: Cliente pede endereço do servidor principal
 *
 * Outros:
 * - BAD_REQUEST: Resposta de erro
 */
public enum MsgTypeUDP {
    // Mensagens de Servidor (com VER=1)
    REGISTER,
    HEARTBEAT,
    DEREGISTER,

    // Mensagens de Cliente (sem VER)
    LOGIN,

    // Respostas
    BAD_REQUEST
}
