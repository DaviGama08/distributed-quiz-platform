package pt.isec.directory.threads;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.IDirectoryManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Diretoria robusta com 4 threads (todas via Runnable):
 *  (1) UdpListenerThread     — recebe datagramas UDP e empilha na fila
 *  (2) WorkerThread          — processa datagramas, atualiza estado e responde
 *  (3) ReaperThread          — TTL de 17s, remove servidores inativos
 *  (4) MetricsThread         — imprime estado e métricas periodicamente
 *
 * Protocolo (texto, K=V separados por pipe '|'):
 *
 * === MENSAGENS DE CLIENTE (sem VER) ===
 *  - TYPE=LOGIN
 *
 * === MENSAGENS DE SERVIDOR ===
 *  - TYPE=REGISTER   | ID=<serverId> | TCP=<ip:port> | DBV=<dbVersion>
 *  - TYPE=HEARTBEAT  | ID=<serverId> | DBV=<dbVersion>
 *  - TYPE=DEREGISTER | ID=<serverId>
 *
 * Respostas (texto):
 *  - "200 OK"
 *  - "200 PRINCIPAL <ip:port>"
 *  - "400 BAD_REQUEST <motivo>"
 *  - "404 NO_PRINCIPAL"
 *  - "409 CONFLICT <motivo>"
 *  - "500 ERROR <motivo>"
 */
public class UdpListenerThread implements Runnable {
    private final IDirectoryManager tInfo;

    public UdpListenerThread(IDirectoryManager tInfo) {
        this.tInfo = tInfo;
    }

    @Override
    public void run() {
        DatagramSocket socket = tInfo.socket();
        System.out.println("Directoria UDP a escutar na porta " + tInfo.udpPort() + "...");
        byte[] buffer         = new byte[tInfo.maxPacketSize()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (tInfo.isRunning()) {
            try {
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                tInfo.queue().put(
                        new UdpMessage(packet.getAddress(), packet.getPort(), data, data.length)
                );

            } catch (SocketTimeoutException ste) {
                // opcional: socket configurado com timeout; apenas revalida loop e continua
            } catch (SocketException se) {
                // socket fechado intencionalmente (shutdown) causará aqui um SocketException
                if (!tInfo.isRunning() || socket.isClosed()) {
                    // shutdown ordenado — terminar o loop sem alarmes
                    break;
                }
                // erro de socket inesperado -> log e pequena pausa para evitar busy-loop
                System.err.println("Erro de socket UDP: " + se.getMessage());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (IOException e) {
                // outros IO erros
                if (tInfo.isRunning()) System.err.println("Erro a receber UDP: " + e.getMessage());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException ie) {
                // thread interrompida enquanto bloqueada no queue.put()
                Thread.currentThread().interrupt(); //restaura sinal de pedido de interrupção
                break;
            }
        }
        System.out.println("UdpListenerThread terminou.");
    }
}
