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
import pt.isec.client.ClientManager;

/**
 * Dashboard principal para estudantes
 */
public class StudentDashboard {

    private final Stage stage;
    private final ClientManager clientManager;
    private final String userEmail;
    private Scene scene;

    // UI Components
    private Label welcomeLabel;
    private TextArea notificationArea;

    public StudentDashboard(Stage stage, ClientManager clientManager, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.userEmail = userEmail;
        initializeUI();
        setupPropertyChangeListeners();
    }

    /**
     * Configura listeners para notificações do servidor
     */
    private void setupPropertyChangeListeners() {
        // Listener para notificações assíncronas
        clientManager.getService().addPropertyChangeListener(
                pt.isec.client.ClientService.PROP_NOTIFICATION,
                evt -> {
                    String notification = (String) evt.getNewValue();
                    if (notification != null) addNotification(notification);
                }
        );
    }

    private void initializeUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #ecf0f1;");

        // Top - Header
        root.setTop(createHeader());

        // Left - Menu lateral
        root.setLeft(createSidebar());

        // Center - Área principal
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);
        // scene.getStylesheets().add(getClass().getResource("/styles/dashboard.css").toExternalForm());
    }

    /**
     * Cria o cabeçalho
     */
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

    /**
     * Cria o menu lateral
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #2c3e50;");

        Label menuLabel = new Label("MENU");
        menuLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        menuLabel.setTextFill(Color.WHITE);

        Button answerQuestionBtn = createMenuButton(" Responder Pergunta", "#3498db");
        answerQuestionBtn.setOnAction(e -> showAnswerQuestionView());

        Button historyBtn = createMenuButton(" Histórico", "#9b59b6");
        historyBtn.setOnAction(e -> showHistoryView());

        Button logoutBtn = createMenuButton(" Logout", "#e74c3c");
        logoutBtn.setOnAction(e -> handleLogout());

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

    /**
     * Cria a área principal
     */
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
        instructions.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-background-radius: 5;");

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

    /**
     * Cria botão do menu
     */
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

    /**
     * Mostra a view de responder pergunta
     */
    private void showAnswerQuestionView() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Responder Pergunta");
        dialog.setHeaderText("Insira o código da pergunta");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField codeField = new TextField();
        codeField.setPromptText("Código (ex: ABC123)");
        codeField.setPrefWidth(250);

        grid.add(new Label("Código da Pergunta:"), 0, 0);
        grid.add(codeField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        ButtonType searchButtonType = new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            String code = codeField.getText().trim();
            if (!code.isEmpty()) {
                // TODO: Buscar pergunta no servidor e mostrar
                showQuestionDetails(code);
            }
        });
    }

    /**
     * Mostra detalhes da pergunta e permite responder
     */
    private void showQuestionDetails(String code) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + code);
        dialog.setHeaderText("Responda à pergunta abaixo:");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Pergunta (simulação - em produção vem do servidor)
        Label questionLabel = new Label("Para enviar e receber dados via TCP em Java, recorre-se a objetos do tipo:");
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        questionLabel.setWrapText(true);

        // Opções (RadioButtons)
        ToggleGroup group = new ToggleGroup();

        RadioButton optA = new RadioButton("A) Socket");
        RadioButton optB = new RadioButton("B) ServerSocket");
        RadioButton optC = new RadioButton("C) DatagramSocket");
        RadioButton optD = new RadioButton("D) MulticastSocket");

        optA.setToggleGroup(group);
        optB.setToggleGroup(group);
        optC.setToggleGroup(group);
        optD.setToggleGroup(group);

        optA.setFont(Font.font("Arial", 13));
        optB.setFont(Font.font("Arial", 13));
        optC.setFont(Font.font("Arial", 13));
        optD.setFont(Font.font("Arial", 13));

        VBox options = new VBox(10, optA, optB, optC, optD);
        options.setPadding(new Insets(10));

        content.getChildren().addAll(questionLabel, new Separator(), options);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && group.getSelectedToggle() != null) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                String answer = selected.getText().substring(0, 1); // A, B, C, D

                // TODO: Enviar resposta ao servidor
                showSuccessAlert("Resposta submetida com sucesso!",
                        "Sua resposta '" + answer + "' foi registada.");
            }
        });
    }

    /**
     * Mostra a view de histórico
     */
    private void showHistoryView() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        // Tabela de histórico (simulação)
        TableView<String> table = new TableView<>();
        table.setPrefHeight(300);

        TableColumn<String, String> dateCol = new TableColumn<>("Data");
        TableColumn<String, String> questionCol = new TableColumn<>("Pergunta");
        TableColumn<String, String> answerCol = new TableColumn<>("Resposta");
        TableColumn<String, String> resultCol = new TableColumn<>("Resultado");

        dateCol.setPrefWidth(100);
        questionCol.setPrefWidth(250);
        answerCol.setPrefWidth(80);
        resultCol.setPrefWidth(100);

        table.getColumns().addAll(dateCol, questionCol, answerCol, resultCol);

        // TODO: Preencher com dados reais do servidor
        Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
        noDataLabel.setFont(Font.font("Arial", 14));
        noDataLabel.setTextFill(Color.web("#7f8c8d"));

        content.getChildren().addAll(noDataLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    /**
     * Trata o logout
     */
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Logout");
        alert.setHeaderText("Deseja realmente sair?");
        alert.setContentText("Será necessário fazer login novamente.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // TODO: Fazer logout no servidor
                // Voltar à tela de login
                AuthenticationScreen authScreen = new AuthenticationScreen(stage, clientManager, null);
                authScreen.show();
            }
        });
    }

    /**
     * Adiciona notificação
     */
    public void addNotification(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            notificationArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    /**
     * Mostra alerta de sucesso
     */
    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void show() {
        stage.setScene(scene);
    }
}
