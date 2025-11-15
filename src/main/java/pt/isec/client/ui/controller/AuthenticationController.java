package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.AuthenticationView;
import pt.isec.common.dto.auth.LoginResponseDTO;

public class AuthenticationController {

    private enum Mode { LOGIN, REGISTER }

    private static final Color BLUE = Color.web("#3498db");  // “processo”
    private static final Color RED  = Color.web("#A01316");  // erro

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;

    private final AuthenticationView view;
    private Mode mode = Mode.LOGIN;

    // indica se há um login/registo em curso
    private volatile boolean authBusy = false;

    public AuthenticationController(Stage stage,
                                    ClientManager clientManager,
                                    ClientApplication application) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;

        this.view = new AuthenticationView();
        this.view.createView();
        this.view.registerHandlers(this);

        view.showLoginMode();
        setupPropertyChangeListeners();
    }

    // --------------------------------------------------------
    // Listeners de estado do serviço
    // --------------------------------------------------------

    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        // Quando autenticar com sucesso, abre dashboard
        service.addPropertyChangeListener(
                ClientService.PROP_AUTHENTICATED,
                evt -> {
                    boolean authenticated = (boolean) evt.getNewValue();
                    if (authenticated) {
                        Platform.runLater(() -> {
                            String userType = service.getUserType();
                            String email = service.getUserEmail();
                            openDashboard(userType, email);
                        });
                    }
                }
        );
    }

    // bloqueia / desbloqueia interação
    private void setAuthBusy(boolean busy) {
        authBusy = busy;
        Platform.runLater(() -> view.setAuthBusy(busy));
    }

    // --------------------------------------------------------
    // Helper para garantir ligação
    // --------------------------------------------------------

    private boolean ensureConnected(String context) {
        ClientService service = clientManager.getService();

        if (!service.isRunning()) {
            clientManager.start();
        }

        // Espera até ~3s (20 * 150ms) pela flag isRunning()
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
                String msg = "Não foi possível contactar o servidor.\n" +
                        "Verifique se a diretoria e o servidor estão em execução.";
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

    // --------------------------------------------------------
    // Handlers vindos da View
    // --------------------------------------------------------

    public void onToggleMode() {
        if (authBusy)
            return; // não deixar trocar de ecrã no meio de login/registo

        if (mode == Mode.LOGIN) {
            mode = Mode.REGISTER;
            view.showRegisterMode();
        } else {
            mode = Mode.LOGIN;
            view.showLoginMode();
        }
    }

    public void onLogin() {
        if (mode != Mode.LOGIN)
            return;

        if (authBusy)
            return;

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

        new Thread(() -> {
            try {
                if (!ensureConnected("login")) {
                    return;
                }

                Platform.runLater(() ->
                        view.setLoginStatus("A autenticar...", BLUE, true, false)
                );

                LoginResponseDTO response =
                        clientManager.getAuthService().login(email, password);

                if (response != null) {
                    Platform.runLater(() ->
                            view.setLoginStatus("Aguarde...", BLUE, false, false)
                    );
                } else {
                    Platform.runLater(() ->
                            showLoginError("Credenciais inválidas")
                    );
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        showLoginError("Erro na autenticação: " + e.getMessage())
                );
            } finally {
                setAuthBusy(false);
            }
        }, "LoginThread").start();
    }

    public void onRegister() {
        if (mode != Mode.REGISTER)
            return;

        if (authBusy)
            return;

        String type = view.getSelectedRegisterType(); // STUDENT / TEACHER
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

        new Thread(() -> {
            try {
                if (!ensureConnected("register")) {
                    return;
                }

                boolean ok;

                if ("STUDENT".equalsIgnoreCase(type)) {
                    int number;
                    try {
                        number = Integer.parseInt(extra);
                    } catch (NumberFormatException e) {
                        Platform.runLater(() ->
                                showRegisterError("Número de estudante inválido.")
                        );
                        return;
                    }
                    ok = clientManager.getAuthService()
                            .registerStudent(name, email, password, number);
                } else {
                    ok = clientManager.getAuthService()
                            .registerTeacher(name, email, password, extra);
                }

                if (ok) {
                    Platform.runLater(() -> {
                        view.setRegisterStatus(
                                "Registo bem-sucedido! Já pode fazer login.",
                                BLUE,
                                true
                        );
                        view.prefillLoginEmail(email);
                        mode = Mode.LOGIN;
                        view.showLoginMode();
                    });
                } else {
                    Platform.runLater(() ->
                            showRegisterError("Erro no registo. Tente novamente.")
                    );
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        showRegisterError("Erro no registo: " + e.getMessage())
                );
            } finally {
                setAuthBusy(false);
            }
        }, "RegisterThread").start();
    }

    public void onRegisterTypeChanged(String type) {
        if ("STUDENT".equalsIgnoreCase(type)) {
            view.setRegisterExtraLabel("Número de Estudante");
        } else {
            view.setRegisterExtraLabel("Código de Docente");
        }
    }

    // --------------------------------------------------------
    // Navegação
    // --------------------------------------------------------

    private void openDashboard(String userType, String email) {
        if ("TEACHER".equalsIgnoreCase(userType)) {
            TeacherDashboardController controller =
                    new TeacherDashboardController(stage, clientManager, application, email);
            controller.show();
        } else {
            StudentDashboardController controller =
                    new StudentDashboardController(stage, clientManager, application, email);
            controller.show();
        }
    }

    // --------------------------------------------------------
    // Helpers de feedback
    // --------------------------------------------------------

    private void showLoginError(String message) {
        view.setLoginStatus("❌ " + message, RED, false, true);
    }

    private void showRegisterError(String message) {
        view.setRegisterStatus("❌ " + message, RED, true);
    }

    // --------------------------------------------------------
    // Mostrar view
    // --------------------------------------------------------

    public void show() {
        stage.setScene(view.getScene());
    }
}
