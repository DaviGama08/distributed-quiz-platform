package pt.isec.directory.Threads;

/**
 * Diretoria robusta com 4 threads (todas via Runnable):
 *  (1) UdpListenerRunnable     — recebe datagramas UDP e empilha na fila
 *  (2) WorkerRunnable          — processa datagramas, atualiza estado e responde
 *  (3) ReaperRunnable          — TTL de 17s, remove servidores inativos
 *  (4) MetricsRunnable         — imprime estado e métricas periodicamente
 *
 * Protocolo (texto, K=V separados por pipe '|'):
 *  - Campos obrigatórios: VER=1 | TYPE=<...>
 *  - Mensagens:
 *    * TYPE=REGISTER   | ID=<serverId> | TCP=<ip:port>
 *    * TYPE=HEARTBEAT  | ID=<serverId> | DBV=<dbVersion>
 *    * TYPE=DEREGISTER | ID=<serverId>
 *    * TYPE=CLIENT_QUERY
 *
 * Respostas (texto):
 *  - "200 OK"
 *  - "200 PRINCIPAL <ip:port>"
 *  - "400 BAD_REQUEST <motivo>"
 *  - "404 NO_PRINCIPAL"
 *  - "409 CONFLICT <motivo>"
 *  - "500 ERROR <motivo>"
 */

public class UdpListenerRunnable implements Runnable{

    @Override
    public void run() {

    }

}
