package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.TeacherDashboardView;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.server.model.question.Option;
import pt.isec.server.model.question.OptionLetter;
import pt.isec.server.model.question.Question;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controlador do dashboard do docente. Lida com criação, listagem,
 * visualização de respostas, exportação para CSV e eliminação de perguntas.
 */
public class TeacherDashboardController {
    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final TeacherDashboardView view;

    public TeacherDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.userEmail = userEmail;
        this.view = new TeacherDashboardView(userEmail);
        view.createView();
        view.registerHandlers(this);
        setupPropertyChangeListeners();
    }

    /** Mostra o dashboard */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /** Regista listener para notificações enviadas pelo servidor */
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

    /** Handler para criação de pergunta */
    public void onCreateQuestion() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Criar Nova Pergunta");
        dialog.setHeaderText("Preencha os dados da pergunta");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));
        Label statementLabel = new Label("Enunciado:");
        statementLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        TextArea statementField = new TextArea();
        statementField.setPrefRowCount(3);
        statementField.setPrefWidth(500);
        statementField.setPromptText("Digite o enunciado da pergunta...");
        Label numOptionsLabel = new Label("Número de Opções:");
        numOptionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 6, 4);
        numOptionsSpinner.setPrefWidth(100);
        Label optionsLabel = new Label("Opções:");
        optionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField(); optA.setPromptText("Opção A");
        TextField optB = new TextField(); optB.setPromptText("Opção B");
        TextField optC = new TextField(); optC.setPromptText("Opção C");
        TextField optD = new TextField(); optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);
        Label correctLabel = new Label("Resposta Correta:");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue("A");
        Label periodLabel = new Label("Período de Disponibilidade:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        HBox periodBox = new HBox(10);
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("Data início");
        TextField startTime = new TextField(); startTime.setPromptText("HH:MM"); startTime.setPrefWidth(80);
        Label toLabel = new Label(" até ");
        DatePicker endDate = new DatePicker(); endDate.setPromptText("Data fim");
        TextField endTime = new TextField(); endTime.setPromptText("HH:MM"); endTime.setPrefWidth(80);
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
                String statement = statementField.getText().trim();
                if (statement.isEmpty()) {
                    showErrorAlert("Erro", "O enunciado não pode estar vazio.");
                    return;
                }
                int numOptions = numOptionsSpinner.getValue();
                List<Option> options = new ArrayList<>();
                TextField[] allOptions = {optA, optB, optC, optD};
                OptionLetter[] letters = OptionLetter.values();
                for (int i = 0; i < numOptions; i++) {
                    String optText = allOptions[i].getText().trim();
                    if (optText.isEmpty()) {
                        showErrorAlert("Erro", "A opção " + letters[i] + " não pode estar vazia.");
                        return;
                    }
                    options.add(new Option(letters[i], optText));
                }
                OptionLetter correctOption = OptionLetter.valueOf(correctCombo.getValue());
                LocalDate startD = startDate.getValue();
                LocalDate endD   = endDate.getValue();
                String startT    = startTime.getText().trim();
                String endT      = endTime.getText().trim();
                if (startD == null || endD == null || startT.isEmpty() || endT.isEmpty()) {
                    showErrorAlert("Erro", "Datas e horas de início e fim são obrigatórias.");
                    return;
                }
                try {
                    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
                    LocalTime sTime = LocalTime.parse(startT, timeFormat);
                    LocalTime eTime = LocalTime.parse(endT, timeFormat);
                    LocalDateTime startAt = LocalDateTime.of(startD, sTime);
                    LocalDateTime endAt   = LocalDateTime.of(endD, eTime);
                    Integer teacherId = clientManager.getUserId();
                    if (teacherId == null) {
                        showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    CreateQuestionResponseDTO resp = clientManager.getQuestionService().createQuestion(new CreateQuestionDTO(
                            statement, teacherId, options, correctOption, startAt, endAt));
                    if (resp != null) {
                        showSuccessAlert("Pergunta Criada",
                                "A pergunta foi criada com sucesso!\nCódigo: " + resp.accessCode());
                    } else {
                        showErrorAlert("Erro", "Falha ao criar a pergunta.");
                    }
                } catch (Exception e) {
                    showErrorAlert("Erro", "Formato de hora inválido (utilize HH:MM).");
                }
            }
        });
    }

    /** Handler para listar perguntas */
    public void onListQuestions() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Listar Perguntas");
        dialog.setHeaderText("Suas perguntas criadas");
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(700);
        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        Label filterLabel = new Label("Filtrar:");
        filterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Todas", "Ativas", "Futuras", "Expiradas");
        filterCombo.setValue("Todas");
        Button applyFilterBtn = new Button("Aplicar");
        applyFilterBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        filters.getChildren().addAll(filterLabel, filterCombo, applyFilterBtn);
        TableView<Question> table = new TableView<>();
        table.setPrefHeight(400);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Question, String> codeCol = new TableColumn<>("Código");
        codeCol.setCellValueFactory(data -> new SimpleStringProperty(
                Objects.toString(data.getValue().getAccessCode(), "")));
        TableColumn<Question, String> stmtCol = new TableColumn<>("Enunciado");
        stmtCol.setCellValueFactory(data -> new SimpleStringProperty(
                Objects.toString(data.getValue().getStatement(), "")));
        TableColumn<Question, String> periodCol = new TableColumn<>("Período");
        periodCol.setCellValueFactory(data -> {
            Question q = data.getValue();
            LocalDateTime s = q.getStartAt();
            LocalDateTime e = q.getEndAt();
            String str = s + " - " + e;
            return new SimpleStringProperty(str);
        });
        TableColumn<Question, String> stateCol = new TableColumn<>("Estado");
        stateCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getState().name()));
        table.getColumns().addAll(codeCol, stmtCol, periodCol, stateCol);
        content.getChildren().addAll(filters, table);
        applyFilterBtn.setOnAction(ev -> {
            Integer teacherId = clientManager.getUserId();
            if (teacherId == null) {
                showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                return;
            }
            String sel = filterCombo.getValue();
            String filter = null;
            if ("Ativas".equalsIgnoreCase(sel)) filter = "active";
            else if ("Futuras".equalsIgnoreCase(sel)) filter = "future";
            else if ("Expiradas".equalsIgnoreCase(sel)) filter = "expired";
            List<Question> questions = clientManager.getQuestionService().listQuestions(new ListQuestionsDTO(teacherId, filter));
            table.getItems().clear();
            if (questions != null) table.getItems().addAll(questions);
        });
        applyFilterBtn.fire();
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** Handler para ver respostas de uma pergunta */
    public void onViewAnswers() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ver Respostas");
        dialog.setHeaderText("Ver respostas de uma pergunta");
        dialog.setContentText("Código da pergunta:");
        dialog.showAndWait().ifPresent(code -> {
            if (!code.trim().isEmpty()) {
                Integer teacherId = clientManager.getUserId();
                if (teacherId == null) {
                    showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                    return;
                }
                Question q = findQuestionByCode(code.trim());
                if (q == null) {
                    showErrorAlert("Erro", "Pergunta não encontrada ou não é sua.");
                    return;
                }
                var answers = clientManager.getAnswerService().viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
                showAnswersDetails(code.trim(), q, answers);
            }
        });
    }

    /** Mostra os detalhes das respostas numa pergunta */
    private void showAnswersDetails(String code, Question q, List<pt.isec.server.model.question.Answer> answers) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Respostas - " + code);
        dialog.setHeaderText("Respostas submetidas pelos estudantes");
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(750);
        VBox infoBox = new VBox(5);
        infoBox.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 15; -fx-border-radius: 5; -fx-background-radius: 5;");
        Label questionLabel = new Label("Pergunta: " + q.getStatement());
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        Label correctLabel = new Label("Resposta correta: " + q.getCorrectOption().name());
        correctLabel.setFont(Font.font("Arial", 13));
        Label periodLabel = new Label("Período: " + q.getStartAt() + " - " + q.getEndAt());
        periodLabel.setFont(Font.font("Arial", 13));
        infoBox.getChildren().addAll(questionLabel, correctLabel, periodLabel);
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(15));
        statsBox.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-background-radius: 5;");
        int total = answers == null ? 0 : answers.size();
        long correctCnt = answers == null ? 0 : answers.stream().filter(pt.isec.server.model.question.Answer::isCorrect).count();
        int wrongCnt = total - (int) correctCnt;
        double correctPerc = total == 0 ? 0 : (100.0 * correctCnt / total);
        double wrongPerc   = total == 0 ? 0 : (100.0 * wrongCnt / total);
        VBox totalBox = new VBox(5);
        totalBox.setAlignment(Pos.CENTER);
        Label totalValue = new Label(String.valueOf(total));
        totalValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        Label totalLabel = new Label("Total Respostas");
        totalLabel.setFont(Font.font("Arial", 12));
        totalBox.getChildren().addAll(totalValue, totalLabel);
        VBox correctBox = new VBox(5);
        correctBox.setAlignment(Pos.CENTER);
        Label correctValue = new Label(correctCnt + " (" + Math.round(correctPerc) + "%)");
        correctValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        correctValue.setTextFill(Color.web("#27ae60"));
        Label correctLabelText = new Label("Corretas");
        correctLabelText.setFont(Font.font("Arial", 12));
        correctBox.getChildren().addAll(correctValue, correctLabelText);
        VBox wrongBox = new VBox(5);
        wrongBox.setAlignment(Pos.CENTER);
        Label wrongValue = new Label(wrongCnt + " (" + Math.round(wrongPerc) + "%)");
        wrongValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        wrongValue.setTextFill(Color.web("#e74c3c"));
        Label wrongLabelText = new Label("Erradas");
        wrongLabelText.setFont(Font.font("Arial", 12));
        wrongBox.getChildren().addAll(wrongValue, wrongLabelText);
        statsBox.getChildren().addAll(totalBox, correctBox, wrongBox);
        TableView<pt.isec.server.model.question.Answer> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<pt.isec.server.model.question.Answer, String> studCol = new TableColumn<>("Estudante");
        studCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStudentId())));
        TableColumn<pt.isec.server.model.question.Answer, String> answerCol = new TableColumn<>("Resposta");
        answerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSelectedOption().name()));
        TableColumn<pt.isec.server.model.question.Answer, String> correctAnsCol = new TableColumn<>("Correta?");
        correctAnsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isCorrect() ? "Sim" : "Não"));
        table.getColumns().addAll(studCol, answerCol, correctAnsCol);
        if (answers != null) table.getItems().addAll(answers);
        content.getChildren().addAll(infoBox, statsBox, new Label("Respostas:"), table);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /*
    public void onExport() {
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
                        new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
                File file = fileChooser.showSaveDialog(stage);
                if (file != null) {
                    Integer teacherId = clientManager.getUserId();
                    if (teacherId == null) {
                        showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    Question q = findQuestionByCode(code.trim());
                    if (q == null) {
                        showErrorAlert("Erro", "Pergunta não encontrada ou não é sua.");
                        return;
                    }
                    boolean ok = clientManager.getAnswerService().exportResultsToCSV(q.getId(), teacherId, file.getAbsolutePath());
                    if (ok) {
                        showSuccessAlert("Exportação Concluída",
                                "Resultados exportados para:\n" + file.getAbsolutePath());
                    } else {
                        showErrorAlert("Erro", "Não foi possível exportar os resultados.");
                    }
                }
            }
        });
    }*/


    /** Handler para eliminar uma pergunta */
    public void onDeleteQuestion() {
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
                        Integer teacherId = clientManager.getUserId();
                        if (teacherId == null) {
                            showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                            return;
                        }
                        Question q = findQuestionByCode(code.trim());
                        if (q == null) {
                            showErrorAlert("Erro", "Pergunta não encontrada ou não é sua.");
                            return;
                        }
                        boolean ok = clientManager.getQuestionService().deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                        if (ok) {
                            showSuccessAlert("Pergunta Eliminada",
                                    "A pergunta " + code + " foi eliminada com sucesso.");
                        } else {
                            showErrorAlert("Erro", "Não foi possível eliminar a pergunta.");
                        }
                    }
                });
            }
        });
    }

    /** Handler para logout */
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

    /** Mostra alerta de sucesso */
    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Mostra alerta de erro */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Procura uma pergunta criada pelo docente através do seu código de acesso */
    private Question findQuestionByCode(String accessCode) {
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null || accessCode == null || accessCode.isBlank()) {
            return null;
        }
        List<Question> list = clientManager.getQuestionService().listQuestions(new ListQuestionsDTO(teacherId, null));
        if (list != null) {
            for (Question q : list) {
                if (accessCode.equalsIgnoreCase(q.getAccessCode())) {
                    return q;
                }
            }
        }
        return null;
    }
}
