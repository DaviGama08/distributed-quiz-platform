package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.StudentDashboardView;

/**
 * Controller do dashboard do estudante.
 * Contém toda a lógica: listeners, diálogos, navegação, etc.
 */
public class StudentDashboardController {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;

    private final StudentDashboardView view;

    public StudentDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.userEmail = userEmail;

        this.view = new StudentDashboardView(userEmail);
        view.createView();
        view.registerHandlers(this);

        setupPropertyChangeListeners();
    }

    // --------------------------------------------------------
    // Mostrar dashboard
    // --------------------------------------------------------

    public void show() {
        stage.setScene(view.getScene());
    }

    // --------------------------------------------------------
    // Listeners de notificações do servidor
    // --------------------------------------------------------

    private void setupPropertyChangeListeners() {
        clientManager.getService().addPropertyChangeListener(
                ClientService.PROP_NOTIFICATION,
                evt -> {
                    String notification = (String) evt.getNewValue();
                    if (notification != null) {
                        Platform.runLater(() -> {
                            view.addNotification(notification);
                            view.update();
                        });
                    }
                }
        );
    }

    // --------------------------------------------------------
    // Handlers chamados pela View (registerHandlers)
    // --------------------------------------------------------

    /**
     * Handler para "Responder Pergunta".
     */
    public void onAnswerQuestion() {
        Dialog<ButtonType> dialog = new Dialog<>();
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

        ButtonType searchButtonType =
                new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == searchButtonType) {   // agora response é ButtonType
                String code = codeField.getText().trim();
                if (!code.isEmpty()) {
                    // TODO: Buscar pergunta no servidor e mostrar
                    showQuestionDetails(code);
                }
            }
        });
    }

    /**
     * Mostra detalhes da pergunta e permite responder.
     */
    private void showQuestionDetails(String code) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + code);
        dialog.setHeaderText("Responda à pergunta abaixo:");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Pergunta (simulação - em produção vem do servidor)
        Label questionLabel = new Label(
                "Para enviar e receber dados via TCP em Java, recorre-se a objetos do tipo:");
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
     * Handler para "Histórico".
     */
    public void onShowHistory() {
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
     * Handler para "Logout".
     */
    public void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Logout");
        alert.setHeaderText("Deseja realmente sair?");
        alert.setContentText("Será necessário fazer login novamente.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // TODO: Fazer logout no servidor (se aplicável)
                AuthenticationController authController =
                        new AuthenticationController(stage, clientManager, application);
                authController.show();
            }
        });
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------

    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
