package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import pt.isec.client.ui.controller.AuthenticationController;

import java.util.Objects;

public class AuthenticationView {

    private Scene scene;

    // botão para alternar login/registro (lado esquerdo)
    private Button toggleModeButton;

    // títulos/subtítulos do lado direito
    private Label rightTitleLabel;
    private Label rightSubtitleLabel;

    // contentores de formulário
    private VBox loginFormBox;
    private VBox registerFormBox;

    // Login
    private TextField loginEmailField;
    private PasswordField loginPasswordField;
    private Button loginButton;
    private ProgressIndicator loginProgress;
    private Label loginStatusLabel;

    // Registo
    private RadioButton rbStudent;
    private RadioButton rbTeacher;
    private Label registerExtraLabel;
    private TextField registerNameField;
    private TextField registerEmailField;
    private PasswordField registerPasswordField;
    private TextField registerExtraField;
    private Button registerButton;
    private Label registerStatusLabel;

    public AuthenticationView() { }

    // --------------------------------------------------------
    // Criação da View
    // --------------------------------------------------------

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("auth-root");

        HBox mainContent = new HBox();
        mainContent.setSpacing(0);

        VBox leftPane = createLeftPane();
        VBox rightPane = createRightPane();

        HBox.setHgrow(leftPane, Priority.ALWAYS);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        mainContent.getChildren().addAll(leftPane, rightPane);
        root.setCenter(mainContent);

        scene = new Scene(root, 900, 600);

        try {
            var cssUrl = getClass().getResource("/styles/authentication.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) { }
    }

    public void registerHandlers(AuthenticationController controller) {
        toggleModeButton.setOnAction(e -> controller.onToggleMode());

        loginPasswordField.setOnAction(e -> controller.onLogin());
        loginButton.setOnAction(e -> controller.onLogin());

        registerButton.setOnAction(e -> controller.onRegister());
        rbStudent.setOnAction(e -> controller.onRegisterTypeChanged("STUDENT"));
        rbTeacher.setOnAction(e -> controller.onRegisterTypeChanged("TEACHER"));
    }

    public void update() {
        // por enquanto nada específico
    }

    // --------------------------------------------------------
    // LEFT PANE
    // --------------------------------------------------------

    private VBox createLeftPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("left-pane");
        pane.setPrefWidth(380);

        ImageView logoView;
        try {
            Image logo = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/imgs/logo.png"),
                    "logo.png não encontrado em resources"
            ));
            logoView = new ImageView(logo);
            logoView.setPreserveRatio(true);
            logoView.setFitHeight(200);
            logoView.setFitWidth(200);
        } catch (Exception e) {
            logoView = new ImageView();
        }

        Separator divider = new Separator();
        divider.setPrefWidth(260);
        divider.setOpacity(0.7);

        Label welcomeTitle = new Label("Bem-vindo de volta!");
        welcomeTitle.getStyleClass().add("left-title");
        welcomeTitle.setWrapText(true);
        welcomeTitle.setTextAlignment(TextAlignment.CENTER);
        welcomeTitle.setMaxWidth(280);

        Label welcomeText = new Label(
                "Para continuar ligado ao sistema,\n" +
                        "autentique-se ou crie uma nova conta."
        );
        welcomeText.getStyleClass().add("left-subtitle");
        welcomeText.setWrapText(true);
        welcomeText.setTextAlignment(TextAlignment.CENTER);
        welcomeText.setMaxWidth(300);

        toggleModeButton = new Button("CRIAR CONTA");
        toggleModeButton.getStyleClass().add("toggle-mode-button");
        toggleModeButton.setPrefWidth(220);

        VBox inner = new VBox(28, logoView, divider, welcomeTitle, welcomeText, toggleModeButton);
        inner.setAlignment(Pos.CENTER);
        inner.getStyleClass().add("left-inner");
        VBox.setVgrow(inner, Priority.ALWAYS);

        pane.getChildren().add(inner);
        return pane;
    }

    // --------------------------------------------------------
    // RIGHT PANE
    // --------------------------------------------------------

    private VBox createRightPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("right-pane");
        pane.setPadding(new Insets(40));
        pane.setAlignment(Pos.CENTER);
        pane.setPrefWidth(560);

        rightTitleLabel = new Label("Entrar no Sistema");
        rightTitleLabel.getStyleClass().add("right-title");

        rightSubtitleLabel = new Label("Use o seu email e password para entrar.");
        rightSubtitleLabel.getStyleClass().add("right-subtitle");

        VBox header = new VBox(5, rightTitleLabel, rightSubtitleLabel);
        header.setAlignment(Pos.CENTER);

        StackPane formsContainer = new StackPane();
        formsContainer.setPadding(new Insets(20, 0, 0, 0));

        loginFormBox = createLoginForm();
        registerFormBox = createRegisterForm();
        formsContainer.getChildren().addAll(registerFormBox, loginFormBox);

        VBox centerBox = new VBox(30, header, formsContainer);
        centerBox.setAlignment(Pos.CENTER);

        VBox.setVgrow(centerBox, Priority.ALWAYS);
        pane.getChildren().add(centerBox);

        showLoginMode();
        return pane;
    }

    private VBox createLoginForm() {
        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);
        form.setPadding(new Insets(10));

        loginEmailField = new TextField();
        loginEmailField.setPromptText("Email (ex: xxx@isec.pt)");
        loginEmailField.setPrefWidth(340);

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");
        loginPasswordField.setPrefWidth(340);

        loginButton = new Button("ENTRAR");
        loginButton.getStyleClass().add("primary-pill-button");
        loginButton.setPrefWidth(220);

        loginProgress = new ProgressIndicator();
        loginProgress.setMaxSize(30, 30);
        loginProgress.setVisible(false);

        loginStatusLabel = new Label();
        loginStatusLabel.setWrapText(true);
        loginStatusLabel.setTextAlignment(TextAlignment.CENTER);
        loginStatusLabel.setAlignment(Pos.CENTER);
        loginStatusLabel.setMaxWidth(340);

        form.getChildren().addAll(
                loginEmailField,
                loginPasswordField,
                loginButton,
                loginProgress,
                loginStatusLabel
        );

        return form;
    }

    private VBox createRegisterForm() {
        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);
        form.setPadding(new Insets(10));

        ToggleGroup typeGroup = new ToggleGroup();
        rbStudent = new RadioButton("Estudante");
        rbTeacher = new RadioButton("Docente");
        rbStudent.setToggleGroup(typeGroup);
        rbTeacher.setToggleGroup(typeGroup);
        rbStudent.setSelected(true);

        HBox typeBox = new HBox(15, rbStudent, rbTeacher);
        typeBox.setAlignment(Pos.CENTER);

        registerNameField = new TextField();
        registerNameField.setPromptText("Nome completo");

        registerEmailField = new TextField();
        registerEmailField.setPromptText("Email (ex: xxx@isec.pt)");

        registerPasswordField = new PasswordField();
        registerPasswordField.setPromptText("Password (mínimo 6 caracteres)");

        registerExtraLabel = new Label("Número de Estudante");

        registerExtraField = new TextField();
        registerExtraField.setPromptText("Ex: 123456");

        registerButton = new Button("CRIAR CONTA");
        registerButton.getStyleClass().add("primary-pill-button");
        registerButton.setPrefWidth(220);

        registerStatusLabel = new Label();
        registerStatusLabel.setWrapText(true);
        registerStatusLabel.setTextAlignment(TextAlignment.CENTER);
        registerStatusLabel.setMaxWidth(340);

        form.getChildren().addAll(
                typeBox,
                registerNameField,
                registerEmailField,
                registerPasswordField,
                registerExtraLabel,
                registerExtraField,
                registerButton,
                registerStatusLabel
        );

        return form;
    }

    // --------------------------------------------------------
    // Modos
    // --------------------------------------------------------

    public void showLoginMode() {
        rightTitleLabel.setText("Entrar no Sistema");
        rightSubtitleLabel.setText("Use o seu email e password para entrar.");
        loginFormBox.setVisible(true);
        loginFormBox.setManaged(true);

        registerFormBox.setVisible(false);
        registerFormBox.setManaged(false);

        toggleModeButton.setText("CRIAR CONTA");
    }

    public void showRegisterMode() {
        rightTitleLabel.setText("Criar Conta");
        rightSubtitleLabel.setText("Use o seu email institucional para se registar.");
        loginFormBox.setVisible(false);
        loginFormBox.setManaged(false);

        registerFormBox.setVisible(true);
        registerFormBox.setManaged(true);

        toggleModeButton.setText("ENTRAR");
    }

    // --------------------------------------------------------
    // API para o Controller
    // --------------------------------------------------------

    public Scene getScene() {
        return scene;
    }

    // Login
    public String getLoginEmail() {
        return loginEmailField.getText().trim();
    }

    public String getLoginPassword() {
        return loginPasswordField.getText();
    }

    public void setLoginStatus(String message, javafx.scene.paint.Color color,
                               boolean showProgress, boolean loginButtonEnabled) {
        loginStatusLabel.setText(message == null ? "" : message);
        loginStatusLabel.setTextFill(color);
        loginProgress.setVisible(showProgress);
        loginButton.setDisable(!loginButtonEnabled);
    }

    // Registo
    public String getRegisterName() {
        return registerNameField.getText().trim();
    }

    public String getRegisterEmail() {
        return registerEmailField.getText().trim();
    }

    public String getRegisterPassword() {
        return registerPasswordField.getText();
    }

    public String getRegisterExtra() {
        return registerExtraField.getText().trim();
    }

    public String getSelectedRegisterType() {
        return rbStudent.isSelected() ? "STUDENT" : "TEACHER";
    }

    public void setRegisterExtraLabel(String text) {
        registerExtraLabel.setText(text);
        if ("Número de Estudante".equalsIgnoreCase(text)) {
            registerExtraField.setPromptText("Ex: 123456");
        } else {
            registerExtraField.setPromptText("Código fornecido pela instituição");
        }
    }

    public void setRegisterStatus(String message, javafx.scene.paint.Color color, boolean buttonEnabled) {
        registerStatusLabel.setText(message == null ? "" : message);
        registerStatusLabel.setTextFill(color);
        registerButton.setDisable(!buttonEnabled);
    }

    public void prefillLoginEmail(String email) {
        loginEmailField.setText(email);
    }

    /**
     * Bloqueia / desbloqueia interação enquanto login/registo está a decorrer.
     * Repara que NÃO mexemos nos botões de ação aqui (loginButton/registerButton),
     * isso continua a ser responsabilidade de setLoginStatus / setRegisterStatus.
     */
    public void setAuthBusy(boolean busy) {
        // botão de alternar
        if (toggleModeButton != null)
            toggleModeButton.setDisable(busy);

        // campos de login
        if (loginEmailField != null)
            loginEmailField.setDisable(busy);
        if (loginPasswordField != null)
            loginPasswordField.setDisable(busy);

        // campos de registo
        if (rbStudent != null)
            rbStudent.setDisable(busy);
        if (rbTeacher != null)
            rbTeacher.setDisable(busy);
        if (registerNameField != null)
            registerNameField.setDisable(busy);
        if (registerEmailField != null)
            registerEmailField.setDisable(busy);
        if (registerPasswordField != null)
            registerPasswordField.setDisable(busy);
        if (registerExtraField != null)
            registerExtraField.setDisable(busy);
    }
}
