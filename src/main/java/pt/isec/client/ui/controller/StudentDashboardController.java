package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.StudentDashboardView;
import pt.isec.server.model.question.Answer;
import pt.isec.server.model.question.OptionLetter;
import pt.isec.server.model.question.Question;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador do dashboard do estudante. Interage com o servidor para
 * responder a perguntas e obter histórico.
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
        ButtonType searchButtonType = new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);
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
    /** Mostra detalhes da pergunta para resposta e submete resposta ao servidor */
    private void showQuestionDetails(String code) {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            return;
        }
        Question question = clientManager.getQuestionService().joinQuestion(code, studentId);
        if (question == null) {
            showErrorAlert("Pergunta não encontrada ou fora do período de resposta.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + code);
        dialog.setHeaderText(question.getStatement());
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        ToggleGroup group = new ToggleGroup();
        var opts = question.getOptions();
        List<RadioButton> radioButtons = new ArrayList<>();
        for (var opt : opts) {
            RadioButton rb = new RadioButton(opt.getLetter().name() + ") " + opt.getText());
            rb.setToggleGroup(group);
            rb.setFont(Font.font("Arial", 13));
            radioButtons.add(rb);
        }
        VBox options = new VBox(10);
        options.getChildren().addAll(radioButtons);
        content.getChildren().add(options);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");
        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK && group.getSelectedToggle() != null) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                String answerLetter = selected.getText().substring(0, 1);
                OptionLetter selectedOption = OptionLetter.valueOf(answerLetter);
                boolean ok = clientManager.getAnswerService().submitAnswer(question.getId(), studentId, selectedOption);
                if (ok) {
                    showSuccessAlert("Resposta submetida com sucesso!", "");
                } else {
                    showErrorAlert("Falha ao submeter a resposta.");
                }
            }
        });
    }
    /** Handler para mostrar o histórico de respostas */
    public void onShowHistory() {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            return;
        }
        List<Answer> history = clientManager.getAnswerService().getStudentHistory(studentId);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);
        if (history == null || history.isEmpty()) {
            Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
            noDataLabel.setFont(Font.font("Arial", 14));
            noDataLabel.setTextFill(Color.web("#7f8c8d"));
            content.getChildren().add(noDataLabel);
        } else {
            TableView<Answer> table = new TableView<>();
            table.setPrefHeight(300);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            TableColumn<Answer, String> dateCol = new TableColumn<>("Data/Hora");
            dateCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getAnsweredAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            // Mostra o identificador da pergunta em vez de chamar getQuestion()
            TableColumn<Answer, String> questionCol = new TableColumn<>("Pergunta");
            questionCol.setCellValueFactory(data -> new SimpleStringProperty(
                    String.valueOf(data.getValue().getQuestionId())));
            TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
            answerCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getSelectedOption().name()));
            TableColumn<Answer, String> resultCol = new TableColumn<>("Correta?");
            resultCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().isCorrect() ? "Sim" : "Não"));
            table.getColumns().addAll(dateCol, questionCol, answerCol, resultCol);
            table.getItems().addAll(history);
            content.getChildren().add(table);
        }
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** Handler de logout */
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
    /** Mostra mensagem de sucesso */
    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    /** Mostra mensagem de erro */
    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
