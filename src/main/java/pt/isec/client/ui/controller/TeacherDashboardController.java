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
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controlador do dashboard do docente. Lida com criação, listagem
 * e visualização de respostas de perguntas em regime assíncrono.
 * Todos os pedidos ao servidor são enfileirados; as respostas são tratadas
 * por eventos (property changes) de ClientService.
 */
public class TeacherDashboardController {
    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final TeacherDashboardView view;

    // Guarda a última lista de perguntas (actualizada ao receber LIST_QUESTIONS_RESPONSE)
    private final List<Question> lastQuestions = new ArrayList<>();

    // Tabela actualmente aberta no diálogo de listagem (pode ser null)
    private TableView<Question> questionsTable = null;

    // Flags de espera para operações assíncronas
    private volatile boolean awaitingCreateQuestion = false;
    private volatile boolean awaitingListQuestions  = false;
    private volatile boolean awaitingViewAnswers    = false;

    // Filtro actual para listagem
    private volatile String currentFilter = null;

    // Guarda a pergunta seleccionada para visualização de respostas
    private volatile Question pendingViewQuestion = null;

    public TeacherDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application   = application;
        this.userEmail     = userEmail;
        this.view          = new TeacherDashboardView(userEmail);
        view.createView();
        view.registerHandlers(this);
        setupPropertyChangeListeners();
    }

    /** Exibe o dashboard */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /** Regista listeners para eventos do ClientService */
    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        // Notificações genéricas do servidor
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

        // Resposta à criação de pergunta
        service.addPropertyChangeListener(
                ClientService.PROP_CREATE_QUESTION_RESPONSE,
                evt -> {
                    if (!awaitingCreateQuestion) return;
                    awaitingCreateQuestion = false;
                    CreateQuestionResponseDTO resp = (CreateQuestionResponseDTO) evt.getNewValue();
                    Platform.runLater(() -> {
                        showSuccessAlert("Pergunta Criada",
                                "A pergunta foi criada com sucesso!\nCódigo: " +
                                        (resp != null ? resp.accessCode() : ""));
                        refreshQuestions();
                    });
                }
        );

        // Lista de perguntas devolvida
        service.addPropertyChangeListener(
                ClientService.PROP_LIST_QUESTIONS_RESPONSE,
                evt -> {
                    if (!awaitingListQuestions) return;
                    awaitingListQuestions = false;
                    @SuppressWarnings("unchecked")
                    List<Question> list = (List<Question>) evt.getNewValue();

                    Platform.runLater(() -> {
                        lastQuestions.clear();
                        if (list != null) lastQuestions.addAll(list);
                        // actualiza a tabela do diálogo, se existir
                        refreshQuestionsTableView();
                    });
                }
        );

        // Respostas de uma pergunta (vista do docente) devolvidas
        service.addPropertyChangeListener(
                ClientService.PROP_VIEW_ANSWERS_RESPONSE,
                evt -> {
                    if (!awaitingViewAnswers) return;
                    awaitingViewAnswers = false;
                    @SuppressWarnings("unchecked")
                    List<Answer> answers = (List<Answer>) evt.getNewValue();
                    final Question q = pendingViewQuestion;
                    pendingViewQuestion = null;
                    Platform.runLater(() -> {
                        if (q != null) showAnswersDetails(q, answers);
                        else showErrorAlert("Erro", "Pergunta não encontrada.");
                    });
                }
        );
    }

    /** Handler para criar nova pergunta; envia apenas o pedido ao serviço */
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
        okButton.setOnAction(ev -> {
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
                awaitingCreateQuestion = true;
                System.out.println("ID: " + teacherId);
                clientManager.getQuestionService().createQuestion(new CreateQuestionDTO(
                        statement, teacherId, options, correctOption, startAt, endAt));
            } catch (Exception e) {
                showErrorAlert("Erro", "Formato de hora inválido (utilize HH:MM).");
            }
        });

        dialog.showAndWait();
    }

    /** Handler para listar perguntas, usando filtros. */
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

        // guarda referência à tabela do diálogo
        this.questionsTable = table;

        // handler do botão de filtro
        applyFilterBtn.setOnAction(ev -> {
            Integer teacherId = clientManager.getUserId();
            if (teacherId == null) {
                showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                return;
            }

            String sel = filterCombo.getValue();
            if ("Ativas".equalsIgnoreCase(sel)) currentFilter = "active";
            else if ("Futuras".equalsIgnoreCase(sel)) currentFilter = "future";
            else if ("Expiradas".equalsIgnoreCase(sel)) currentFilter = "expired";
            else currentFilter = null;

            // pede nova lista ao servidor
            awaitingListQuestions = true;
            clientManager.getQuestionService()
                    .listQuestions(new ListQuestionsDTO(teacherId, currentFilter));

            // enquanto a resposta não chega, mostra o que já temos em memória
            refreshQuestionsTableView();
        });

        // Carrega perguntas inicialmente (usa filtro default "Todas")
        applyFilterBtn.fire();

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();

        // diálogo fechado -> deixa de haver tabela activa
        this.questionsTable = null;
    }

    /** Actualiza a TableView com base em lastQuestions + currentFilter. */
    private void refreshQuestionsTableView() {
        if (questionsTable == null) return;

        questionsTable.getItems().clear();
        if (lastQuestions.isEmpty()) return;

        if (currentFilter == null) {
            questionsTable.getItems().addAll(lastQuestions);
            return;
        }

        for (Question q : lastQuestions) {
            switch (currentFilter) {
                case "active" -> {
                    if ("ACTIVE".equalsIgnoreCase(q.getState().name()))
                        questionsTable.getItems().add(q);
                }
                case "future" -> {
                    if ("FUTURE".equalsIgnoreCase(q.getState().name()))
                        questionsTable.getItems().add(q);
                }
                case "expired" -> {
                    if ("EXPIRED".equalsIgnoreCase(q.getState().name()))
                        questionsTable.getItems().add(q);
                }
            }
        }
    }

    /** Requisita uma actualização da lista de perguntas; chamada após criação ou eliminação */
    private void refreshQuestions() {
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) return;
        awaitingListQuestions = true;
        clientManager.getQuestionService().listQuestions(new ListQuestionsDTO(teacherId, currentFilter));
    }

    /** Handler para solicitar visualização de respostas de uma pergunta */
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
                pendingViewQuestion = q;
                awaitingViewAnswers = true;
                clientManager.getAnswerService().viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
            }
        });
    }

    /** Mostra as respostas da pergunta (chamado ao receber VIEW_ANSWERS_RESPONSE) */
    private void showAnswersDetails(Question q, List<Answer> answers) {
        String code = q.getAccessCode();
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
        long correctCnt = answers == null ? 0 : answers.stream().filter(Answer::isCorrect).count();
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

        TableView<Answer> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Answer, String> studCol = new TableColumn<>("Estudante");
        studCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStudentId())));
        TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
        answerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSelectedOption().name()));
        TableColumn<Answer, String> correctAnsCol = new TableColumn<>("Correta?");
        correctAnsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isCorrect() ? "Sim" : "Não"));
        table.getColumns().addAll(studCol, answerCol, correctAnsCol);
        if (answers != null) table.getItems().addAll(answers);

        content.getChildren().addAll(infoBox, statsBox, new Label("Respostas:"), table);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** Handler para eliminar uma pergunta. Enfileira o pedido e refresca a lista. */
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
                        clientManager.getQuestionService().deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                        showSuccessAlert("Pedido de eliminação enviado",
                                "A pergunta " + code + " será eliminada (caso não tenha respostas).");
                        refreshQuestions();
                    }
                });
            }
        });
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

    /** Procura uma pergunta pelo seu código na lista carregada */
    private Question findQuestionByCode(String accessCode) {
        if (accessCode == null || accessCode.isBlank()) return null;
        for (Question q : lastQuestions) {
            if (accessCode.equalsIgnoreCase(q.getAccessCode())) {
                return q;
            }
        }
        return null;
    }
}
