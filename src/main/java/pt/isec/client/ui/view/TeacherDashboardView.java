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
        root.setStyle("-fx-background-color: #ecf0f1;");

        // Top - Header
        root.setTop(createHeader());

        // Left - Menu lateral
        root.setLeft(createSidebar());

        // Center - Área principal
        mainContentArea = new VBox(20);
        mainContentArea.setPadding(new Insets(30));
        showWelcomeView();

        root.setCenter(mainContentArea);

        scene = new Scene(root, 1100, 750);
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
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #8e44ad;");

        welcomeLabel = new Label("Bem-vindo, Docente!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        welcomeLabel.setTextFill(Color.WHITE);

        Label emailLabel = new Label(userEmail);
        emailLabel.setFont(Font.font("Arial", 14));
        emailLabel.setTextFill(Color.web("#ecf0f1"));

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #2c3e50;");

        Label menuLabel = new Label("MENU");
        menuLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        menuLabel.setTextFill(Color.WHITE);

        createQuestionBtn = createMenuButton("➕ Criar Pergunta", "#27ae60");
        listQuestionsBtn = createMenuButton("📋 Listar Perguntas", "#3498db");
        viewAnswersBtn = createMenuButton("📊 Ver Respostas", "#e67e22");
        exportBtn = createMenuButton("💾 Exportar CSV", "#16a085");
        deleteBtn = createMenuButton("🗑️ Eliminar Pergunta", "#c0392b");
        logoutBtn = createMenuButton("🚪 Logout", "#e74c3c");

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

    private Button createMenuButton(String text, String color) {
        Button btn = new Button(text);
        btn.setPrefWidth(200);
        btn.setPrefHeight(45);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;"
        );

        // Efeito hover
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));

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
        cards.getChildren().addAll(
                createInfoCard("Total de Perguntas", "0", "#3498db"),
                createInfoCard("Perguntas Ativas", "0", "#27ae60"),
                createInfoCard("Respostas Recebidas", "0", "#e67e22")
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

    private VBox createInfoCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200);
        card.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-background-radius: 10;"
        );

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        valueLabel.setTextFill(Color.WHITE);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.WHITE);

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
