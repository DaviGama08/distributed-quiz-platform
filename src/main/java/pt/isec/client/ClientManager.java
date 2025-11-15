package pt.isec.client;

import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;
import pt.isec.client.services.ClientService;

public class ClientManager {
    private final int port;
    private final String ip;
    private final ClientService service;

    private final AuthClientService authService;
    private final QuestionClientService questionService;
    private final AnswerClientService answerService;

    public ClientManager(String ip, int port){
        this.port = port;
        this.ip = ip;
        this.service = new ClientService(this, port, ip);
        this.authService = new AuthClientService(service);
        this.questionService = new QuestionClientService(service);
        this.answerService = new AnswerClientService(service);
    }

    public void start(){
        // Arranca discovery+conexão em background (NÃO chamar service.start() diretamente)
        new Thread(service::run, "ClientBootstrap").start();
    }

    public void stop(){ service.stop(); }

    // Serviços
    public AuthClientService getAuthService() { return authService; }
    public QuestionClientService getQuestionService() { return questionService; }
    public AnswerClientService getAnswerService() { return answerService; }
    public ClientService getService() { return service; }
}
