package pt.isec.client.screen;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;

/**
 * Tela de autenticação (Login/Registo)
 * Primeira tela que o utilizador vê
 */
public class AuthenticationScreen {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private Scene scene;

    // UI Components
    private TabPane tabPane;
    private TextField loginEmailField;
    private PasswordField loginPasswordField;
    private Button loginButton;
    private ProgressIndicator loginProgress;
    private Label loginStatusLabel;

    // Registo Estudante
    private TextField studentNumberField;
    private TextField studentNameField;
    private TextField studentEmailField;
    private PasswordField studentPasswordField;
    private Button registerStudentButton;
    private Label studentStatusLabel;

    // Registo Docente
    private TextField teacherCodeField;
    private TextField teacherNameField;
    private TextField teacherEmailField;
    private PasswordField teacherPasswordField;
    private Button registerTeacherButton;
    private Label teacherStatusLabel;

    public AuthenticationScreen(Stage stage, ClientManager clientManager, ClientApplication application) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        initializeUI();
        setupPropertyChangeListeners();
    }

    /**
     * Configura listeners para mudanças de estado do ClientService
     */
    private void setupPropertyChangeListeners() {
        // 1) Login concluído → abrir dashboard
        clientManager.getService().addPropertyChangeListener(
                pt.isec.client.ClientService.PROP_AUTHENTICATED,
                evt -> {
                    boolean authenticated = (boolean) evt.getNewValue();
                    if (authenticated) {
                        Platform.runLater(() -> {
                            String userType = clientManager.getService().getUserType();
                            String email    = clientManager.getService().getUserEmail();
                            openDashboard(userType, email);
                        });
                    }
                }
        );

        // 2) Estado de ligação → atualizar label
        clientManager.getService().addPropertyChangeListener(
                pt.isec.client.ClientService.PROP_CONNECTION_STATUS,
                evt -> Platform.runLater(() -> updateConnectionStatus(String.valueOf(evt.getNewValue())))
        );
    }

    /**
     * Atualiza o status de conexão na UI
     */
    private void updateConnectionStatus(String status) {
        switch (status) {
            case "CONNECTING":
                loginStatusLabel.setText("A conectar ao servidor...");
                loginStatusLabel.setTextFill(Color.web("#3498db"));
                break;
            case "CONNECTED":
                loginStatusLabel.setText("Conectado ao servidor");
                loginStatusLabel.setTextFill(Color.web("#27ae60"));
                break;
            case "AUTHENTICATING":
                loginStatusLabel.setText("A autenticar...");
                loginStatusLabel.setTextFill(Color.web("#3498db"));
                break;
            case "AUTHENTICATED":
                loginStatusLabel.setText("✓ Autenticação bem-sucedida!");
                loginStatusLabel.setTextFill(Color.web("#27ae60"));
                break;
            case "DISCONNECTED":
                loginStatusLabel.setText("Desconectado do servidor");
                loginStatusLabel.setTextFill(Color.web("#e74c3c"));
                break;
        }
    }

    private void initializeUI() {
        // Root layout
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Header
        Label titleLabel = new Label("Sistema de Gestão de Perguntas");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#2c3e50"));

        Label subtitleLabel = new Label("Bem-vindo! Por favor, autentique-se");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.web("#7f8c8d"));

        VBox header = new VBox(5, titleLabel, subtitleLabel);
        header.setAlignment(Pos.CENTER);

        // TabPane com Login e Registo
        tabPane = new TabPane();
        tabPane.setMaxWidth(500);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab de Login
        Tab loginTab = new Tab("Login");
        loginTab.setContent(createLoginPane());

        // Tab de Registo Estudante
        Tab registerStudentTab = new Tab("Registar Estudante");
        registerStudentTab.setContent(createRegisterStudentPane());

        // Tab de Registo Docente
        Tab registerTeacherTab = new Tab("Registar Docente");
        registerTeacherTab.setContent(createRegisterTeacherPane());

        tabPane.getTabs().addAll(loginTab, registerStudentTab, registerTeacherTab);

        // Status de conexão
        Label connectionLabel = new Label("A conectar ao servidor...");
        connectionLabel.setFont(Font.font("Arial", 12));
        connectionLabel.setTextFill(Color.web("#95a5a6"));

        root.getChildren().addAll(header, tabPane, connectionLabel);

        // Iniciar "estado" da ligação (texto)
        new Thread(() -> connectToServer(connectionLabel)).start();

        scene = new Scene(root, 900, 600);

        // Carregar CSS se disponível
        try {
            var cssUrl = getClass().getResource("/styles/authentication.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            // CSS opcional - continuar sem ele
        }
    }

    /**
     * Cria o painel de login
     */
    private VBox createLoginPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(30));
        pane.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Entrar no Sistema");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        // Email
        Label emailLabel = new Label("Email:");
        emailLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        loginEmailField = new TextField();
        loginEmailField.setPromptText("exemplo@isec.pt");
        loginEmailField.setPrefWidth(300);

        // Password
        Label passwordLabel = new Label("Password:");
        passwordLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");
        loginPasswordField.setPrefWidth(300);

        // Enter key para fazer login
        loginPasswordField.setOnAction(e -> handleLogin());

        // Botão de login
        loginButton = new Button("Entrar");
        loginButton.setPrefWidth(150);
        loginButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        loginButton.setOnAction(e -> handleLogin());

        // Progress indicator
        loginProgress = new ProgressIndicator();
        loginProgress.setMaxSize(30, 30);
        loginProgress.setVisible(false);

        // Status label
        loginStatusLabel = new Label();
        loginStatusLabel.setWrapText(true);

        pane.getChildren().addAll(
                title,
                new VBox(5, emailLabel, loginEmailField),
                new VBox(5, passwordLabel, loginPasswordField),
                loginButton,
                loginProgress,
                loginStatusLabel
        );

        return pane;
    }

    /**
     * Cria o painel de registo de estudante
     */
    private VBox createRegisterStudentPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(30));
        pane.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Registar Novo Estudante");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        // Número de estudante
        Label numberLabel = new Label("Número de Estudante:");
        numberLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        studentNumberField = new TextField();
        studentNumberField.setPromptText("Ex: 123456");
        studentNumberField.setPrefWidth(300);

        // Nome
        Label nameLabel = new Label("Nome:");
        nameLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        studentNameField = new TextField();
        studentNameField.setPromptText("Nome completo");
        studentNameField.setPrefWidth(300);

        // Email
        Label emailLabel = new Label("Email:");
        emailLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        studentEmailField = new TextField();
        studentEmailField.setPromptText("exemplo@isec.pt");
        studentEmailField.setPrefWidth(300);

        // Password
        Label passwordLabel = new Label("Password:");
        passwordLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        studentPasswordField = new PasswordField();
        studentPasswordField.setPromptText("Mínimo 6 caracteres");
        studentPasswordField.setPrefWidth(300);

        // Botão de registo
        registerStudentButton = new Button("Registar");
        registerStudentButton.setPrefWidth(150);
        registerStudentButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        registerStudentButton.setOnAction(e -> handleRegisterStudent());

        // Status label
        studentStatusLabel = new Label();
        studentStatusLabel.setWrapText(true);

        pane.getChildren().addAll(
                title,
                new VBox(5, numberLabel, studentNumberField),
                new VBox(5, nameLabel, studentNameField),
                new VBox(5, emailLabel, studentEmailField),
                new VBox(5, passwordLabel, studentPasswordField),
                registerStudentButton,
                studentStatusLabel
        );

        return pane;
    }

    /**
     * Cria o painel de registo de docente
     */
    private VBox createRegisterTeacherPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(30));
        pane.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Registar Novo Docente");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        // Código de docente
        Label codeLabel = new Label("Código de Registo de Docentes:");
        codeLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        teacherCodeField = new PasswordField();
        teacherCodeField.setPromptText("Código fornecido pela instituição");
        teacherCodeField.setPrefWidth(300);

        // Nome
        Label nameLabel = new Label("Nome:");
        nameLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        teacherNameField = new TextField();
        teacherNameField.setPromptText("Nome completo");
        teacherNameField.setPrefWidth(300);

        // Email
        Label emailLabel = new Label("Email:");
        emailLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        teacherEmailField = new TextField();
        teacherEmailField.setPromptText("exemplo@isec.pt");
        teacherEmailField.setPrefWidth(300);

        // Password
        Label passwordLabel = new Label("Password:");
        passwordLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        teacherPasswordField = new PasswordField();
        teacherPasswordField.setPromptText("Mínimo 6 caracteres");
        teacherPasswordField.setPrefWidth(300);

        // Botão de registo
        registerTeacherButton = new Button("Registar");
        registerTeacherButton.setPrefWidth(150);
        registerTeacherButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        registerTeacherButton.setOnAction(e -> handleRegisterTeacher());

        // Status label
        teacherStatusLabel = new Label();
        teacherStatusLabel.setWrapText(true);

        pane.getChildren().addAll(
                title,
                new VBox(5, codeLabel, teacherCodeField),
                new VBox(5, nameLabel, teacherNameField),
                new VBox(5, emailLabel, teacherEmailField),
                new VBox(5, passwordLabel, teacherPasswordField),
                registerTeacherButton,
                teacherStatusLabel
        );

        return pane;
    }

    /**
     * Apenas atualiza o texto de “estado” inicial
     */
    private void connectToServer(Label statusLabel) {
        Platform.runLater(() -> {
            statusLabel.setText("Pronto para autenticação");
            statusLabel.setTextFill(Color.web("#27ae60"));
        });
    }

    /**
     * Trata o login
     */
    private void handleLogin() {
        String email = loginEmailField.getText().trim();
        String password = loginPasswordField.getText();

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
        loginButton.setDisable(true);
        loginProgress.setVisible(true);
        loginStatusLabel.setText("A conectar ao servidor...");
        loginStatusLabel.setTextFill(Color.web("#3498db"));

        // Autenticar em background thread
        new Thread(() -> {
            try {
                // Garante que o serviço está a correr (discovery + TCP + threads)
                if (!clientManager.getService().isRunning()) {
                    clientManager.start();
                }

                Platform.runLater(() -> loginStatusLabel.setText("A autenticar..."));

                // Usar AuthClientService para autenticar
                pt.isec.common.dto.auth.LoginResponseDTO response =
                        clientManager.getAuthService().login(email, password);

                if (response != null) {
                    // sucesso – ClientService deverá ter feito setAuthenticated/userType/userEmail
                    Platform.runLater(() -> {
                        loginProgress.setVisible(false);
                        // o listener de PROP_AUTHENTICATED é que abre o dashboard
                    });
                } else {
                    Platform.runLater(() -> {
                        showLoginError("Credenciais inválidas");
                        loginButton.setDisable(false);
                        loginProgress.setVisible(false);
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoginError("Erro na autenticação: " + e.getMessage());
                    loginButton.setDisable(false);
                    loginProgress.setVisible(false);
                });
            }
        }, "LoginThread").start();
    }

    /**
     * Trata o registo de estudante
     */
    private void handleRegisterStudent() {
        String number = studentNumberField.getText().trim();
        String name = studentNameField.getText().trim();
        String email = studentEmailField.getText().trim();
        String password = studentPasswordField.getText();

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

        registerStudentButton.setDisable(true);
        studentStatusLabel.setText("A registar...");
        studentStatusLabel.setTextFill(Color.web("#3498db"));

        // Registar em background (simulado)
        new Thread(() -> {
            try {
                // TODO: Implementar registo real no servidor
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    showStudentSuccess("Registo bem-sucedido! Por favor, faça login.");
                    registerStudentButton.setDisable(false);
                    // Mudar para tab de login
                    tabPane.getSelectionModel().select(0);
                    loginEmailField.setText(email);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showStudentError("Erro no registo: " + e.getMessage());
                    registerStudentButton.setDisable(false);
                });
            }
        }, "RegisterStudentThread").start();
    }

    /**
     * Trata o registo de docente
     */
    private void handleRegisterTeacher() {
        String code = teacherCodeField.getText().trim();
        String name = teacherNameField.getText().trim();
        String email = teacherEmailField.getText().trim();
        String password = teacherPasswordField.getText();

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

        registerTeacherButton.setDisable(true);
        teacherStatusLabel.setText("A registar...");
        teacherStatusLabel.setTextFill(Color.web("#3498db"));

        // Registar em background (simulado)
        new Thread(() -> {
            try {
                // TODO: Implementar registo real no servidor
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    showTeacherSuccess("Registo bem-sucedido! Por favor, faça login.");
                    registerTeacherButton.setDisable(false);
                    // Mudar para tab de login
                    tabPane.getSelectionModel().select(0);
                    loginEmailField.setText(email);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showTeacherError("Erro no registo: " + e.getMessage());
                    registerTeacherButton.setDisable(false);
                });
            }
        }, "RegisterTeacherThread").start();
    }

    /**
     * Abre o dashboard apropriado após autenticação
     */
    private void openDashboard(String userType, String email) {
        if ("TEACHER".equalsIgnoreCase(userType)) {
            TeacherDashboard dashboard = new TeacherDashboard(stage, clientManager, email);
            dashboard.show();
        } else {
            StudentDashboard dashboard = new StudentDashboard(stage, clientManager, email);
            dashboard.show();
        }
    }

    // Métodos auxiliares para mensagens
    private void showLoginError(String message) {
        loginStatusLabel.setText("❌ " + message);
        loginStatusLabel.setTextFill(Color.web("#e74c3c"));
    }

    private void showStudentError(String message) {
        studentStatusLabel.setText("❌ " + message);
        studentStatusLabel.setTextFill(Color.web("#e74c3c"));
    }

    private void showStudentSuccess(String message) {
        studentStatusLabel.setText("✓ " + message);
        studentStatusLabel.setTextFill(Color.web("#27ae60"));
    }

    private void showTeacherError(String message) {
        teacherStatusLabel.setText("❌ " + message);
        teacherStatusLabel.setTextFill(Color.web("#e74c3c"));
    }

    private void showTeacherSuccess(String message) {
        teacherStatusLabel.setText("✓ " + message);
        teacherStatusLabel.setTextFill(Color.web("#27ae60"));
    }

    public void show() {
        stage.setScene(scene);
    }
}
