package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import pt.isec.client.ui.controller.StudentDashboardController;

/**
 * Dashboard do estudante com tema escuro. Usa dashboard.css para estilos.
 */
public class StudentDashboardView {
    private final String userEmail;
    private Scene scene;

    private Label welcomeLabel;
    private TextArea notificationArea;

    // botões do menu
    private Button answerQuestionBtn;
    private Button historyBtn;
    private Button logoutBtn;

    public StudentDashboardView(String userEmail) {
        this.userEmail = userEmail;
    }

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        root.setTop(createHeader());
        root.setLeft(createSidebar());
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) { }
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("dashboard-header-dark");

        welcomeLabel = new Label("Bem-vindo, Estudante!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        welcomeLabel.getStyleClass().add("header-welcome");

        Label emailLabel = new Label(userEmail);
        emailLabel.getStyleClass().add("header-email-dark");

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("dashboard-sidebar-dark");
        sidebar.setPrefWidth(240);

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title-dark");

        answerQuestionBtn = createMenuButton("Responder Pergunta");
        historyBtn = createMenuButton("Histórico");
        logoutBtn = createMenuButton("Logout");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                menuLabel,
                new Separator(),
                answerQuestionBtn,
                historyBtn,
                spacer,
                logoutBtn
        );

        return sidebar;
    }

    private VBox createMainArea() {
        VBox mainArea = new VBox(20);
        mainArea.getStyleClass().add("dashboard-main-area");
        mainArea.setPadding(new Insets(20));

        Label notifLabel = new Label("Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        notifLabel.getStyleClass().add("dashboard-section-title");

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Aguardando notificações do servidor...");
        notificationArea.getStyleClass().add("notification-area-dark");
        notificationArea.setWrapText(true);

        Label instructionsLabel = new Label("Como começar:");
        instructionsLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        instructionsLabel.getStyleClass().add("dashboard-section-title");

        VBox instructions = new VBox(10);
        instructions.getStyleClass().add("instructions-box-dark");
        instructions.setAlignment(Pos.TOP_LEFT);

        Label inst1 = new Label("1. Obtenha o código da pergunta com o seu docente");
        Label inst2 = new Label("2. Clique em 'Responder Pergunta' no menu lateral");
        Label inst3 = new Label("3. Insira o código e responda à pergunta");
        Label inst4 = new Label("4. Verifique seu histórico de respostas a qualquer momento");

        inst1.getStyleClass().add("instruction-line");
        inst2.getStyleClass().add("instruction-line");
        inst3.getStyleClass().add("instruction-line");
        inst4.getStyleClass().add("instruction-line");

        instructions.getChildren().addAll(inst1, inst2, inst3, inst4);

        mainArea.getChildren().addAll(
                notifLabel,
                notificationArea,
                instructionsLabel,
                instructions
        );
        return mainArea;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-button-dark");
        btn.setPrefWidth(Double.MAX_VALUE);
        return btn;
    }

    public Scene getScene() { return scene; }

    public void registerHandlers(StudentDashboardController controller) {
        answerQuestionBtn.setOnAction(e -> controller.onAnswerQuestion());
        historyBtn.setOnAction(e -> controller.onShowHistory());
        logoutBtn.setOnAction(e -> controller.onLogout());
    }

    public void update() {
        // nada extra
    }

    public void addNotification(String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
