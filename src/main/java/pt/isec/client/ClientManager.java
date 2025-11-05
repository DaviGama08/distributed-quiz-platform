package pt.isec.client;

public class ClientManager {
    private final int port;
    private final String ip;
    private final ClientService service;

    public ClientManager(int port, String ip){
        this.port = port; this.ip = ip;
        service = new ClientService(this, port, ip);
    }

    public void start(){
        service.start();
    }

    public void stop(){
        service.stop();
    }
}
