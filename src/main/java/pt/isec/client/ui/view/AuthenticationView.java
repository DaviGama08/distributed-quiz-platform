package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import pt.isec.client.ui.controller.AuthenticationController;

import java.util.Objects;

/**
 * View de autenticação. A aparência é definida em authentication.css.
 */
public class AuthenticationView {
    private Scene scene;

    // alternância login/registo
    private Button toggleModeButton;

    // títulos do painel direito
    private Label rightTitleLabel;
    private Label rightSubtitleLabel;

    // formulários
    private VBox loginFormBox;
    private VBox registerFormBox;

    // componentes do login
    private TextField loginEmailField;
    private PasswordField loginPasswordField;
    private Button loginButton;
    private ProgressIndicator loginProgress;
    private Label loginStatusLabel;

    // componentes do registo
    private RadioButton rbStudent;
    private RadioButton rbTeacher;
    private Label registerExtraLabel;
    private TextField registerNameField;
    private TextField registerEmailField;
    private PasswordField registerPasswordField;
    private TextField registerExtraField;
    private Button registerButton;
    private Label registerStatusLabel;

    // overlay semi‑transparente para bloquear a interface
    private Pane busyOverlay;

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("auth-root");

        // Painel esquerdo (logo + mensagem)
        VBox leftPane = new VBox();
        leftPane.getStyleClass().add("left-pane");
        leftPane.setPrefWidth(380);

        VBox leftInner = new VBox(20);
        leftInner.getStyleClass().add("left-inner");

        // Logo da instituição
        ImageView logoView;
        try {
            Image logo = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/imgs/logo.png")
            ));
            logoView = new ImageView(logo);
            logoView.setPreserveRatio(true);
            logoView.setFitHeight(120);
        } catch (Exception e) {
            logoView = new ImageView();
        }

        // Título e subtítulo centrados
        Label welcomeTitle = new Label("Bem‑vindo!");
        welcomeTitle.getStyleClass().add("left-title");

        Label welcomeText = new Label("Autentique‑se ou crie uma conta para continuar.");
        welcomeText.getStyleClass().add("left-subtitle");

        toggleModeButton = new Button("CRIAR CONTA");
        toggleModeButton.getStyleClass().add("toggle-mode-button");

        leftInner.getChildren().addAll(logoView, welcomeTitle, welcomeText, toggleModeButton);
        leftPane.getChildren().add(leftInner);

        // Painel direito (formularios)
        VBox rightPane = new VBox();
        rightPane.getStyleClass().add("right-pane");
        rightPane.setAlignment(Pos.CENTER);
        rightPane.setPadding(new Insets(40));

        rightTitleLabel = new Label("Entrar");
        rightTitleLabel.getStyleClass().add("right-title");

        rightSubtitleLabel = new Label("Use o seu email e password.");
        rightSubtitleLabel.getStyleClass().add("right-subtitle");
        rightSubtitleLabel.setWrapText(true); // permite quebra de linha

        VBox header = new VBox(5, rightTitleLabel, rightSubtitleLabel);
        header.setAlignment(Pos.CENTER);

        StackPane formsContainer = new StackPane();
        formsContainer.setPadding(new Insets(30, 0, 0, 0));

        loginFormBox = createLoginForm();
        registerFormBox = createRegisterForm();

        formsContainer.getChildren().addAll(registerFormBox, loginFormBox);

        VBox centerBox = new VBox(30, header, formsContainer);
        centerBox.setAlignment(Pos.CENTER);
        rightPane.getChildren().add(centerBox);

        root.setLeft(leftPane);
        root.setCenter(rightPane);

        // Overlay transparente sem spinner
        StackPane stack = new StackPane();
        stack.getChildren().add(root);

        Pane overlay = new Pane();
        overlay.getStyleClass().add("busy-overlay");
        overlay.setVisible(false);
        overlay.setMouseTransparent(false);
        this.busyOverlay = overlay;

        stack.getChildren().add(overlay);

        scene = new Scene(stack, 900, 600);
        // Carrega CSS
        try {
            var cssUrl = getClass().getResource("/styles/authentication.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {
        }

        showLoginMode();
    }

    /** Cria o formulário de login */
    private VBox createLoginForm() {
        VBox form = new VBox(12);
        form.getStyleClass().add("auth-form");
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);

        loginEmailField = new TextField();
        loginEmailField.setPromptText("Email");
        loginEmailField.getStyleClass().add("form-field");

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");
        loginPasswordField.getStyleClass().add("form-field");

        loginButton = new Button("ENTRAR");
        loginButton.getStyleClass().add("primary-pill-button");
        loginButton.setPrefWidth(220);
        loginButton.setDefaultButton(true);  // Enter acciona login

        loginProgress = new ProgressIndicator();
        loginProgress.setMaxSize(30, 30);
        loginProgress.setVisible(false);

        loginStatusLabel = new Label();
        loginStatusLabel.getStyleClass().add("status-label");
        loginStatusLabel.setWrapText(true);
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

    /** Cria o formulário de registo */
    private VBox createRegisterForm() {
        VBox form = new VBox(12);
        form.getStyleClass().add("auth-form");
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);

        ToggleGroup typeGroup = new ToggleGroup();
        rbStudent = new RadioButton("Estudante");
        rbTeacher = new RadioButton("Docente");
        rbStudent.setToggleGroup(typeGroup);
        rbTeacher.setToggleGroup(typeGroup);
        rbStudent.setSelected(true);
        rbStudent.getStyleClass().add("radio-dark");
        rbTeacher.getStyleClass().add("radio-dark");

        HBox typeBox = new HBox(15, rbStudent, rbTeacher);
        typeBox.setAlignment(Pos.CENTER);

        registerNameField = new TextField();
        registerNameField.setPromptText("Nome completo");
        registerNameField.getStyleClass().add("form-field");

        registerEmailField = new TextField();
        registerEmailField.setPromptText("Email");
        registerEmailField.getStyleClass().add("form-field");

        registerPasswordField = new PasswordField();
        registerPasswordField.setPromptText("Password (mínimo 6)");
        registerPasswordField.getStyleClass().add("form-field");

        registerExtraLabel = new Label("Número de Estudante");
        registerExtraLabel.getStyleClass().add("label-dark");
        registerExtraField = new TextField();
        registerExtraField.setPromptText("Ex: 123456");
        registerExtraField.getStyleClass().add("form-field");

        registerButton = new Button("CRIAR CONTA");
        registerButton.getStyleClass().add("primary-pill-button");
        registerButton.setPrefWidth(220);
        registerButton.setDefaultButton(false); // passa a default no modo de registo

        registerStatusLabel = new Label();
        registerStatusLabel.getStyleClass().add("status-label");
        registerStatusLabel.setWrapText(true);
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

    /* Alterna para o modo login */
    public void showLoginMode() {
        rightTitleLabel.setText("Entrar");
        rightSubtitleLabel.setText("Use o seu email e password.");
        rightSubtitleLabel.setWrapText(true);
        loginFormBox.setVisible(true);
        loginFormBox.setManaged(true);

        registerFormBox.setVisible(false);
        registerFormBox.setManaged(false);

        toggleModeButton.setText("CRIAR CONTA");
        loginButton.setDefaultButton(true);
        registerButton.setDefaultButton(false);
    }

    /* Alterna para o modo registo */
    public void showRegisterMode() {
        rightTitleLabel.setText("Criar Conta");
        rightSubtitleLabel.setText("Use o seu email institucional.");
        rightSubtitleLabel.setWrapText(true);
        loginFormBox.setVisible(false);
        loginFormBox.setManaged(false);

        registerFormBox.setVisible(true);
        registerFormBox.setManaged(true);

        toggleModeButton.setText("ENTRAR");
        loginButton.setDefaultButton(false);
        registerButton.setDefaultButton(true);
    }

    /* API exposta ao controller */
    public Scene getScene() { return scene; }
    public String getLoginEmail() { return loginEmailField.getText().trim(); }
    public String getLoginPassword() { return loginPasswordField.getText(); }
    public String getRegisterName() { return registerNameField.getText().trim(); }
    public String getRegisterEmail() { return registerEmailField.getText().trim(); }
    public String getRegisterPassword() { return registerPasswordField.getText(); }
    public String getRegisterExtra() { return registerExtraField.getText().trim(); }
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

    public void prefillLoginEmail(String email) {
        loginEmailField.setText(email);
    }

    /** Actualiza a mensagem de estado do login, mostra ou oculta o progresso e ajusta o botão */
    public void setLoginStatus(String message, Color color,
                               boolean showProgress, boolean loginButtonEnabled) {
        loginStatusLabel.setText(message == null ? "" : message);
        loginStatusLabel.setTextFill(color != null ? color : Color.WHITE);
        loginProgress.setVisible(showProgress);
        loginButton.setDisable(!loginButtonEnabled);
        loginButton.setDefaultButton(loginButtonEnabled);
    }

    /** Actualiza a mensagem de estado do registo e o botão de registo */
    public void setRegisterStatus(String message, Color color, boolean buttonEnabled) {
        registerStatusLabel.setText(message == null ? "" : message);
        registerStatusLabel.setTextFill(color != null ? color : Color.WHITE);
        registerButton.setDisable(!buttonEnabled);
        registerButton.setDefaultButton(buttonEnabled);
    }

    /** Activa ou desactiva todos os campos e mostra/oculta o overlay */
    public void setAuthBusy(boolean busy) {
        toggleModeButton.setDisable(busy);
        loginEmailField.setDisable(busy);
        loginPasswordField.setDisable(busy);
        rbStudent.setDisable(busy);
        rbTeacher.setDisable(busy);
        registerNameField.setDisable(busy);
        registerEmailField.setDisable(busy);
        registerPasswordField.setDisable(busy);
        registerExtraField.setDisable(busy);
        showBusy(busy);
    }

    public void showBusy(boolean busy) {
        if (busyOverlay != null) busyOverlay.setVisible(busy);
    }

    public void clearLoginFields() {
        loginEmailField.clear();
        loginPasswordField.clear();
        setLoginStatus("", Color.WHITE, false, true);
    }

    public void clearRegisterFields() {
        registerNameField.clear();
        registerEmailField.clear();
        registerPasswordField.clear();
        registerExtraField.clear();
        rbStudent.setSelected(true);
        rbTeacher.setSelected(false);
        setRegisterExtraLabel("Número de Estudante");
        setRegisterStatus("", Color.WHITE, true);
    }

    public void registerHandlers(AuthenticationController controller) {
        toggleModeButton.setOnAction(e -> controller.onToggleMode());
        loginPasswordField.setOnAction(e -> {
            try {
                controller.onLogin();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });
        loginButton.setOnAction(e -> {
            try {
                controller.onLogin();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        registerButton.setOnAction(e -> controller.onRegister());
        rbStudent.setOnAction(e -> controller.onRegisterTypeChanged("STUDENT"));
        rbTeacher.setOnAction(e -> controller.onRegisterTypeChanged("TEACHER"));
    }

    public void update() {
        // não requerido no momento
    }
}
