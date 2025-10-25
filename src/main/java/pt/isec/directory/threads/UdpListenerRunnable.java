package pt.isec.directory.Threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.IDirectoryService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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
public class UdpListenerRunnable implements Runnable {
    private final IDirectoryService directoryService;

    public UdpListenerRunnable(IDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public void run() {
        DatagramSocket socket = directoryService.getSocket();
        System.out.println("Directoria UDP a escutar na porta " + directoryService.getUdpPort() + "...");
        byte[] buffer         = new byte[directoryService.getMaxPacketSize()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (directoryService.getRunning()) {
            try {
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                directoryService.queue().put(
                        new UdpMessage(packet.getAddress(), packet.getPort(), data, data.length)
                );
            } catch (IOException e) {
                if (directoryService.getRunning())
                    System.err.println("Erro a receber UDP: " + e.getMessage());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("UdpListenerRunnable terminou.");
    }
}
