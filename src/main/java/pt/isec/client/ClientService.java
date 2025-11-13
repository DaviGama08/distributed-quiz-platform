package pt.isec.client;

import pt.isec.client.threads.ClientListenerRunnable;
import pt.isec.client.threads.RequestSenderRunnable;
import pt.isec.client.threads.ResponseHandlerRunnable;
import pt.isec.common.messages.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientService implements IClientService{
    private final int directoryUdpPort;
    private final String  directoryHost;

    private int serverTcpPort;
    private String serverTcpHost;

    private final int DATAGRAM_PACKET_SIZE = 1024;
    private final int DISCOVERY_TIMEOUT_MS = 5000;
    private final int CONNECTION_TIMEOUT_MS = 10000;

    private Thread  tListener;
    private Thread   tSender;
    private Thread tResponse;

    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Filas para comunicação entre threads
    private final BlockingQueue<Message<? extends Serializable>> requestQueue;
    private final BlockingQueue<Message<? extends Serializable>> responseQueue;

    private volatile boolean running;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 2;

    private final ClientManager manager;

    public ClientService(ClientManager manager, int directoryUdpPort, String directoryHost) {
        this.manager = manager;
        this.directoryUdpPort = directoryUdpPort;
        this.directoryHost = directoryHost;
        this.requestQueue = new LinkedBlockingQueue<>();
        this.responseQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }
    //TODO: método para se comunicar a primeira vez com o servidor
    private boolean discoverServer() {
        try{
            if(udpSocket == null || udpSocket.isClosed()){
                udpSocket = new DatagramSocket();
            }
            udpSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            byte[] data = "LOGIN".getBytes();
            DatagramPacket packet = new DatagramPacket(data,
                                                       data.length,
                                                       InetAddress.getByName(directoryHost),
                                                       directoryUdpPort);
            udpSocket.send(packet);

            byte[] buffer = new byte[DATAGRAM_PACKET_SIZE];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(receive);

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
            System.out.println("[ClientService] Servidor descoberto: " + host + ":" + port);
            return true;
        }catch (IOException | NumberFormatException e) {
            System.err.println("[ClientService] Discovery failed: " + e.getMessage());
            return false;
        }
    }

    private boolean connectToServer() {
        try {
            System.out.println("[ClientService] Conectando ao servidor TCP " + serverTcpHost + ":" + serverTcpPort);
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverTcpHost, serverTcpPort), CONNECTION_TIMEOUT_MS);

            out = new ObjectOutputStream(tcpSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(tcpSocket.getInputStream());

            System.out.println("[ClientService] Conectado ao servidor!");
            return true;
        } catch (IOException e) {
            System.err.println("[ClientService] Falha ao conectar: " + e.getMessage());
            closeConnection();
            return false;
        }
    }

    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (IOException e) {
            System.err.println("[ClientService] Erro ao fechar conexão: " + e.getMessage());
        }
    }
    @Override
    public void run(){
        boolean connected = false;
        for(int i = 0; i < 3; i++) {
            connected = discoverServer();
            if(connected) break;
        }
        if(!connected){
            System.err.println("[ClientService] Directory not found");
            return;
        }

        // Conectar ao servidor TCP
        if(!connectToServer()){
            System.err.println("[ClientService] Failed to connect to server");
            return;
        }

        running = true;
        start();
    }
    @Override
    public void start() {
        tListener = new Thread(new ClientListenerRunnable(this),  "ClientListenerRunnable");
        tSender   = new Thread(new RequestSenderRunnable(this), "RequestSenderRunnable");
        tResponse = new Thread(new ResponseHandlerRunnable(this), "ResponseHandlerRunnable");

        tListener.start();
        tSender.start();
        tResponse.start();
    }
    @Override
    public void stop() {
        running = false;

        if(tListener != null) tListener.interrupt();
        if(tSender != null)   tSender.interrupt();
        if(tResponse != null) tResponse.interrupt();

        closeConnection();

        if(udpSocket != null && !udpSocket.isClosed())
            udpSocket.close();
    }

    // Getters for threads
    public ObjectInputStream getInputStream() { return in; }
    public ObjectOutputStream getOutputStream() { return out; }
    public BlockingQueue<Message<? extends Serializable>> getRequestQueue() { return requestQueue; }
    public BlockingQueue<Message<? extends Serializable>> getResponseQueue() { return responseQueue; }
    public boolean isRunning() { return running; }
    public Socket getTcpSocket() { return tcpSocket; }

    // Public API methods for sending requests
    public void sendMessage(Message<? extends Serializable> message) {
        try {
            requestQueue.put(message);
        } catch (InterruptedException e) {
            System.err.println("[ClientService] Failed to queue message: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public Message<? extends Serializable> waitForResponse() throws InterruptedException {
        return responseQueue.take();
    }

    public void handleConnectionLost() {
        if(reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.err.println("[ClientService] Max reconnect attempts reached. Exiting...");
            manager.stop();
            return;
        }

        reconnectAttempts++;
        System.out.println("[ClientService] Connection lost. Attempting to reconnect (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");

        closeConnection();

        try {
            Thread.sleep(5000); // Wait before retrying
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Try to reconnect
        run();
    }
}
