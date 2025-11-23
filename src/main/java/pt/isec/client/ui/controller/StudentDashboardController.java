package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.StudentDashboardView;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador do dashboard do estudante. Interage com o servidor para
 * responder a perguntas e obter histórico, seguindo o modelo de eventos.
 * Todos os pedidos são enfileirados e as respostas são tratadas via
 * propriedades do ClientService.
 */
public class StudentDashboardController {
    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final StudentDashboardView view;

    // Flags de espera
    private volatile boolean awaitingJoinQuestion = false;
    private volatile boolean awaitingSubmitAnswer = false;
    private volatile boolean awaitingHistory      = false;

    public StudentDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application   = application;
        this.userEmail     = userEmail;
        this.view          = new StudentDashboardView(userEmail);
        view.createView();
        view.registerHandlers(this);
        setupPropertyChangeListeners();
    }

    /** Exibe o dashboard do estudante */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /** Regista listeners para eventos do ClientService */
    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        // Notificações genéricas
        service.addPropertyChangeListener(
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

        // Resposta ao join numa pergunta
        service.addPropertyChangeListener(
                ClientService.PROP_JOIN_QUESTION_RESPONSE,
                evt -> {
                    if (!awaitingJoinQuestion) return;
                    awaitingJoinQuestion = false;
                    Question q = (Question) evt.getNewValue();
                    Platform.runLater(() -> {
                        if (q == null) {
                            showErrorAlert("Código inválido ou pergunta não existente.");
                        } else {
                            openQuestionDialog(q);
                        }
                    });
                }
        );

        // Submissão de resposta bem sucedida
        service.addPropertyChangeListener(
                ClientService.PROP_SUBMIT_ANSWER_OK,
                evt -> {
                    if (!awaitingSubmitAnswer) return;
                    awaitingSubmitAnswer = false;
                    String msg = (String) evt.getNewValue();
                    Platform.runLater(() ->
                            showSuccessAlert("Resposta submetida com sucesso!",
                                    msg == null ? "" : msg)
                    );
                }
        );

        // Falha na submissão de resposta
        service.addPropertyChangeListener(
                ClientService.PROP_SUBMIT_ANSWER_FAIL,
                evt -> {
                    if (!awaitingSubmitAnswer) return;
                    awaitingSubmitAnswer = false;
                    String msg = (String) evt.getNewValue();
                    Platform.runLater(() ->
                            showErrorAlert("Falha ao submeter a resposta: " +
                                    (msg == null ? "" : msg))
                    );
                }
        );

        // Histórico de respostas devolvido
        service.addPropertyChangeListener(
                ClientService.PROP_LIST_ANSWERED_RESPONSE,
                evt -> {
                    if (!awaitingHistory) return;
                    awaitingHistory = false;
                    @SuppressWarnings("unchecked")
                    List<Answer> history = (List<Answer>) evt.getNewValue();
                    Platform.runLater(() ->
                            showHistoryDialog(history)
                    );
                }
        );
    }

    /** Handler para solicitar uma pergunta (insere código e envia JoinQuestion) */
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
                    Integer studentId = clientManager.getUserId();
                    if (studentId == null) {
                        showErrorAlert("Sessão inválida. Faça login novamente.");
                        return;
                    }
                    awaitingJoinQuestion = true;
                    try {
                        clientManager.getQuestionService()
                                .joinQuestion(new JoinQuestionDTO(code, studentId));
                    } catch (Exception e) {
                        showErrorAlert("Erro ao procurar pergunta: " + e.getMessage());
                        awaitingJoinQuestion = false;
                    }
                }
            }
        });
    }

    /** Abre a janela com a pergunta e envia a resposta seleccionada */
    private void openQuestionDialog(Question question) {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + question.getAccessCode());
        dialog.setHeaderText(question.getStatement());
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        ToggleGroup group = new ToggleGroup();
        List<RadioButton> radioButtons = new ArrayList<>();
        List<Option> opts = question.getOptions();
        for (Option opt : opts) {
            RadioButton rb = new RadioButton(opt.getLetter().name() + ") " + opt.getText());
            rb.setToggleGroup(group);
            rb.setFont(Font.font("Arial", 13));
            radioButtons.add(rb);
        }
        VBox optionsBox = new VBox(10);
        optionsBox.getChildren().addAll(radioButtons);
        content.getChildren().add(optionsBox);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");
        okButton.setOnAction(ev -> {
            if (group.getSelectedToggle() != null) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                String answerLetter = selected.getText().substring(0, 1);
                OptionLetter selectedOption = OptionLetter.valueOf(answerLetter);
                awaitingSubmitAnswer = true;
                try {
                    clientManager.getAnswerService().submitAnswer(
                            new SubmitAnswerDTO(question.getId(), studentId, selectedOption));
                } catch (Exception ex) {
                    showErrorAlert("Erro ao submeter resposta: " + ex.getMessage());
                    awaitingSubmitAnswer = false;
                }
            }
        });
        dialog.showAndWait();
    }

    /** Handler para solicitar o histórico de respostas */
    public void onShowHistory() {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            return;
        }
        awaitingHistory = true;
        try {
            clientManager.getAnswerService().viewAnswersForStudent(studentId);
        } catch (Exception e) {
            showErrorAlert("Erro ao obter histórico: " + e.getMessage());
            awaitingHistory = false;
        }
    }

    /** Exibe histórico de respostas numa janela (ao receber LIST_ANSWERED_RESPONSE) */
    private void showHistoryDialog(List<Answer> history) {
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
            TableColumn<Answer, String> questionCol = new TableColumn<>("Pergunta");
            questionCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getQuestionStatement() != null
                            ? data.getValue().getQuestionStatement()
                            : String.valueOf(data.getValue().getQuestionId())
            ));
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
                try {
                    clientManager.getAuthService().logout();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
