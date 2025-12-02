package pt.isec.client;

import javafx.application.Platform;
import pt.isec.client.core.ClientService;
import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;
import pt.isec.common.util.Log;

/**
 * Orchestrates the client-side services and exposes them to the UI layer.
 * <p>
 * Responsible for:
 * <ul>
 *     <li>Starting and stopping the {@link ClientService}</li>
 *     <li>Providing access to authentication, question and answer services</li>
 *     <li>Requesting the UI to close when the client stops</li>
 * </ul>
 */
public class ClientManager {

    private final ClientService service;
    private final AuthClientService authService;
    private final QuestionClientService questionService;
    private final AnswerClientService answerService;

    /**
     * Callback registered by the UI to be executed when the manager needs to close the UI.
     */
    private Runnable uiCloser;

    /**
     * Creates a new {@code ClientManager} and initializes the underlying services.
     *
     * @param ip   directory IP address
     * @param port directory UDP port
     */
    public ClientManager(String ip, int port) {
        this.service = new ClientService(this, port, ip);
        this.authService = new AuthClientService(service);
        this.questionService = new QuestionClientService(service);
        this.answerService = new AnswerClientService(service);
    }

    /**
     * Starts the discovery and TCP connection service.
     * <p>
     * If it is not possible to contact the directory/server, the client is stopped and
     * the JavaFX application is terminated.
     */
    public void start() {
        if (!service.run()) {
            Log.error(ClientManager.class,
                    "[ClientManager] Não foi possível contactar servidor/diretoria. A encerrar aplicação.");
            stop();
            Platform.exit();
        }
    }

    /**
     * Stops the client service, closes network resources and requests the UI to close.
     * <p>
     * If a {@link #setUiCloser(Runnable)} callback was registered, it is executed on the
     * JavaFX Application Thread.
     */
    public void stop() {
        if (service != null) {
            service.stop();
        }
        if (uiCloser != null) {
            try {
                Platform.runLater(uiCloser);
            } catch (Exception e) {
                Log.error(ClientManager.class,
                        "[ClientManager] Não conseguiu correr o uiCloser: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Registers a callback that will be invoked when the {@code ClientManager}
     * wants the UI to close.
     *
     * @param uiCloser runnable to be invoked to close the UI
     */
    public void setUiCloser(Runnable uiCloser) {
        this.uiCloser = uiCloser;
    }

    /**
     * Gets the answer client service.
     *
     * @return {@link AnswerClientService} instance
     */
    public AnswerClientService getAnswerService() {
        return answerService;
    }

    /**
     * Gets the authentication client service.
     *
     * @return {@link AuthClientService} instance
     */
    public AuthClientService getAuthService() {
        return authService;
    }

    /**
     * Gets the question client service.
     *
     * @return {@link QuestionClientService} instance
     */
    public QuestionClientService getQuestionService() {
        return questionService;
    }

    /**
     * Gets the core client service.
     *
     * @return {@link ClientService} instance
     */
    public ClientService getService() {
        return service;
    }

    /**
     * Gets the authenticated user's ID.
     *
     * @return user ID or {@code null} if not authenticated
     */
    public Integer getUserId() {
        return service.getUserId();
    }

    /**
     * Sets the authenticated user's ID.
     *
     * @param id new user ID
     */
    public void setUserId(Integer id) {
        service.setUserId(id);
    }

    /**
     * Gets the authenticated user's student number.
     *
     * @return student number or {@code null} if not set
     */
    public Integer getStudentNumber() {
        return service.getStudentNumber();
    }

    /**
     * Sets the authenticated user's student number.
     *
     * @param number new student number
     */
    public void setStudentNumber(Integer number) {
        service.setStudentNumber(number);
    }
}
