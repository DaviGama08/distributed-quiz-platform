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
        root.setStyle("-fx-background-color: #ecf0f1;");

        // Top - Header
        root.setTop(createHeader());

        // Left - Menu lateral
        root.setLeft(createSidebar());

        // Center - Área principal
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);
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
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #34495e;");

        welcomeLabel = new Label("Bem-vindo, Estudante!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        welcomeLabel.setTextFill(Color.WHITE);

        Label emailLabel = new Label(userEmail);
        emailLabel.setFont(Font.font("Arial", 14));
        emailLabel.setTextFill(Color.web("#bdc3c7"));

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #2c3e50;");

        Label menuLabel = new Label("MENU");
        menuLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        menuLabel.setTextFill(Color.WHITE);

        answerQuestionBtn = createMenuButton(" Responder Pergunta", "#3498db");
        historyBtn = createMenuButton(" Histórico", "#9b59b6");
        logoutBtn = createMenuButton(" Logout", "#e74c3c");

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
        mainArea.setPadding(new Insets(30));

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
        instructions.setPadding(new Insets(15));
        instructions.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #bdc3c7;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;"
        );

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

    private Button createMenuButton(String text, String color) {
        Button btn = new Button(text);
        btn.setPrefWidth(180);
        btn.setPrefHeight(40);
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
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + "-fx-opacity: 0.8;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle() + "-fx-opacity: 1.0;"));

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
