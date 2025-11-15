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

    public boolean start(){
        /*ThreadFactory factory = r ->{
            Thread t = new Thread(r);
            t.setName("ClientService");
            t.setDaemon(true);
            return t;
        };
        try(ExecutorService executor = Executors.newSingleThreadExecutor(factory)){
           executor.submit(service::run);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }*/
        service.run();
        return service.isRunning();
    }

    public void stop(){ service.stop(); }

    // Serviços
    public AuthClientService getAuthService() { return authService; }
    public QuestionClientService getQuestionService() { return questionService; }
    public AnswerClientService getAnswerService() { return answerService; }
    public ClientService getService() { return service; }
}
