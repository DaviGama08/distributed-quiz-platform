package pt.isec.client;

import pt.isec.client.threads.ClientListenerRunnable;
import pt.isec.client.threads.RequestSenderRunnable;
import pt.isec.client.threads.ResponseHandlerRunnable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClientService implements IClientService{
    private final int directoryUdpPort;
    private final String  directoryHost;

    private int serverTcpPort;
    private String serverTcpHost;

    private final int DATAGRAM_PACKET_SIZE = 1024;
    private final int DISCOVERY_TIMEOUT_MS = 5000;
    private Thread  tListener;
    private Thread   tSender;
    private Thread tResponse;

    private DatagramSocket socket;

    private final ClientManager manager;

    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
    }
    //TODO: método para se comunicar a primeira vez com o servidor
    private boolean discoverServer() {
        try{
            if(socket == null || socket.isClosed()){
                socket = new DatagramSocket();
            }
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            byte[] data = "LOGIN".getBytes();
            DatagramPacket packet = new DatagramPacket(data,
                                                       data.length,
                                                       InetAddress.getByName(directoryHost),
                                                       directoryUdpPort);
            socket.send(packet);

            byte[] buffer = new byte[DATAGRAM_PACKET_SIZE];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            socket.receive(receive);

            String msg = new String(receive.getData(), 0, receive.getLength()).trim();

            if(!msg.startsWith("SERVER")){
                System.err.println("[ClientService] Esperado: SERVER 192.168.1.10:5000");
                return false;
            }

            //pular o server, ficando apenas com o endereço e porta no target
            String target = msg.substring("SERVER ".length()).trim();
            int idx = target.lastIndexOf(':');
            if(idx <= 0){
                System.err.println("[ClientService] Esperado: 192.168.1.10':'5000");
                return false;
            }

            String host = target.substring(0, idx);
            int port = Integer.parseInt(target.substring(idx + 1));

            this.serverTcpHost = host;
            this.serverTcpPort = port;
            return true;
        }catch (IOException | NumberFormatException e) {
            System.err.println("[ClientService] Discovery failed: " + e.getMessage());
            return false;
        }
    }
    @Override
    public void run(){
        boolean connected = false;
        for(int i = 0; i < 3;i++) {
            connected = discoverServer();
            if(connected) break;
        }
        if(!connected){
            System.err.println("[ClientService] Directory not found");
            return;
        }
        start();
    }
    @Override
    public void start() {
        tListener = new Thread(new ClientListenerRunnable(),  "ClientListenerRunnable");
        tSender   = new Thread(new RequestSenderRunnable(), "RequestSenderRunnable");
        tResponse = new Thread(new ResponseHandlerRunnable(), "ResponseHandlerRunnable");

        tListener.start();
        tSender.start();
        tResponse.start();
    }
    @Override
    public void stop() {
        if(tListener != null) tListener.interrupt();
        if(tSender != null)   tSender.interrupt();
        if(tResponse != null) tResponse.interrupt();

        if(socket.isClosed() || socket != null)
            socket.close();
    }
}
