package pt.isec.client;

import javafx.application.Platform;
import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;
import pt.isec.client.core.ClientService;
/**
 * Classe que orquestra os serviços disponíveis para o cliente.
 */
public class ClientManager {
    private final ClientService service;
    private final AuthClientService authService;
    private final QuestionClientService questionService;
    private final AnswerClientService answerService;
    // callback que a UI regista para ser executado quando o manager pedir para fechar a UI
    private Runnable uiCloser;



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
    public void stop(){
        if(service != null)
            service.stop();
        if(uiCloser != null) {
            try {
                javafx.application.Platform.runLater(uiCloser);
            } catch (Exception e){
                // caso a JavaFX runtime não esteja disponível, apenas logue
                System.err.println("[ClientManager] Não conseguiu correr o uiCloser: " + e.getMessage());
            }
        }
    }

    //UI regista aqui o closer
    public void setUiCloser(Runnable uiCloser) {
        this.uiCloser = uiCloser;
    }

    public AnswerClientService getAnswerService() { return answerService; }
    public AuthClientService getAuthService() { return authService; }
    public QuestionClientService getQuestionService() { return questionService; }
    public ClientService getService() { return service; }
    /** Obtém o ID do utilizador autenticado. */
    public Integer getUserId() { return service.getUserId(); }
    /** Define o ID do utilizador autenticado. */
    public void setUserId(Integer id) { service.setUserId(id); }
    /** Obtém o número de estudante do utilizador autenticado. */
    public Integer getStudentNumber() { return service.getStudentNumber(); }
    /** Define o número de estudante do utilizador autenticado. */
    public void setStudentNumber(Integer number) { service.setStudentNumber(number); }
}
