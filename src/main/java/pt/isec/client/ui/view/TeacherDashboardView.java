package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import pt.isec.client.ui.controller.TeacherDashboardController;

/**
 * Dashboard do docente (tema escuro com acentos vermelhos).
 */
public class TeacherDashboardView {
    private final String userEmail;

    private Scene scene;
    private Label welcomeLabel;
    private TextArea notificationArea;
    private VBox mainContentArea;

    // botões
    private Button createQuestionBtn;
    private Button listQuestionsBtn;
    private Button viewAnswersBtn;
    private Button exportBtn;
    private Button deleteBtn;
    private Button logoutBtn;

    public TeacherDashboardView(String userEmail) {
        this.userEmail = userEmail;
    }

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        // Cabeçalho
        root.setTop(createHeader());

        // Sidebar
        root.setLeft(createSidebar());

        // Área principal
        mainContentArea = new VBox(20);
        mainContentArea.getStyleClass().add("dashboard-main-area");
        mainContentArea.setPadding(new Insets(20));
        showWelcomeView();
        root.setCenter(mainContentArea);

        scene = new Scene(root, 1100, 750);
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

        welcomeLabel = new Label("Bem-vindo, Docente!");
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

        createQuestionBtn = createMenuButton("Criar Pergunta");
        listQuestionsBtn = createMenuButton("Listar Perguntas");
        viewAnswersBtn = createMenuButton("Ver Respostas");
        exportBtn = createMenuButton("Exportar CSV");
        deleteBtn = createMenuButton("Eliminar Pergunta");
        logoutBtn = createMenuButton("Logout");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                menuLabel,
                new Separator(),
                createQuestionBtn,
                listQuestionsBtn,
                viewAnswersBtn,
                exportBtn,
                deleteBtn,
                spacer,
                logoutBtn
        );

        return sidebar;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-button-dark");
        btn.setPrefWidth(Double.MAX_VALUE);
        return btn;
    }

    private void showWelcomeView() {
        mainContentArea.getChildren().clear();

        Label titleLabel = new Label("Painel do Docente");
        titleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 28));
        titleLabel.getStyleClass().add("dashboard-title");

        Label notifLabel = new Label("Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        notifLabel.getStyleClass().add("dashboard-section-title");

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Sem notificações");
        notificationArea.getStyleClass().add("notification-area-dark");
        notificationArea.setWrapText(true);

        HBox cards = new HBox(20);
        cards.getChildren().addAll(
                createInfoCard("Total de Perguntas", "0"),
                createInfoCard("Perguntas Ativas", "0"),
                createInfoCard("Respostas Recebidas", "0")
        );

        mainContentArea.getChildren().addAll(
                titleLabel,
                new Separator(),
                notifLabel,
                notificationArea,
                new Label(),
                cards
        );
    }

    private VBox createInfoCard(String title, String value) {
        VBox card = new VBox(8);
        card.getStyleClass().add("info-card-dark");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("info-card-value-dark");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("info-card-title-dark");

        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
    }

    public Scene getScene() { return scene; }

    public void registerHandlers(TeacherDashboardController controller) {
        createQuestionBtn.setOnAction(e -> controller.onCreateQuestion());
        listQuestionsBtn.setOnAction(e -> controller.onListQuestions());
        viewAnswersBtn.setOnAction(e -> controller.onViewAnswers());
        //exportBtn.setOnAction(e -> controller.onExport());
        deleteBtn.setOnAction(e -> controller.onDeleteQuestion());
        logoutBtn.setOnAction(e -> controller.onLogout());
    }

    public void update() {
        // não requerido por enquanto
    }

    public void addNotification(String message) {
        if (notificationArea == null) return;
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
