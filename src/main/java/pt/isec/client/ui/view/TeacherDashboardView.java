package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import pt.isec.client.ui.controller.TeacherDashboardController;

/**
 * View do dashboard do docente.
 * Responsável por construir a UI base e expor handlers/métodos de atualização.
 */
public class TeacherDashboardView {

    private final String userEmail;

    private Scene scene;

    // UI Components
    private Label welcomeLabel;
    private TextArea notificationArea;
    private VBox mainContentArea;

    // Menu buttons
    private Button createQuestionBtn;
    private Button listQuestionsBtn;
    private Button viewAnswersBtn;
    private Button exportBtn;
    private Button deleteBtn;
    private Button logoutBtn;

    public TeacherDashboardView(String userEmail) {
        this.userEmail = userEmail;
    }

    // --------------------------------------------------------
    // Métodos principais: createView / registerHandlers / update
    // --------------------------------------------------------

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("teacher-root");

        // Top - Header
        root.setTop(createHeader());

        // Left - Menu lateral
        root.setLeft(createSidebar());

        // Center - Área principal
        mainContentArea = new VBox(20);
        mainContentArea.getStyleClass().add("teacher-main");
        showWelcomeView();

        root.setCenter(mainContentArea);

        scene = new Scene(root, 1100, 750);

        // carregar CSS
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {}
    }

    public void registerHandlers(TeacherDashboardController controller) {
        createQuestionBtn.setOnAction(e -> controller.onCreateQuestion());
        listQuestionsBtn.setOnAction(e -> controller.onListQuestions());
        viewAnswersBtn.setOnAction(e -> controller.onViewAnswers());
        exportBtn.setOnAction(e -> controller.onExport());
        deleteBtn.setOnAction(e -> controller.onDeleteQuestion());
        logoutBtn.setOnAction(e -> controller.onLogout());
    }

    public void update() {
        // Nada específico por agora.
    }

    // --------------------------------------------------------
    // Criação de UI
    // --------------------------------------------------------

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("header-teacher");

        welcomeLabel = new Label("Bem-vindo, Docente!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        welcomeLabel.setTextFill(Color.WHITE);
        welcomeLabel.getStyleClass().add("header-title");

        Label emailLabel = new Label(userEmail);
        emailLabel.getStyleClass().add("header-email-teacher");

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().addAll("sidebar", "sidebar-teacher");

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title");

        createQuestionBtn = createMenuButton("➕ Criar Pergunta", "btn-green");
        listQuestionsBtn = createMenuButton("📋 Listar Perguntas", "btn-blue");
        viewAnswersBtn = createMenuButton("📊 Ver Respostas", "btn-orange");
        exportBtn = createMenuButton("💾 Exportar CSV", "btn-teal");
        deleteBtn = createMenuButton("🗑️ Eliminar Pergunta", "btn-red-dark");
        logoutBtn = createMenuButton("🚪 Logout", "btn-red");

        // Separador
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

    private Button createMenuButton(String text, String colorClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("sidebar-button", "sidebar-button-teacher", colorClass);
        return btn;
    }

    /**
     * Mostra a view de boas-vindas no centro.
     */
    private void showWelcomeView() {
        mainContentArea.getChildren().clear();

        Label titleLabel = new Label("🎓 Painel do Docente");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));

        // Área de notificações
        Label notifLabel = new Label("🔔 Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Sem notificações");
        notificationArea.setWrapText(true);

        // Cards de informação
        HBox cards = new HBox(20);
        cards.getStyleClass().add("info-cards-box");
        cards.getChildren().addAll(
                createInfoCard("Total de Perguntas", "0", "card-blue"),
                createInfoCard("Perguntas Ativas", "0", "card-green"),
                createInfoCard("Respostas Recebidas", "0", "card-orange")
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

    private VBox createInfoCard(String title, String value, String colorClass) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("info-card", colorClass);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("info-card-value");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("info-card-title");

        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
    }

    // --------------------------------------------------------
    // API para o Controller
    // --------------------------------------------------------

    public Scene getScene() {
        return scene;
    }

    public void addNotification(String message) {
        if (notificationArea == null) return;

        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
