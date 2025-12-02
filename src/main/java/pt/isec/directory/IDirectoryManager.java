package pt.isec.directory;

import pt.isec.common.messages.UdpMessage;

import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

public interface IDirectoryManager {

    /* ===================== CONFIGURAÇÃO UDP ===================== */

    int udpPort();
    int maxPacketSize();
    DatagramSocket socket();


    /* ===================== ESTADO GLOBAL ===================== */

    boolean isRunning();


    /* ===================== REGISTO E GESTÃO DE SERVIDORES ===================== */

    ConcurrentMap<String, ServerInfo> servers();
    Map<String, ServerInfo> serversOrdered();
    Object serversLock();

    int serversCount();
    String masterServerUuid();
    int serverTcpPort(String uuid);

    void removeServersFromList(long currTime);


    /* ===================== PROCESSAMENTO DE MENSAGENS ===================== */

    BlockingQueue<UdpMessage> queue();
}
