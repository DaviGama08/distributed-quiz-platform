package pt.isec.directory.Threads;

import pt.isec.directory.DirectoryService;
import pt.isec.directory.MsgType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final int portUdp;
    private volatile boolean running;

    private final int TIMEOUT  = 10000;
    private final int MAX_SIZE = 1024;

    private final ConcurrentMap<String,
            DirectoryService.ServerInfo> servers = new ConcurrentHashMap<>();

    public UdpListenerRunnable(int portUdp, boolean running){
        this.portUdp = portUdp;
        this.running = running;
    }

    @Override
    public void run() {

        try(DatagramSocket socket = new DatagramSocket(portUdp)){
            System.out.println("Directoria UDP iniciada na porta " + portUdp + "...");
            byte[] buffer = new byte[MAX_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while(running){
                System.out.println("A espera de pedidos...");
                socket.receive(packet);
                socket.setSoTimeout(TIMEOUT);

                MsgType msgType;

                //Desserialização da String recebida
                try(ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    ObjectInputStream ois     = new ObjectInputStream(bais)){
                    msgType = (MsgType) ois.readObject();
                }

                //Trata o pedido
                if(!handleRequest(msgType)){continue;}



            }

        }catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro na directoria UDP: " + e.getMessage());
        }
    }

    private boolean handleRequest(MsgType m){
        switch (m){
            case REGISTER -> {

            }
            case DEREGISTER -> {

            }
            case HEARTBEAT -> {

            }
            case CLIENT_QUERY -> {

            }
            default -> {
                return false;
            }
        }
        return true;
    }

}
