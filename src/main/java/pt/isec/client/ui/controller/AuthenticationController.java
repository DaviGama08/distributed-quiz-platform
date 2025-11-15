package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.AuthenticationView;
import pt.isec.common.dto.auth.LoginResponseDTO;

import java.beans.PropertyChangeListener;

/**
 * Controller da autenticação.
 * Contém TODA a lógica da view: validações, chamadas aos serviços,
 * listeners, navegação para dashboards, etc.
 */
public class AuthenticationController {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;

    private final AuthenticationView view;

    public AuthenticationController(Stage stage, ClientManager clientManager, ClientApplication application) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;

        this.view = new AuthenticationView();
        view.createView();
        view.registerHandlers(this);

        setupPropertyChangeListeners();
        startInitialConnectionStatusUpdate();
    }

    // --------------------------------------------------------
    // Externamente: mostrar esta view
    // --------------------------------------------------------

    public void show() {
        stage.setScene(view.getScene());
    }

    // --------------------------------------------------------
    // PropertyChangeListeners do ClientService
    // --------------------------------------------------------

    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        // 1) Login concluído → abrir dashboard
        PropertyChangeListener authListener = evt -> {
            boolean authenticated = (boolean) evt.getNewValue();
            if (authenticated) {
                Platform.runLater(() -> {
                    String userType = service.getUserType();
                    String email = service.getUserEmail();
                    openDashboard(userType, email);
                });
            }
        };

        service.addPropertyChangeListener(ClientService.PROP_AUTHENTICATED, authListener);

        // 2) Estado de ligação → atualizar label
        PropertyChangeListener connListener = evt ->
                Platform.runLater(() -> updateConnectionStatus(String.valueOf(evt.getNewValue())));

        service.addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connListener);
    }

    private void startInitialConnectionStatusUpdate() {
        new Thread(this::connectToServer, "ConnectToServerThread").start();
    }

    /**
     * Apenas atualiza o texto de “estado” inicial (lógica fica aqui).
     */
    private void connectToServer() {
        Platform.runLater(() -> {
            view.setConnectionStatus("Pronto para autenticação", Color.web("#27ae60"));
            view.update();
        });
    }

    /**
     * Converte o estado de ligação em texto e cor para a view.
     */
    private void updateConnectionStatus(String status) {
        String message;
        Color color = switch (status) {
            case "CONNECTING" -> {
                message = "A conectar ao servidor...";
                yield Color.web("#3498db");
            }
            case "CONNECTED" -> {
                message = "Conectado ao servidor";
                yield Color.web("#27ae60");
            }
            case "AUTHENTICATING" -> {
                message = "A autenticar...";
                yield Color.web("#3498db");
            }
            case "AUTHENTICATED" -> {
                message = "✓ Autenticação bem-sucedida!";
                yield Color.web("#27ae60");
            }
            default -> {
                message = "Desconectado do servidor";
                yield Color.web("#e74c3c");
            }
        };

        view.setConnectionStatus(message, color);
        view.update();
    }

    // --------------------------------------------------------
    // Handlers chamados pela view (registerHandlers)
    // --------------------------------------------------------

    /**
     * Handler de login – chamado pela view.
     */
    public void onLogin() {
        String email = view.getLoginEmail();
        String password = view.getLoginPassword();

        // Validações
        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Por favor, preencha todos os campos.");
            return;
        }

        if (!email.contains("@")) {
            showLoginError("Email inválido.");
            return;
        }

        // Mostrar progress
        view.setLoginStatus("A conectar ao servidor...",
                Color.web("#3498db"),
                true,
                false);

        // Autenticar em background thread
        new Thread(() -> {
            try {
                // Garante que o serviço está a correr (discovery + TCP + threads)
                if (!clientManager.getService().isRunning()) {
                    if(!clientManager.start()){
                        Platform.runLater(()->
                                view.setLoginStatus("Impossibilidade de contactar o servidor\n" +
                                        "Verifique se a diretoria e o servidor estão a correr!",
                                        Color.web("#e74c3c"),
                                        false,
                                        true));

                        view.update();
                    }
                    return;
                }

                Platform.runLater(() ->
                        view.setLoginStatus("A autenticar...",
                                Color.web("#3498db"),
                                true,
                                false));

                // Usar AuthClientService para autenticar
                LoginResponseDTO response =
                        clientManager.getAuthService().login(email, password);

                if (response != null) {
                    // sucesso – ClientService deverá ter feito setAuthenticated/userType/userEmail
                    Platform.runLater(() -> {
                        view.setLoginStatus("Aguarde...",
                                Color.web("#3498db"),
                                false,
                                false);
                        view.update();
                    });
                } else {
                    Platform.runLater(() -> {
                        showLoginError("Credenciais inválidas");
                        view.setLoginStatus("Credenciais inválidas",
                                Color.web("#e74c3c"),
                                false,
                                true);
                        view.update();
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoginError("Erro na autenticação: " + e.getMessage());
                    view.setLoginStatus("Erro na autenticação: " + e.getMessage(),
                            Color.web("#e74c3c"),
                            false,
                            true);
                    view.update();
                });
            }
        }, "LoginThread").start();
    }

    /**
     * Handler de registo de estudante – chamado pela view.
     */
    public void onRegisterStudent() {
        String number = view.getStudentNumber();
        String name = view.getStudentName();
        String email = view.getStudentEmail();
        String password = view.getStudentPassword();

        // Validações
        if (number.isEmpty() || name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showStudentError("Por favor, preencha todos os campos.");
            return;
        }

        if (!email.contains("@")) {
            showStudentError("Email inválido.");
            return;
        }

        if (password.length() < 6) {
            showStudentError("Password deve ter no mínimo 6 caracteres.");
            return;
        }

        try {
            Integer.parseInt(number);
        } catch (NumberFormatException e) {
            showStudentError("Número de estudante inválido.");
            return;
        }

        view.setStudentStatus("A registar...",
                Color.web("#3498db"),
                false);

        // Registar em background (simulado)
        new Thread(() -> {
            try {
                // TODO: Implementar registo real no servidor
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    showStudentSuccess("Registo bem-sucedido! Por favor, faça login.");
                    view.setStudentStatus("✓ Registo bem-sucedido! Por favor, faça login.",
                            Color.web("#27ae60"),
                            true);
                    view.switchToLoginTabAndPrefillEmail(email);
                    view.update();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showStudentError("Erro no registo: " + e.getMessage());
                    view.setStudentStatus("Erro no registo: " + e.getMessage(),
                            Color.web("#e74c3c"),
                            true);
                    view.update();
                });
            }
        }, "RegisterStudentThread").start();
    }

    /**
     * Handler de registo de docente – chamado pela view.
     */
    public void onRegisterTeacher() {
        String code = view.getTeacherCode();
        String name = view.getTeacherName();
        String email = view.getTeacherEmail();
        String password = view.getTeacherPassword();

        // Validações
        if (code.isEmpty() || name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showTeacherError("Por favor, preencha todos os campos.");
            return;
        }

        if (!email.contains("@")) {
            showTeacherError("Email inválido.");
            return;
        }

        if (password.length() < 6) {
            showTeacherError("Password deve ter no mínimo 6 caracteres.");
            return;
        }

        view.setTeacherStatus("A registar...",
                Color.web("#3498db"),
                false);

        // Registar em background (simulado)
        new Thread(() -> {
            try {
                // TODO: Implementar registo real no servidor
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    showTeacherSuccess("Registo bem-sucedido! Por favor, faça login.");
                    view.setTeacherStatus("✓ Registo bem-sucedido! Por favor, faça login.",
                            Color.web("#27ae60"),
                            true);
                    view.switchToLoginTabAndPrefillEmail(email);
                    view.update();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showTeacherError("Erro no registo: " + e.getMessage());
                    view.setTeacherStatus("Erro no registo: " + e.getMessage(),
                            Color.web("#e74c3c"),
                            true);
                    view.update();
                });
            }
        }, "RegisterTeacherThread").start();
    }

    // --------------------------------------------------------
    // Navegação
    // --------------------------------------------------------

    private void openDashboard(String userType, String email) {
        if ("TEACHER".equalsIgnoreCase(userType)) {
            TeacherDashboardController dashboard =
                    new TeacherDashboardController(stage, clientManager, application, email);
            dashboard.show();
        } else {
            StudentDashboardController dashboard =
                    new StudentDashboardController(stage, clientManager, application, email);
            dashboard.show();
        }
    }

    // --------------------------------------------------------
    // Helpers de mensagens (lógica aqui, view só mostra)
    // --------------------------------------------------------

    private void showLoginError(String message) {
        view.setLoginStatus("❌ " + message, Color.web("#e74c3c"), false, true);
    }

    private void showStudentError(String message) {
        view.setStudentStatus("❌ " + message, Color.web("#e74c3c"), true);
    }

    private void showStudentSuccess(String message) {
        view.setStudentStatus("✓ " + message, Color.web("#27ae60"), true);
    }

    private void showTeacherError(String message) {
        view.setTeacherStatus("❌ " + message, Color.web("#e74c3c"), true);
    }

    private void showTeacherSuccess(String message) {
        view.setTeacherStatus("✓ " + message, Color.web("#27ae60"), true);
    }
}
