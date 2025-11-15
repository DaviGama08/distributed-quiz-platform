package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pt.isec.client.ui.controller.AuthenticationController;

/**
 * View de autenticação (Login/Registo).
 * Responsável APENAS pela construção da UI e registo de handlers.
 */
public class AuthenticationView {

    private Scene scene;

    // Componentes comuns
    private TabPane tabPane;
    private Label connectionStatusLabel;

    // Login
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

    public AuthenticationView() {
    }

    // --------------------------------------------------------
    // Métodos principais: createView / registerHandlers / update
    // --------------------------------------------------------

    /**
     * Cria toda a interface gráfica (botões, labels, layouts, etc.)
     */
    public void createView() {
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
        connectionStatusLabel = new Label("A conectar ao servidor...");
        connectionStatusLabel.setFont(Font.font("Arial", 12));
        connectionStatusLabel.setTextFill(Color.web("#95a5a6"));

        root.getChildren().addAll(header, tabPane, connectionStatusLabel);

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
     * Regista os handlers dos componentes, delegando a lógica no controller.
     */
    public void registerHandlers(AuthenticationController controller) {
        // LOGIN
        loginPasswordField.setOnAction(e -> controller.onLogin());
        loginButton.setOnAction(e -> controller.onLogin());

        // REGISTO ESTUDANTE
        registerStudentButton.setOnAction(e -> controller.onRegisterStudent());

        // REGISTO DOCENTE
        registerTeacherButton.setOnAction(e -> controller.onRegisterTeacher());
    }

    /**
     * Método genérico para actualizar a view quando o controller assim o decidir.
     */
    public void update() {
        // Nada específico por agora.
    }

    // --------------------------------------------------------
    // Criação dos painéis (apenas UI, sem lógica)
    // --------------------------------------------------------

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

        // Botão de login
        loginButton = new Button("Entrar");
        loginButton.setPrefWidth(150);
        loginButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");

        // Progress indicator
        loginProgress = new ProgressIndicator();
        loginProgress.setMaxSize(30, 30);
        loginProgress.setVisible(false);

        // Status label
        loginStatusLabel = new Label();
        loginStatusLabel.setWrapText(true);
        loginStatusLabel.setAlignment(Pos.CENTER);
        loginStatusLabel.setTextAlignment(TextAlignment.CENTER);
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

    // --------------------------------------------------------
    // API para o Controller (getters + setters de estado)
    // --------------------------------------------------------

    public Scene getScene() {
        return scene;
    }

    // Inputs de Login
    public String getLoginEmail() {
        return loginEmailField.getText().trim();
    }

    public String getLoginPassword() {
        return loginPasswordField.getText();
    }

    // Inputs Estudante
    public String getStudentNumber() {
        return studentNumberField.getText().trim();
    }

    public String getStudentName() {
        return studentNameField.getText().trim();
    }

    public String getStudentEmail() {
        return studentEmailField.getText().trim();
    }

    public String getStudentPassword() {
        return studentPasswordField.getText();
    }

    // Inputs Docente
    public String getTeacherCode() {
        return teacherCodeField.getText().trim();
    }

    public String getTeacherName() {
        return teacherNameField.getText().trim();
    }

    public String getTeacherEmail() {
        return teacherEmailField.getText().trim();
    }

    public String getTeacherPassword() {
        return teacherPasswordField.getText();
    }

    // Estado Login
    public void setLoginStatus(String message, Color color,
                               boolean showProgress, boolean loginButtonEnabled) {
        loginStatusLabel.setText(message);
        loginStatusLabel.setTextFill(color);
        loginProgress.setVisible(showProgress);
        loginButton.setDisable(!loginButtonEnabled);
    }

    // Estado Estudante
    public void setStudentStatus(String message, Color color, boolean registerEnabled) {
        studentStatusLabel.setText(message);
        studentStatusLabel.setTextFill(color);
        registerStudentButton.setDisable(!registerEnabled);
    }

    // Estado Docente
    public void setTeacherStatus(String message, Color color, boolean registerEnabled) {
        teacherStatusLabel.setText(message);
        teacherStatusLabel.setTextFill(color);
        registerTeacherButton.setDisable(!registerEnabled);
    }

    // Estado de ligação
    public void setConnectionStatus(String message, Color color) {
        connectionStatusLabel.setText(message);
        connectionStatusLabel.setTextFill(color);
    }

    // Tab de login + preencher email
    public void switchToLoginTabAndPrefillEmail(String email) {
        tabPane.getSelectionModel().select(0);
        loginEmailField.setText(email);
    }
}
