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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import pt.isec.client.ClientManager;

import java.io.File;

/**
 * Dashboard principal para docentes
 */
public class TeacherDashboard {

    private final Stage stage;
    private final ClientManager clientManager;
    private final String userEmail;
    private Scene scene;

    // UI Components
    private Label welcomeLabel;
    private TextArea notificationArea;
    private VBox mainContentArea;

    public TeacherDashboard(Stage stage, ClientManager clientManager, String userEmail) {
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
        mainContentArea = new VBox(20);
        mainContentArea.setPadding(new Insets(30));
        showWelcomeView();

        root.setCenter(mainContentArea);

        scene = new Scene(root, 1100, 750);
    }

    /**
     * Cria o cabeçalho
     */
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

    /**
     * Cria o menu lateral
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #2c3e50;");

        Label menuLabel = new Label("MENU");
        menuLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        menuLabel.setTextFill(Color.WHITE);

        Button createQuestionBtn = createMenuButton("➕ Criar Pergunta", "#27ae60");
        createQuestionBtn.setOnAction(e -> showCreateQuestionView());

        Button listQuestionsBtn = createMenuButton("📋 Listar Perguntas", "#3498db");
        listQuestionsBtn.setOnAction(e -> showListQuestionsView());

        Button viewAnswersBtn = createMenuButton("📊 Ver Respostas", "#e67e22");
        viewAnswersBtn.setOnAction(e -> showViewAnswersView());

        Button exportBtn = createMenuButton("💾 Exportar CSV", "#16a085");
        exportBtn.setOnAction(e -> showExportView());

        Button deleteBtn = createMenuButton("🗑️ Eliminar Pergunta", "#c0392b");
        deleteBtn.setOnAction(e -> showDeleteQuestionView());

        Button logoutBtn = createMenuButton("🚪 Logout", "#e74c3c");
        logoutBtn.setOnAction(e -> handleLogout());

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

    /**
     * Cria botão do menu
     */
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
     * Mostra a view de boas-vindas
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

    /**
     * Cria card de informação
     */
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

    /**
     * Mostra a view de criar pergunta
     */
    private void showCreateQuestionView() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Criar Nova Pergunta");
        dialog.setHeaderText("Preencha os dados da pergunta");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        // Enunciado
        Label statementLabel = new Label("Enunciado:");
        statementLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        TextArea statementField = new TextArea();
        statementField.setPrefRowCount(3);
        statementField.setPrefWidth(500);
        statementField.setPromptText("Digite o enunciado da pergunta...");

        // Número de opções
        Label numOptionsLabel = new Label("Número de Opções:");
        numOptionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 6, 4);
        numOptionsSpinner.setPrefWidth(100);

        // Opções
        Label optionsLabel = new Label("Opções:");
        optionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        VBox optionsBox = new VBox(10);
        TextField optA = new TextField(); optA.setPromptText("Opção A");
        TextField optB = new TextField(); optB.setPromptText("Opção B");
        TextField optC = new TextField(); optC.setPromptText("Opção C");
        TextField optD = new TextField(); optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        // Resposta correta
        Label correctLabel = new Label("Resposta Correta:");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue("A");

        // Período
        Label periodLabel = new Label("Período de Disponibilidade:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        HBox periodBox = new HBox(10);
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("Data início");
        TextField startTime = new TextField();
        startTime.setPromptText("HH:MM");
        startTime.setPrefWidth(80);

        Label toLabel = new Label(" até ");

        DatePicker endDate = new DatePicker();
        endDate.setPromptText("Data fim");
        TextField endTime = new TextField();
        endTime.setPromptText("HH:MM");
        endTime.setPrefWidth(80);

        periodBox.getChildren().addAll(startDate, startTime, toLabel, endDate, endTime);

        grid.add(statementLabel, 0, 0);
        grid.add(statementField, 0, 1, 2, 1);
        grid.add(numOptionsLabel, 0, 2);
        grid.add(numOptionsSpinner, 1, 2);
        grid.add(optionsLabel, 0, 3);
        grid.add(optionsBox, 0, 4, 2, 1);
        grid.add(correctLabel, 0, 5);
        grid.add(correctCombo, 1, 5);
        grid.add(periodLabel, 0, 6);
        grid.add(periodBox, 0, 7, 2, 1);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Criar Pergunta");

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // TODO: Validar e enviar ao servidor
                String statement = statementField.getText().trim();

                if (statement.isEmpty()) {
                    showErrorAlert("Erro", "O enunciado não pode estar vazio.");
                    return;
                }

                // TODO: Enviar dados ao servidor
                showSuccessAlert("Pergunta Criada",
                        "A pergunta foi criada com sucesso!\nCódigo: [GERADO PELO SERVIDOR]");
            }
        });
    }

    /**
     * Mostra a view de listar perguntas
     */
    private void showListQuestionsView() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Listar Perguntas");
        dialog.setHeaderText("Suas perguntas criadas");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(700);

        // Filtros
        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);

        Label filterLabel = new Label("Filtrar:");
        filterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(
                "Todas",
                "Ativas",
                "Futuras",
                "Expiradas"
        );
        filterCombo.setValue("Todas");

        Button applyFilterBtn = new Button("Aplicar");
        applyFilterBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");

        filters.getChildren().addAll(filterLabel, filterCombo, applyFilterBtn);

        // Tabela
        TableView<String> table = new TableView<>();
        table.setPrefHeight(350);

        TableColumn<String, String> codeCol = new TableColumn<>("Código");
        TableColumn<String, String> statementCol = new TableColumn<>("Enunciado");
        TableColumn<String, String> periodCol = new TableColumn<>("Período");
        TableColumn<String, String> statusCol = new TableColumn<>("Estado");

        codeCol.setPrefWidth(100);
        statementCol.setPrefWidth(300);
        periodCol.setPrefWidth(150);
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(codeCol, statementCol, periodCol, statusCol);

        // TODO: Preencher com dados do servidor
        Label noDataLabel = new Label("Nenhuma pergunta criada ainda.");
        noDataLabel.setFont(Font.font("Arial", 14));
        noDataLabel.setTextFill(Color.web("#7f8c8d"));

        content.getChildren().addAll(filters, noDataLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    /**
     * Mostra a view de ver respostas
     */
    private void showViewAnswersView() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ver Respostas");
        dialog.setHeaderText("Ver respostas de uma pergunta");
        dialog.setContentText("Código da pergunta:");

        dialog.showAndWait().ifPresent(code -> {
            if (!code.trim().isEmpty()) {
                showAnswersDetails(code);
            }
        });
    }

    /**
     * Mostra detalhes das respostas
     */
    private void showAnswersDetails(String code) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Respostas - " + code);
        dialog.setHeaderText("Respostas submetidas pelos estudantes");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(750);

        // Info da pergunta
        VBox infoBox = new VBox(5);
        infoBox.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 15; -fx-border-radius: 5; -fx-background-radius: 5;");

        Label questionLabel = new Label("Pergunta: [Enunciado da pergunta]");
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label correctLabel = new Label("Resposta correta: A");
        correctLabel.setFont(Font.font("Arial", 13));

        Label periodLabel = new Label("Período: 10/11/2025 10:00 - 10/11/2025 12:00");
        periodLabel.setFont(Font.font("Arial", 13));

        infoBox.getChildren().addAll(questionLabel, correctLabel, periodLabel);

        // Estatísticas
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(15));
        statsBox.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-background-radius: 5;");

        VBox totalBox = new VBox(5);
        totalBox.setAlignment(Pos.CENTER);
        Label totalValue = new Label("0");
        totalValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        Label totalLabel = new Label("Total Respostas");
        totalLabel.setFont(Font.font("Arial", 12));
        totalBox.getChildren().addAll(totalValue, totalLabel);

        VBox correctBox = new VBox(5);
        correctBox.setAlignment(Pos.CENTER);
        Label correctValue = new Label("0 (0%)");
        correctValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        correctValue.setTextFill(Color.web("#27ae60"));
        Label correctLabelText = new Label("Corretas");
        correctLabelText.setFont(Font.font("Arial", 12));
        correctBox.getChildren().addAll(correctValue, correctLabelText);

        VBox wrongBox = new VBox(5);
        wrongBox.setAlignment(Pos.CENTER);
        Label wrongValue = new Label("0 (0%)");
        wrongValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        wrongValue.setTextFill(Color.web("#e74c3c"));
        Label wrongLabelText = new Label("Erradas");
        wrongLabelText.setFont(Font.font("Arial", 12));
        wrongBox.getChildren().addAll(wrongValue, wrongLabelText);

        statsBox.getChildren().addAll(totalBox, correctBox, wrongBox);

        // Tabela de respostas
        TableView<String> table = new TableView<>();
        table.setPrefHeight(250);

        TableColumn<String, String> numberCol = new TableColumn<>("Nº Est.");
        TableColumn<String, String> nameCol = new TableColumn<>("Nome");
        TableColumn<String, String> emailCol = new TableColumn<>("Email");
        TableColumn<String, String> answerCol = new TableColumn<>("Resposta");
        TableColumn<String, String> timeCol = new TableColumn<>("Data/Hora");

        numberCol.setPrefWidth(80);
        nameCol.setPrefWidth(150);
        emailCol.setPrefWidth(180);
        answerCol.setPrefWidth(80);
        timeCol.setPrefWidth(120);

        table.getColumns().addAll(numberCol, nameCol, emailCol, answerCol, timeCol);

        // TODO: Preencher com dados do servidor

        content.getChildren().addAll(infoBox, statsBox, new Label("Respostas:"), table);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    /**
     * Mostra a view de exportar
     */
    private void showExportView() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Exportar Resultados");
        dialog.setHeaderText("Exportar resultados para CSV");
        dialog.setContentText("Código da pergunta:");

        dialog.showAndWait().ifPresent(code -> {
            if (!code.trim().isEmpty()) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Guardar Ficheiro CSV");
                fileChooser.setInitialFileName("resultados_" + code + ".csv");
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("CSV Files", "*.csv")
                );

                File file = fileChooser.showSaveDialog(stage);

                if (file != null) {
                    // TODO: Solicitar exportação ao servidor
                    showSuccessAlert("Exportação Concluída",
                            "Resultados exportados para:\n" + file.getAbsolutePath());
                }
            }
        });
    }

    /**
     * Mostra a view de eliminar pergunta
     */
    private void showDeleteQuestionView() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Eliminar Pergunta");
        dialog.setHeaderText("⚠️ Atenção: Esta ação é irreversível!");
        dialog.setContentText("Código da pergunta a eliminar:");

        dialog.showAndWait().ifPresent(code -> {
            if (!code.trim().isEmpty()) {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Confirmar Eliminação");
                confirmAlert.setHeaderText("Tem certeza que deseja eliminar a pergunta " + code + "?");
                confirmAlert.setContentText("Esta ação não pode ser desfeita.");

                confirmAlert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        // TODO: Enviar pedido de eliminação ao servidor
                        showSuccessAlert("Pergunta Eliminada",
                                "A pergunta " + code + " foi eliminada com sucesso.");
                    }
                });
            }
        });
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
            if (notificationArea != null) {
                String timestamp = java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                );
                notificationArea.appendText("[" + timestamp + "] " + message + "\n");
            }
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

    /**
     * Mostra alerta de erro
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void show() {
        stage.setScene(scene);
    }
}
