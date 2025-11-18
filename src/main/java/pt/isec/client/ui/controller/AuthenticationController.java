package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.AuthenticationView;
import pt.isec.common.dto.auth.LoginResponseDTO;

/**
 * Controlador da interface de autenticação. Contém lógica de validação,
 * chamadas aos serviços de autenticação e navegação.
 */
public class AuthenticationController {
    private enum Mode { LOGIN, REGISTER }
    private static final Color BLUE = Color.web("#3498db");
    private static final Color RED  = Color.web("#A01316");
    private static final long TIMEOUT_MS = 10000;
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

    /** Observa quando a autenticação é bem-sucedida para abrir o dashboard */
    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();
        service.addPropertyChangeListener(ClientService.PROP_AUTHENTICATED, evt -> {
            boolean authenticated = (boolean) evt.getNewValue();
            if (authenticated) {
                Platform.runLater(() -> {
                    String userType = service.getUserType();
                    String email    = service.getUserEmail();
                    openDashboard(userType, email);
                });
            }
        });
    }

    /** Alterna entre os modos login e registo */
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

    /** Handler para o botão/Enter de login */
    public void onLogin() {
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
        view.setLoginStatus("A conectar ao servidor...", BLUE, true, false);
        scheduleTimeout("login");
        new Thread(() -> {
            try {
                if (!ensureConnected("login")) return;
                Platform.runLater(() -> view.setLoginStatus("A autenticar...", BLUE, true, false));
                LoginResponseDTO response = clientManager.getAuthService().login(email, password);
                if (response != null) {
                    Platform.runLater(() -> view.setLoginStatus("Aguarde...", BLUE, false, false));
                } else {
                    Platform.runLater(() -> showLoginError("Credenciais inválidas."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showLoginError("Erro na autenticação: " + e.getMessage()));
            } finally {
                setAuthBusy(false);
            }
        }, "LoginThread").start();
    }

    /** Handler para o botão de registo */
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
        scheduleTimeout("register");
        new Thread(() -> {
            try {
                if (!ensureConnected("register")) return;
                boolean ok;
                if ("STUDENT".equalsIgnoreCase(type)) {
                    int number;
                    try {
                        number = Integer.parseInt(extra);
                    } catch (NumberFormatException e) {
                        Platform.runLater(() -> showRegisterError("Número de estudante inválido."));
                        return;
                    }
                    ok = clientManager.getAuthService().registerStudent(name, email, password, number);
                } else {
                    ok = clientManager.getAuthService().registerTeacher(name, email, password, extra);
                }
                if (ok) {
                    Platform.runLater(() -> {
                        view.setRegisterStatus("Registo bem-sucedido! Já pode fazer login.", BLUE, false);
                        view.clearRegisterFields();
                        view.clearLoginFields();
                        mode = Mode.LOGIN;
                        view.showLoginMode();
                    });
                } else {
                    Platform.runLater(() -> showRegisterError("Erro no registo. Tente novamente."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showRegisterError("Erro no registo: " + e.getMessage()));
            } finally {
                setAuthBusy(false);
            }
        }, "RegisterThread").start();
    }

    /** Actualiza a label do campo extra de registo conforme o tipo (estudante ou docente) */
    public void onRegisterTypeChanged(String type) {
        if ("STUDENT".equalsIgnoreCase(type)) {
            view.setRegisterExtraLabel("Número de Estudante");
        } else {
            view.setRegisterExtraLabel("Código de Docente");
        }
    }

    /** Garante que o cliente está conectado ao servidor. Se não estiver, tenta iniciar o serviço e espera até 3 segundos. */
    private boolean ensureConnected(String context) {
        ClientService service = clientManager.getService();
        if (!service.isRunning()) {
            clientManager.start();
        }
        int attempts = 0;
        while (attempts < 20 && !service.isRunning()) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
        if (!service.isRunning()) {
            Platform.runLater(() -> {
                String msg = "Não foi possível contactar o servidor.\nVerifique se a diretoria e o servidor estão em execução.";
                if ("login".equalsIgnoreCase(context)) {
                    showLoginError(msg);
                } else {
                    showRegisterError(msg);
                }
            });
            return false;
        }
        return true;
    }

    /** Liga/desliga a flag de operação e actualiza a view para bloquear/desbloquear os campos */
    private void setAuthBusy(boolean busy) {
        authBusy = busy;
        Platform.runLater(() -> view.setAuthBusy(busy));
    }

    /** Agenda um timeout de 10 segundos para abortar operações longas */
    private void scheduleTimeout(String context) {
        new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException ignored) { }
            if (authBusy) {
                Platform.runLater(() -> {
                    if ("login".equalsIgnoreCase(context)) {
                        showLoginError("Tempo esgotado. Tente novamente.");
                    } else {
                        showRegisterError("Tempo esgotado. Tente novamente.");
                    }
                    setAuthBusy(false);
                });
            }
        }, "AuthTimeoutThread").start();
    }

    /** Abre o dashboard adequado conforme o tipo de utilizador após autenticação */
    private void openDashboard(String userType, String email) {
        view.clearLoginFields();
        view.clearRegisterFields();
        if ("TEACHER".equalsIgnoreCase(userType)) {
            application.showTeacherDashboard(email);
        } else {
            application.showStudentDashboard(email);
        }
    }

    /** Mostra mensagem de erro no login e reabilita o botão */
    private void showLoginError(String message) {
        view.setLoginStatus("❌ " + message, RED, false, true);
    }

    /** Mostra mensagem de erro no registo e reabilita o botão */
    private void showRegisterError(String message) {
        view.setRegisterStatus("❌ " + message, RED, true);
    }

    /** Mostra o ecrã de login/registo e reinicia estados */
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
