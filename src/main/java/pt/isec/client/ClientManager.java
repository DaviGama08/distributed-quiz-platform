package pt.isec.client;

import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;

/**
 * Gerenciador principal do cliente
 * Coordena serviços de alto nível e ciclo de vida da aplicação
 */
public class ClientManager {
    private final int port;
    private final String ip;
    private final ClientService service;

    // Serviços de alto nível
    private final AuthClientService authService;
    private final QuestionClientService questionService;
    private final AnswerClientService answerService;

    public ClientManager(int port, String ip){
        this.port = port;
        this.ip = ip;
        this.service = new ClientService(this, port, ip);

        // Inicializar serviços
        this.authService = new AuthClientService(service);
        this.questionService = new QuestionClientService(service);
        this.answerService = new AnswerClientService(service);
    }

    public void start(){
        System.out.println("[ClientManager] Starting client...");
        new Thread(service, "ClientService").start();
    }

    public void stop(){
        System.out.println("[ClientManager] Stopping client...");
        service.stop();
    }

    // Getters para os serviços
    public AuthClientService getAuthService() {
        return authService;
    }

    public QuestionClientService getQuestionService() {
        return questionService;
    }

    public AnswerClientService getAnswerService() {
        return answerService;
    }

    public ClientService getService() {
        return service;
    }
}
