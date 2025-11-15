package pt.isec.client.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import pt.isec.client.ui.controller.StudentDashboardController;

/**
 * View do dashboard do estudante.
 * Apenas trata de criar a UI e expor handlers/métodos de atualização.
 */
public class StudentDashboardView {

    private final String userEmail;

    private Scene scene;

    // UI Components
    private Label welcomeLabel;
    private TextArea notificationArea;

    // Menu buttons
    private Button answerQuestionBtn;
    private Button historyBtn;
    private Button logoutBtn;

    public StudentDashboardView(String userEmail) {
        this.userEmail = userEmail;
    }

    // --------------------------------------------------------
    // Métodos principais: createView / registerHandlers / update
    // --------------------------------------------------------

    /**
     * Cria toda a interface do dashboard.
     */
    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("student-root");

        // Top - Header
        root.setTop(createHeader());

        // Left - Menu lateral
        root.setLeft(createSidebar());

        // Center - Área principal
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);

        // carregar CSS
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {}
    }

    /**
     * Regista handlers dos botões chamando métodos do controller.
     */
    public void registerHandlers(StudentDashboardController controller) {
        answerQuestionBtn.setOnAction(e -> controller.onAnswerQuestion());
        historyBtn.setOnAction(e -> controller.onShowHistory());
        logoutBtn.setOnAction(e -> controller.onLogout());
    }

    /**
     * Ponto de extensão para futuros updates globais.
     */
    public void update() {
        // Nada específico por agora.
    }

    // --------------------------------------------------------
    // Criação das partes da UI
    // --------------------------------------------------------

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("header-student");

        welcomeLabel = new Label("Bem-vindo, Estudante!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        welcomeLabel.setTextFill(Color.WHITE);
        welcomeLabel.getStyleClass().add("header-title");

        Label emailLabel = new Label(userEmail);
        emailLabel.getStyleClass().add("header-email");

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().addAll("sidebar", "sidebar-student");

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title");

        answerQuestionBtn = createMenuButton(" Responder Pergunta", "btn-blue");
        historyBtn = createMenuButton(" Histórico", "btn-purple");
        logoutBtn = createMenuButton(" Logout", "btn-red");

        // Separador
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
        mainArea.getStyleClass().add("student-main");

        // Área de notificações
        Label notifLabel = new Label(" Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Aguardando notificações do servidor...");
        notificationArea.setWrapText(true);

        // Instruções
        Label instructionsLabel = new Label("Como começar:");
        instructionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        VBox instructions = new VBox(10);
        instructions.getStyleClass().add("instructions-box");

        Label inst1 = new Label("1️⃣  Obtenha o código da pergunta com o seu docente");
        Label inst2 = new Label("2️⃣  Clique em 'Responder Pergunta' no menu lateral");
        Label inst3 = new Label("3️⃣  Insira o código e responda à pergunta");
        Label inst4 = new Label("4️⃣  Verifique seu histórico de respostas a qualquer momento");

        inst1.setFont(Font.font("Arial", 14));
        inst2.setFont(Font.font("Arial", 14));
        inst3.setFont(Font.font("Arial", 14));
        inst4.setFont(Font.font("Arial", 14));

        instructions.getChildren().addAll(inst1, inst2, inst3, inst4);

        mainArea.getChildren().addAll(
                notifLabel,
                notificationArea,
                instructionsLabel,
                instructions
        );

        return mainArea;
    }

    private Button createMenuButton(String text, String colorClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("sidebar-button", colorClass);
        return btn;
    }

    // --------------------------------------------------------
    // API para o Controller
    // --------------------------------------------------------

    public Scene getScene() {
        return scene;
    }

    /**
     * Adiciona texto à área de notificação (incluindo timestamp).
     */
    public void addNotification(String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
