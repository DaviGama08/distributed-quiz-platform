package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.AuthenticationView;
import pt.isec.common.dto.auth.AuthResponseDTO;

import java.beans.PropertyChangeEvent;

/**
 * Controlador da interface de autenticação. Contém lógica de validação,
 * chamadas aos serviços de autenticação e navegação.
 */
public class AuthenticationController {
    private enum Mode { LOGIN, REGISTER }

    private static final Color BLUE = Color.web("#3498db");
    private static final Color RED  = Color.web("#A01316");

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final AuthenticationView view;

    private Mode mode = Mode.LOGIN;
    private volatile boolean authBusy = false;

    public AuthenticationController(Stage stage, ClientManager clientManager, ClientApplication application) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.view = new AuthenticationView();
        this.view.createView();
        this.view.registerHandlers(this);
        view.showLoginMode();
        setupPropertyChangeListeners();
    }

    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        // Escuta do estado autenticado
        service.addPropertyChangeListener(ClientService.PROP_AUTHENTICATED, this::handleAuthenticationChange);

        // Sucesso no login
        service.addPropertyChangeListener(ClientService.PROP_LOGIN_OK, this::handleLoginSuccessResponse);

        // Falha no login OU erro genérico
        service.addPropertyChangeListener(ClientService.PROP_LOGIN_FAIL, this::handleLoginFailResponse);

        // Sucesso no registo
        service.addPropertyChangeListener(ClientService.PROP_REGISTER_OK, this::handleRegisterOkResponse);

        // Estados da ligação (diretoria / servidor)
        service.addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, this::handleConnectionStatusChange);
    }

    private void handleAuthenticationChange(PropertyChangeEvent evt) {
        boolean authenticated = (boolean) evt.getNewValue();
        if (authenticated) {
            Platform.runLater(() -> {
                ClientService service = clientManager.getService();
                String userType = service.getUserType();
                String email    = service.getUserEmail();
                openDashboard(userType, email);
            });
        }
    }

    private void handleLoginSuccessResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        ClientService service = clientManager.getService();
        try {
            service.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            service.setUserId(null);
        }
        service.setUserType(data.userType());
        service.setUserEmail(data.email());
        service.setAuthenticated(true);

        Platform.runLater(() ->
                view.setLoginStatus("✔️ Login OK! A carregar dashboard...", Color.GREEN, false, true)
        );
    }

    /**
     * Trata tanto LOGIN_FAIL como ERROR vindos do servidor.
     * Se estivermos em modo LOGIN, mostra mensagem no formulário de login.
     * Se estivermos em modo REGISTER, mostra mensagem no formulário de registo.
     */
    private void handleLoginFailResponse(PropertyChangeEvent evt) {
        String data = (String) evt.getNewValue();
        Platform.runLater(() -> {
            if (mode == Mode.LOGIN) {
                view.setLoginStatus("❌ Login Falhou: " + data, RED, false, true);
            } else {
                view.setRegisterStatus("❌ Registo Falhou: " + data, RED, true);
            }
            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    /** Sucesso no registo */
    private void handleRegisterOkResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        ClientService service = clientManager.getService();

        try {
            service.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            service.setUserId(null);
        }
        service.setUserType(data.userType());
        service.setUserEmail(data.email());
        service.setAuthenticated(true);

        Platform.runLater(() -> {
            view.setRegisterStatus("✔️ Registo OK! A carregar dashboard...", Color.GREEN, true);

            // limpar formulários e voltar ao modo login
            view.clearRegisterFields();
            view.clearLoginFields();
            mode = Mode.LOGIN;
            view.showLoginMode();

            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    private void handleConnectionStatusChange(PropertyChangeEvent evt) {
        String status = (String) evt.getNewValue();

        switch (status) {
            case "DIRECTORY_CONNECTING" -> {
                // ecrã azul escuro, centrado
                Platform.runLater(() -> view.showGlobalLoading("A contactar a diretoria..."));
            }
            case "SERVER_CONNECTING" -> {
                Platform.runLater(() -> view.showGlobalLoading("A ligar ao servidor principal..."));
            }
            case "CONNECTED" -> {
                Platform.runLater(() -> {
                    view.hideGlobalLoading();
                    if (!authBusy) {
                        view.setLoginStatus(
                                "Ligação estabelecida. Introduza as suas credenciais.",
                                BLUE,
                                false,
                                true
                        );
                    }
                });
            }
            case "DIRECTORY_ERROR" -> {
                // mantém o overlay, apenas muda o texto para erro
                Platform.runLater(() -> view.showGlobalError("Não foi possível contactar a diretoria/servidor. \nA aplicação vai encerrar..."));
                try {
                    Thread.sleep(3000); // dá tempo para o utilizador ler a mensagem
                } catch (InterruptedException ignored) { }
            }
            case "SERVER_ERROR" -> {
                Platform.runLater(() -> view.showGlobalError("Não foi possível ligar ao servidor principal.\nA aplicação vai encerrar..."));
            }
            default -> {
            }
        }
    }

    /** Alterna entre modos de autenticação */
    public void onToggleMode() {
        if (authBusy) return;
        if (mode == Mode.LOGIN) {
            mode = Mode.REGISTER;
            view.showRegisterMode();
        } else {
            mode = Mode.LOGIN;
            view.showLoginMode();
        }
    }

    /** Handler do botão de login */
    public void onLogin() throws InterruptedException {
        if (mode != Mode.LOGIN || authBusy) return;

        String email = view.getLoginEmail();
        String password = view.getLoginPassword();

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Por favor, preencha todos os campos.");
            return;
        }
        if (!email.contains("@")) {
            showLoginError("Email inválido.");
            return;
        }

        setAuthBusy(true);
        view.setLoginStatus("A autenticar...", BLUE, true, false);

        clientManager.getAuthService().login(email, password);
    }

    /** Handler do botão de registo */
    public void onRegister() {
        if (mode != Mode.REGISTER || authBusy) return;

        String type = view.getSelectedRegisterType();
        String name = view.getRegisterName();
        String email = view.getRegisterEmail();
        String password = view.getRegisterPassword();
        String extra = view.getRegisterExtra();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || extra.isEmpty()) {
            showRegisterError("Por favor, preencha todos os campos.");
            return;
        }
        if (!email.contains("@")) {
            showRegisterError("Email inválido.");
            return;
        }
        if (password.length() < 6) {
            showRegisterError("Password deve ter no mínimo 6 caracteres.");
            return;
        }

        setAuthBusy(true);
        view.setRegisterStatus("A registar...", BLUE, false);

        try {
            if ("STUDENT".equalsIgnoreCase(type)) {
                int number;
                try {
                    number = Integer.parseInt(extra);
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> showRegisterError("Número de estudante inválido."));
                    return;
                }
                clientManager.getAuthService().registerStudent(name, email, password, number);
            } else {
                clientManager.getAuthService().registerTeacher(name, email, password, extra);
            }
        } catch (Exception e) {
            Platform.runLater(() -> showRegisterError("Erro no registo: " + e.getMessage()));
            setAuthBusy(false);
        }
    }

    /** Handler de alteração do tipo de registo */
    public void onRegisterTypeChanged(String type) {
        if ("STUDENT".equalsIgnoreCase(type)) {
            view.setRegisterExtraLabel("Número de Estudante");
        } else {
            view.setRegisterExtraLabel("Código de Docente");
        }
    }

    /** Activa/desactiva o estado de busy e actualiza a interface */
    private void setAuthBusy(boolean busy) {
        authBusy = busy;
        Platform.runLater(() -> view.setAuthBusy(busy));
    }

    /** Abre o dashboard apropriado (docente/estudante) */
    private void openDashboard(String userType, String email) {
        view.clearLoginFields();
        view.clearRegisterFields();
        if ("TEACHER".equalsIgnoreCase(userType)) {
            application.showTeacherDashboard(email);
        } else {
            application.showStudentDashboard(email);
        }
    }

    /** Mostra erro no login */
    private void showLoginError(String message) {
        view.setLoginStatus("❌ " + message, RED, false, true);
    }

    /** Mostra erro no registo */
    private void showRegisterError(String message) {
        view.setRegisterStatus("❌ " + message, RED, true);
    }

    /** Reinicia a vista de autenticação */
    public void show() {
        authBusy = false;
        mode = Mode.LOGIN;
        view.showLoginMode();
        view.setLoginStatus("", BLUE, false, true);
        view.setRegisterStatus("", BLUE, true);
        view.clearLoginFields();
        view.clearRegisterFields();
        view.showBusy(false);
        stage.setScene(view.getScene());
    }
}
