package pt.isec.client;

import javafx.application.Platform;
import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;
import pt.isec.client.services.ClientService;
/**
 * Classe que orquestra os serviços disponíveis para o cliente.
 */
public class ClientManager {
    private final ClientService service;
    private final AuthClientService authService;
    private final QuestionClientService questionService;
    private final AnswerClientService answerService;

    public ClientManager(String ip, int port){
        this.service = new ClientService(this, port, ip);
        this.authService = new AuthClientService(service);
        this.questionService = new QuestionClientService(service);
        this.answerService = new AnswerClientService(service);
    }
    /** Arranca o serviço de descoberta e conexão TCP */
    public void start(){
        if (!service.run()) {
            System.err.println("[ClientManager] Não foi possível contactar servidor/diretoria. A encerrar aplicação.");
            stop(); // fecha recursos de rede
            // sair da app JavaFX
            Platform.exit();
        }

    }
    /** Pára o serviço e fecha conexões */
    public void stop(){ service.stop(); }
    public AuthClientService getAuthService() { return authService; }
    public QuestionClientService getQuestionService() { return questionService; }
    public AnswerClientService getAnswerService() { return answerService; }
    public ClientService getService() { return service; }
    /** Obtém o ID do utilizador autenticado. */
    public Integer getUserId() { return service.getUserId(); }
    /** Define o ID do utilizador autenticado. */
    public void setUserId(Integer id) { service.setUserId(id); }
}
