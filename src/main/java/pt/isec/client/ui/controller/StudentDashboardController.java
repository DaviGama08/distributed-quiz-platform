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
 * Controlador do dashboard do estudante. Responsável por chamar serviços,
 * reagir a cliques e repassar notificações para a view.
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

    /** Mostra o dashboard */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /** Regista listener para notificações do servidor */
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

    /** Handler de responder pergunta */
    public void onAnswerQuestion() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Responder Pergunta");
        dialog.setHeaderText("Insira o código da pergunta");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField codeField = new TextField();
        codeField.setPromptText("Código (ex: ABC123)");
        codeField.setPrefWidth(250);

        content.getChildren().addAll(new Label("Código da Pergunta:"), codeField);
        dialog.getDialogPane().setContent(content);

        ButtonType searchButtonType =
                new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == searchButtonType) {
                String code = codeField.getText().trim();
                if (!code.isEmpty()) {
                    showQuestionDetails(code);
                }
            }
        });
    }

    /** Mostra detalhes da pergunta para resposta */
    private void showQuestionDetails(String code) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + code);
        dialog.setHeaderText("Responda à pergunta abaixo:");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label questionLabel = new Label(
                "Para enviar e receber dados via TCP em Java, recorre-se a objetos do tipo:");
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        questionLabel.setWrapText(true);

        ToggleGroup group = new ToggleGroup();
        RadioButton optA = new RadioButton("A) Socket");
        RadioButton optB = new RadioButton("B) ServerSocket");
        RadioButton optC = new RadioButton("C) DatagramSocket");
        RadioButton optD = new RadioButton("D) MulticastSocket");
        optA.setToggleGroup(group);
        optB.setToggleGroup(group);
        optC.setToggleGroup(group);
        optD.setToggleGroup(group);

        VBox options = new VBox(10, optA, optB, optC, optD);
        options.setPadding(new Insets(10));

        content.getChildren().addAll(questionLabel, new Separator(), options);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK && group.getSelectedToggle() != null) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                String answer = selected.getText().substring(0, 1); // A/B/C/D

                // TODO: enviar resposta ao servidor
                showSuccessAlert("Resposta submetida com sucesso!",
                        "Sua resposta '" + answer + "' foi registada.");
            }
        });
    }

    public void onShowHistory() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
        noDataLabel.setFont(Font.font("Arial", 14));
        noDataLabel.setTextFill(Color.web("#7f8c8d"));

        content.getChildren().addAll(noDataLabel);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    public void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Logout");
        alert.setHeaderText("Deseja realmente sair?");
        alert.setContentText("Será necessário fazer login novamente.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                clientManager.getService().logout();
                application.showAuthentication();
            }
        });
    }

    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
