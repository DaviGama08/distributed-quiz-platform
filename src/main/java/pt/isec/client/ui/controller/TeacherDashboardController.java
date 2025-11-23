package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.TeacherDashboardView;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.question.*;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.beans.PropertyChangeListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controlador do dashboard do docente. Lida com criação, listagem
 * e visualização de respostas de perguntas em regime assíncrono.
 * Todos os pedidos ao servidor são enfileirados; as respostas são tratadas
 * por eventos (property changes) de ClientService.
 *
 * Esta classe implementa IDisposableProp para garantir que os listeners
 * são removidos em dispose() e evitar memory‑leaks.
 */
public class TeacherDashboardController implements IDisposableProp {
    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final String userName;
    private final TeacherDashboardView view;

    // listeners finais — inicializados no construtor para garantir que 'view' está pronto
    // (mantém referências fortes para poder remover no dispose())
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener createQuestionListener;
    private final PropertyChangeListener listQuestionsListener;
    private final PropertyChangeListener viewAnswersListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener updateQuestionListener;
    private volatile boolean awaitingJoinQuestion = false;
    private volatile boolean awaitingUpdateQuestion = false;

    // Guarda a última lista de perguntas (actualizada ao receber LIST_QUESTIONS_RESPONSE)
    private final List<Question> lastQuestions = new ArrayList<>();
    private final Map<Integer, Integer> answersCountByQuestion = new HashMap<>();

    // Tabela actualmente aberta no diálogo de listagem (pode ser null)
    private TableView<Question> questionsTable = null;

    // Flags de espera para operações assíncronas
    private volatile boolean awaitingCreateQuestion = false;
    private volatile boolean awaitingListQuestions  = false;
    private volatile boolean awaitingViewAnswers    = false;

    // Filtro actual para listagem (apenas lado cliente, não envia mais para o servidor)
    private volatile String currentFilter = null;

    // Guarda a pergunta seleccionada para visualização de respostas
    private volatile Question pendingViewQuestion = null;

    // Referência ao diálogo de detalhes atualmente aberto (se houver).
    // Usada para fechar automaticamente após uma edição bem-sucedida.
    private volatile Dialog<?> currentDetailsDialog = null;

    // Nome que aparece no header (perfil do docente)
    private String teacherDisplayName = "Docente";

    // indicador na janela de listagem
    private volatile HBox listLoadingBox = null;
    // indicador na janela de edição
    private volatile HBox dialogLoadingBox = null;

    public TeacherDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userName, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application   = application;
        this.userName      = userName;
        this.userEmail     = userEmail;
        this.view          = new TeacherDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        // inicializa listeners depois de `view` existir

        this.notificationListener = evt -> {
            String notification = (String) evt.getNewValue();
            if (notification != null) {
                Platform.runLater(() -> {
                    view.addNotification(notification);
                    view.update();
                });
            }
        };

        this.createQuestionListener = evt -> {
            if (!awaitingCreateQuestion) return;
            awaitingCreateQuestion = false;
            CreateQuestionResponseDTO resp = (CreateQuestionResponseDTO) evt.getNewValue();
            Platform.runLater(() -> {
                showSuccessAlert("Pergunta Criada",
                        "A pergunta foi criada com sucesso!\nCódigo: " +
                                (resp != null ? resp.accessCode() : ""));
                refreshQuestions(); // volta a pedir TODAS as perguntas
            });
        };

        this.listQuestionsListener = evt -> {
            if (!awaitingListQuestions) return;
            awaitingListQuestions = false;
            @SuppressWarnings("unchecked")
            List<Question> list = (List<Question>) evt.getNewValue();

            Platform.runLater(() -> {
                lastQuestions.clear();
                if (list != null) lastQuestions.addAll(list);
                // actualiza a tabela do diálogo, se existir, aplicando filtro apenas no cliente
                refreshQuestionsTableView();
                // actualiza métricas do dashboard SEM depender do filtro
                updateDashboardStats();
            });
        };

        this.viewAnswersListener = evt -> {
            if (!awaitingViewAnswers) return;
            awaitingViewAnswers = false;
            @SuppressWarnings("unchecked")
            List<Answer> answers = (List<Answer>) evt.getNewValue();
            final Question q = pendingViewQuestion;
            pendingViewQuestion = null;
            Platform.runLater(() -> {
                if (q != null) {
                    // guarda nº respostas desta pergunta
                    answersCountByQuestion.put(q.getId(), answers == null ? 0 : answers.size());
                    updateDashboardStats();
                    showAnswersDetails(q, answers);
                } else {
                    showErrorAlert("Erro", "Pergunta não encontrada.");
                }
            });
        };

        this.joinQuestionListener = evt -> {
            // trata resposta do servidor com QUESTION_DETAILS -> prop JOIN_QUESTION_RESPONSE
            Question q = (Question) evt.getNewValue();
            awaitingJoinQuestion = false;
            Platform.runLater(() -> {
                // esconder indicador de loading da listagem se existir
                try { if (listLoadingBox != null) listLoadingBox.setVisible(false); } catch (Exception ignored) {}
                if (q != null) {
                    // mostra directamente o diálogo de edição com os dados vindos do servidor
                    openEditDialogForQuestion(q, null);
                } else {
                    showErrorAlert("Erro", "Pergunta não encontrada no servidor.");
                }
            });
        };

        this.updateQuestionListener = evt -> {
            if (!awaitingUpdateQuestion) return;
            awaitingUpdateQuestion = false;
            Object payload = evt.getNewValue();
            String result = payload instanceof String s ? s : null;
            Platform.runLater(() -> {
                // esconder indicators caso estejam visíveis
                try { if (dialogLoadingBox != null) dialogLoadingBox.setVisible(false); } catch (Exception ignored) {}
                try { if (listLoadingBox != null) listLoadingBox.setVisible(false); } catch (Exception ignored) {}
                if ("edit-ok".equalsIgnoreCase(result)) {
                    showSuccessAlert("Pergunta Atualizada", "A pergunta foi atualizada com sucesso.");
                    refreshQuestions();
                    // fecha a janela de detalhes se estiver aberta (feedback visual e limpeza)
                    if (currentDetailsDialog != null) {
                        try { currentDetailsDialog.close(); } catch (Exception ignored) {}
                        currentDetailsDialog = null;
                    }
                } else {
                    String msg = result != null ? result : "Falha ao actualizar a pergunta.";
                    // mostra erro e tenta reactivar o botão OK do diálogo de edição caso esteja presente
                    showErrorAlert("Erro ao Actualizar", msg);
                    if (currentDetailsDialog != null) {
                        try {
                            Button ok = (Button) currentDetailsDialog.getDialogPane().lookupButton(ButtonType.OK);
                            if (ok != null) ok.setDisable(false);
                        } catch (Exception ignored) {}
                    }
                }
            });
        };

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
        service.addPropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);

        // Resposta à criação de pergunta
        service.addPropertyChangeListener(ClientService.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);

        // Lista de perguntas devolvida
        service.addPropertyChangeListener(ClientService.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);

        // Resposta ao pedido de detalhes de pergunta (JOIN/QUESTION_DETAILS)
        service.addPropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);

        // Respostas de uma pergunta (vista do docente) devolvidas
        service.addPropertyChangeListener(ClientService.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);

        //Resposta à edição de pergunta
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);

    }

    /** Remove listeners do ClientService para evitar memory‑leaks.
     *  Deve ser chamado quando o controller deixar de ser usado. */
    @Override
    public void dispose() {
        ClientService service = clientManager.getService();
        if (service == null) return;
        try {
            service.removePropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
            service.removePropertyChangeListener(ClientService.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);
            service.removePropertyChangeListener(ClientService.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);
        } catch (Exception ignored) {
            // garantir que dispose é robusto mesmo que o serviço já tenha sido fechado
        }
        // garantir que quaisquer indicadores de loading ficam escondidos
        try { if (listLoadingBox != null) listLoadingBox.setVisible(false); } catch (Exception ignored) {}
        try { if (dialogLoadingBox != null) dialogLoadingBox.setVisible(false); } catch (Exception ignored) {}
    }

    private void updateDashboardStats() {
        int total = lastQuestions.size();
        int active = (int) lastQuestions.stream()
                .filter(q -> "ACTIVE".equalsIgnoreCase(q.getState().name()))
                .count();

        // soma total de respostas conhecidas (só para perguntas cujas respostas já foram consultadas)
        int totalAnswers = answersCountByQuestion.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();

        view.updateStats(total, active, totalAnswers);
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
        statementField.setPromptText("Escreva o enunciado da pergunta...");

        Label numOptionsLabel = new Label("Número de Opções:");
        numOptionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 4, 4);
        numOptionsSpinner.setPrefWidth(100);

        Label optionsLabel = new Label("Opções:");
        optionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField(); optA.setPromptText("Opção A");
        TextField optB = new TextField(); optB.setPromptText("Opção B");
        TextField optC = new TextField(); optC.setPromptText("Opção C");
        TextField optD = new TextField(); optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        numOptionsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int n = newVal == null ? 2 : newVal;
            optionsBox.getChildren().clear();
            if (n >= 1) optionsBox.getChildren().add(optA);
            if (n >= 2) optionsBox.getChildren().add(optB);
            if (n >= 3) optionsBox.getChildren().add(optC);
            if (n >= 4) optionsBox.getChildren().add(optD);
        });

        // forçar layout inicial consistente com o valor inicial
        int init = numOptionsSpinner.getValue();
        optionsBox.getChildren().clear();
        if (init >= 1) optionsBox.getChildren().add(optA);
        if (init >= 2) optionsBox.getChildren().add(optB);
        if (init >= 3) optionsBox.getChildren().add(optC);
        if (init >= 4) optionsBox.getChildren().add(optD);


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
                ev.consume(); // mantém a janela aberta
                return;
            }

            int numOptions = numOptionsSpinner.getValue();
            List<Option> options = new ArrayList<>();
            TextField[] allOptions = {optA, optB, optC, optD};
            OptionLetter[] letters = OptionLetter.values();

            // 1) contar respostas preenchidas
            int filledCount = 0;
            for (int i = 0; i < numOptions; i++) {
                if (!allOptions[i].getText().trim().isEmpty())
                    filledCount++;
            }
            if (filledCount < 2) {
                showErrorAlert("Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                ev.consume();
                return;
            }

            // 2) garantir que todas as respostas até ao nº escolhido estão preenchidas
            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    showErrorAlert("Erro", "Preencha todas as respostas até ao número escolhido.");
                    ev.consume();
                    return;
                }
                options.add(new Option(letters[i], optText));
            }

            // 3) datas obrigatórias
            LocalDate startD = startDate.getValue();
            LocalDate endD   = endDate.getValue();
            String startT    = startTime.getText().trim();
            String endT      = endTime.getText().trim();
            if (startD == null || endD == null || startT.isEmpty() || endT.isEmpty()) {
                showErrorAlert("Erro", "Datas e horas de início e fim são obrigatórias.");
                ev.consume();
                return;
            }

            try {
                DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
                LocalTime sTime = LocalTime.parse(startT, timeFormat);
                LocalTime eTime = LocalTime.parse(endT, timeFormat);
                LocalDateTime startAt = LocalDateTime.of(startD, sTime);
                LocalDateTime endAt   = LocalDateTime.of(endD, eTime);

                // 4) validar ordem das datas
                if (!endAt.isAfter(startAt)) {
                    showErrorAlert("Erro", "A data/hora de fim deve ser posterior à data/hora de início.");
                    ev.consume();
                    return;
                }

                Integer teacherId = clientManager.getUserId();
                if (teacherId == null) {
                    showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                    ev.consume();
                    return;
                }

                awaitingCreateQuestion = true;
                System.out.println("ID: " + teacherId);
                clientManager.getQuestionService().createQuestion(new CreateQuestionDTO(
                        statement, teacherId, options, OptionLetter.valueOf(correctCombo.getValue()), startAt, endAt));

                // sucesso: deixa o evento seguir e a dialog fecha normalmente
            } catch (Exception e) {
                showErrorAlert("Erro", "Formato de hora inválido (utilize HH:MM).");
                ev.consume(); // não fecha a janela
            }
        });

        dialog.showAndWait();
    }


    /** Handler para listar perguntas, usando filtros (filtro só no cliente). */
    public void onListQuestions() {
        Dialog<Void> dialog = new Dialog<>();
        // assegurar que o diálogo de listagem pertence à janela principal para modalidade correta
        try { dialog.initOwner(stage); dialog.initModality(Modality.WINDOW_MODAL); } catch (Exception ignored) {}
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
        filters.getChildren().addAll(filterLabel, filterCombo);

        TableView<Question> table = new TableView<>();

        // abrir menu de ações (Editar / Eliminar / Cancelar) ao fazer duplo clique numa linha
        // usamos ContextMenu para que um clique fora do menu feche-o automaticamente
        table.setRowFactory(tv -> {
            TableRow<Question> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    Question selected = row.getItem();
                    if (selected == null) return;

                    // Se a pergunta estiver expirada, mostramos as respostas diretamente
                    if ("EXPIRED".equalsIgnoreCase(selected.getState().name())) {
                        openAnswersForQuestion(selected);
                        return;
                    }

                    // criar menu contextual com as opções pedidas
                    ContextMenu menu = new ContextMenu();
                    MenuItem editItem = new MenuItem("Editar Pergunta");
                    MenuItem deleteItem = new MenuItem("Eliminar Pergunta");
                    MenuItem cancelItem = new MenuItem("Cancelar");

                    // Editar: verifica se é permitido e pede detalhes ao servidor
                    editItem.setOnAction(ae -> {
                        int cnt = answersCountByQuestion.getOrDefault(selected.getId(), 0);
                        if (cnt > 0) {
                            showErrorAlert("Editar Indisponível", "A pergunta já tem respostas e não pode ser editada.");
                            return;
                        }
                        Integer teacherId = clientManager.getUserId();
                        if (teacherId == null) {
                            showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                            return;
                        }
                        awaitingJoinQuestion = true;
                        // mostra indicador de loading na listagem (não-modal)
                        try { if (listLoadingBox != null) listLoadingBox.setVisible(true); } catch (Exception ignored) {}
                        clientManager.getQuestionService()
                                .joinQuestion(new JoinQuestionDTO(selected.getAccessCode(), teacherId));
                    });

                    // Eliminar: confirmação e envio do pedido
                    deleteItem.setOnAction(ae -> {
                        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                        confirmAlert.setTitle("Confirmar Eliminação");
                        confirmAlert.setHeaderText("Tem certeza que deseja eliminar a pergunta " + selected.getAccessCode() + "?");
                        confirmAlert.setContentText("Esta ação não pode ser desfeita.");
                        confirmAlert.initOwner(table.getScene() != null ? table.getScene().getWindow() : stage);
                        confirmAlert.showAndWait().ifPresent(resp -> {
                            if (resp == ButtonType.OK) {
                                Integer teacherId = clientManager.getUserId();
                                if (teacherId == null) {
                                    showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                                    return;
                                }
                                clientManager.getQuestionService().deleteQuestion(new DeleteQuestionDTO(selected.getId(), teacherId));
                                showSuccessAlert("Pedido de eliminação enviado",
                                        "A pergunta " + selected.getAccessCode() + " será eliminada (caso não tenha respostas).");
                                refreshQuestions();
                            }
                        });
                    });

                    // Cancelar: apenas fecha o menu
                    cancelItem.setOnAction(ae -> { });

                    menu.getItems().addAll(editItem, deleteItem, new SeparatorMenuItem(), cancelItem);
                    // mostra o menu na posição do clique
                    menu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });

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
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String s = q.getStartAt().format(fmt);
            String e = q.getEndAt().format(fmt);
            String str = s + " - " + e;
            return new SimpleStringProperty(str);
        });


        TableColumn<Question, String> stateCol = new TableColumn<>("Estado");
        stateCol.setCellValueFactory(data -> {
            String internal = data.getValue().getState().name();
            String label;
            switch (internal) {
                case "ACTIVE" -> label = "Ativo";
                case "FUTURE" -> label = "Futura";
                case "EXPIRED" -> label = "Expirada";
                default -> label = internal;
            }
            return new SimpleStringProperty(label);
        });

        table.getColumns().addAll(codeCol, stmtCol, periodCol, stateCol);

        content.getChildren().addAll(filters, table);

        // guarda referência à tabela do diálogo
        this.questionsTable = table;

        // Filtro passa a ser reactivo: ao mudar o valor, actualiza a tabela localmente
        filterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            String sel = newVal != null ? newVal : "Todas";
            if ("Ativas".equalsIgnoreCase(sel)) currentFilter = "active";
            else if ("Futuras".equalsIgnoreCase(sel)) currentFilter = "future";
            else if ("Expiradas".equalsIgnoreCase(sel)) currentFilter = "expired";
            else currentFilter = null;

            // apenas filtra a tabela em memória; não volta a pedir ao servidor
            refreshQuestionsTableView();
        });

        // Carrega perguntas inicialmente (SEM filtro no servidor)
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
        } else {
            awaitingListQuestions = true;
            clientManager.getQuestionService()
                    .listQuestions(new ListQuestionsDTO(teacherId, null)); // null => todas
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();

        // diálogo fechado -> deixa de haver tabela activa
        this.questionsTable = null;
    }

    private boolean isExpired(Question q) {
        return q != null && "EXPIRED".equalsIgnoreCase(q.getState().name());
    }

    private void openAnswersForQuestion(Question q) {
        if (!isExpired(q)) {
            showErrorAlert("Erro", "Só pode consultar respostas depois de a pergunta expirar.");
            return;
        }
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
            return;
        }
        pendingViewQuestion = q;
        awaitingViewAnswers = true;
        clientManager.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
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
        // aqui também passa a pedir SEM filtro; filtro é só do lado do cliente
        clientManager.getQuestionService().listQuestions(new ListQuestionsDTO(teacherId, null));
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

                if (!"EXPIRED".equalsIgnoreCase(q.getState().name())) {
                    showErrorAlert("Respostas indisponíveis",
                            "As respostas só podem ser consultadas quando a pergunta estiver expirada.");
                    return;
                }

                pendingViewQuestion = q;
                awaitingViewAnswers = true;
                clientManager.getAnswerService()
                        .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
            }
        });
    }

    // Helper: mostra detalhe da pergunta (sem lista de respostas) — usado para perguntas não expirada
    private void showQuestionDetails(Question q) {
        Dialog<Void> dialog = new Dialog<>();
        // definir owner para que o diálogo de detalhe seja modal em relação ao diálogo de listagem
        try {
            if (questionsTable != null && questionsTable.getScene() != null) {
                dialog.initOwner(questionsTable.getScene().getWindow());
                dialog.initModality(Modality.WINDOW_MODAL);
            } else if (stage != null) {
                dialog.initOwner(stage);
                dialog.initModality(Modality.WINDOW_MODAL);
            }
        } catch (Exception ignored) {}
        // Guardar referência para permitir fecho por updateQuestionListener
        this.currentDetailsDialog = dialog;

        dialog.setTitle("Detalhe - " + q.getAccessCode());
        dialog.setHeaderText("Detalhes da pergunta");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(650);

        VBox infoBox = new VBox(5);
        infoBox.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 15; -fx-border-radius: 5; -fx-background-radius: 5;");
        Label questionLabel = new Label("Pergunta: " + q.getStatement());
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        Label correctLabel = new Label("Resposta correta: " + (q.getCorrectOption() != null ? q.getCorrectOption().name() : ""));
        correctLabel.setFont(Font.font("Arial", 13));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String periodStr = q.getStartAt().format(fmt) + " - " + q.getEndAt().format(fmt);
        Label periodLabel = new Label("Período: " + periodStr);
        periodLabel.setFont(Font.font("Arial", 13));
        infoBox.getChildren().addAll(questionLabel, correctLabel, periodLabel);

        // Estatísticas com base em answersCountByQuestion se disponível
        int knownAnswers = answersCountByQuestion.getOrDefault(q.getId(), 0);
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-background-radius: 5;");
        Label answersKnown = new Label("Respostas conhecidas: " + knownAnswers);
        answersKnown.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        statsBox.getChildren().add(answersKnown);

        content.getChildren().addAll(infoBox, statsBox);

        dialog.getDialogPane().setContent(content);

        ButtonType editButtonType = new ButtonType("Editar Pergunta", ButtonBar.ButtonData.OTHER);
        ButtonType deleteButtonType = new ButtonType("Eliminar Pergunta", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(editButtonType, deleteButtonType, ButtonType.CLOSE);

        Button editButton = (Button) dialog.getDialogPane().lookupButton(editButtonType);
        editButton.setOnAction(ev -> {
            // Verifica se já existem respostas conhecidas (não permite editar)
            int cnt = answersCountByQuestion.getOrDefault(q.getId(), 0);
            if (cnt > 0) {
                showErrorAlert("Editar Indisponível", "A pergunta já tem respostas e não pode ser editada.");
                return;
            }
            this.openEditDialogForQuestion(q, dialog);
        });

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setOnAction(ev -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirmar Eliminação");
            confirmAlert.setHeaderText("Tem certeza que deseja eliminar a pergunta " + q.getAccessCode() + "?");
            confirmAlert.setContentText("Esta ação não pode ser desfeita.");
            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    Integer teacherId = clientManager.getUserId();
                    if (teacherId == null) {
                        showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    clientManager.getQuestionService()
                            .deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                    showSuccessAlert("Pedido de eliminação enviado",
                            "A pergunta " + q.getAccessCode() + " será eliminada (caso não tenha respostas).\n");

                    refreshQuestions();
                    dialog.close();
                }
            });
        });

        dialog.showAndWait();
        if (this.currentDetailsDialog == dialog) this.currentDetailsDialog = null;
    }

    /** Mostra as respostas da pergunta (chamado ao receber VIEW_ANSWERS_RESPONSE) */
    private void showAnswersDetails(Question q, List<Answer> answers) {
        String code = q.getAccessCode();

        Dialog<Void> dialog = new Dialog<>();
        // definir owner/modalidade para manter foco correcto quando chamado a partir da lista
        try {
            if (questionsTable != null && questionsTable.getScene() != null) {
                dialog.initOwner(questionsTable.getScene().getWindow());
                dialog.initModality(Modality.WINDOW_MODAL);
            } else if (stage != null) {
                dialog.initOwner(stage);
                dialog.initModality(Modality.WINDOW_MODAL);
            }
        } catch (Exception ignored) {}
        dialog.setTitle("Respostas - " + code);
        dialog.setHeaderText("Respostas submetidas pelos estudantes");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(750);

        // ========== Caixa de informação da pergunta ==========
        VBox infoBox = new VBox(5);
        infoBox.setStyle(
                "-fx-background-color: #ecf0f1;" +
                        "-fx-padding: 15;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;"
        );

        Label questionLabel = new Label("Pergunta: " + q.getStatement());
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label correctLabel = new Label("Resposta correta: " + q.getCorrectOption().name());
        correctLabel.setFont(Font.font("Arial", 13));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String periodStr = q.getStartAt().format(fmt) + " - " + q.getEndAt().format(fmt);
        Label periodLabel = new Label("Período: " + periodStr);
        periodLabel.setFont(Font.font("Arial", 13));

        infoBox.getChildren().addAll(questionLabel, correctLabel, periodLabel);

        // ========== Estatísticas rápidas ==========
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(15));
        statsBox.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #bdc3c7;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;"
        );

        int total = (answers == null ? 0 : answers.size());
        long correctCnt = (answers == null ? 0 : answers.stream().filter(Answer::isCorrect).count());
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

        // ========== Tabela de respostas ==========
        TableView<Answer> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Answer, String> studCol = new TableColumn<>("Nº Aluno");
        studCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getStudentId())));

        TableColumn<Answer, String> nameCol = new TableColumn<>("Nome");
        nameCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getStudentName() == null ? "" : data.getValue().getStudentName()
                ));

        TableColumn<Answer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getStudentEmail() == null ? "" : data.getValue().getStudentEmail()
                ));

        TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
        answerCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSelectedOption().name()));

        table.getColumns().addAll(studCol, nameCol, emailCol, answerCol);
        if (answers != null) {
            table.getItems().addAll(answers);
        }

        content.getChildren().addAll(infoBox, statsBox, new Label("Respostas:"), table);
        dialog.getDialogPane().setContent(content);

        ButtonType exportButtonType = new ButtonType("Exportar CSV", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Eliminar Pergunta", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, deleteButtonType, ButtonType.CLOSE);

        // botão Exportar CSV
        Button exportButton = (Button) dialog.getDialogPane().lookupButton(exportButtonType);
        exportButton.setOnAction(ev -> {
            exportAnswersToCsv(q, answers);
            ev.consume(); // não fecha o diálogo automaticamente
        });

        // botão Eliminar Pergunta (continua igual ao que já tinhas)
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setOnAction(ev -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirmar Eliminação");
            confirmAlert.setHeaderText("Tem certeza que deseja eliminar a pergunta " + q.getAccessCode() + "?");
            confirmAlert.setContentText("Esta ação não pode ser desfeita.");
            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    Integer teacherId = clientManager.getUserId();
                    if (teacherId == null) {
                        showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    clientManager.getQuestionService()
                            .deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                    showSuccessAlert("Pedido de eliminação enviado",
                            "A pergunta " + q.getAccessCode() + " será eliminada (caso não tenha respostas).");

                    refreshQuestions();
                    dialog.close();
                }
            });
        });

        dialog.showAndWait();
    }
    /**
     * Exporta para CSV no formato pedido no enunciado, por ex.:
     *
     * "dia";"hora inicial";"hora final";"enunciado da pergunta";"opção certa"
     * "06-10-2025";"10:10";"10:12";"Texto da pergunta";"a"
     * "opção";"texto da opção"
     * "a";"Socket"
     * "b";"ServerSocket"
     * ...
     * "número de estudante";"nome";"e-mail";"resposta"
     * "123456";"Nome";"email@exemplo.com";"a"
     */
    private void exportAnswersToCsv(Question q, List<Answer> answers) {
        if (q == null) {
            showErrorAlert("Exportar CSV", "Pergunta inválida.");
            return;
        }

        if (answers == null || answers.isEmpty()) {
            showErrorAlert("Exportar CSV", "Ainda não existem respostas para esta pergunta.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar ficheiro CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ficheiros CSV", "*.csv")
        );

        String baseName = (q.getAccessCode() != null && !q.getAccessCode().isBlank())
                ? q.getAccessCode()
                : "pergunta";
        fileChooser.setInitialFileName("pergunta_" + baseName + ".csv");

        File file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            // utilizador cancelou
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        try (BufferedWriter writer = Files.newBufferedWriter(
                file.toPath(), StandardCharsets.UTF_8)) {

            // ===== 1ª linha: cabeçalho da pergunta =====
            writer.write("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");
            writer.newLine();

            // ===== 2ª linha: dados da pergunta =====
            String dia = q.getStartAt().toLocalDate().format(dateFmt);
            String horaInicial = q.getStartAt().toLocalTime().format(timeFmt);
            String horaFinal = q.getEndAt().toLocalTime().format(timeFmt);
            String enunciado = escapeCsv(q.getStatement());
            String opcaoCerta = q.getCorrectOption() != null
                    ? q.getCorrectOption().name().toLowerCase()
                    : "";

            writer.write("\"" + dia + "\";\"" + horaInicial + "\";\"" + horaFinal + "\";\""
                    + enunciado + "\";\"" + opcaoCerta + "\"");
            writer.newLine();
            writer.newLine();

            // ===== Bloco das opções =====
            writer.write("\"opção\";\"texto da opção\"");
            writer.newLine();

            if (q.getOptions() != null) {
                for (Option opt : q.getOptions()) {
                    if (opt == null) continue;
                    String letra = opt.getLetter() != null
                            ? opt.getLetter().name().toLowerCase()
                            : "";
                    String texto = escapeCsv(opt.getText());
                    writer.write("\"" + letra + "\";\"" + texto + "\"");
                    writer.newLine();
                }
            }
            writer.newLine();

            // ===== Bloco das respostas =====
            writer.write("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");
            writer.newLine();

            for (Answer a : answers) {
                if (a == null) continue;

                String numero = a.getStudentId() == null
                        ? ""
                        : a.getStudentId().toString();
                String nome = escapeCsv(a.getStudentName());
                String email = escapeCsv(a.getStudentEmail());
                String resp = a.getSelectedOption() != null
                        ? a.getSelectedOption().name().toLowerCase()
                        : "";

                writer.write("\"" + numero + "\";\"" + nome + "\";\"" + email + "\";\"" + resp + "\"");
                writer.newLine();
            }

        } catch (IOException e) {
            showErrorAlert("Exportar CSV", "Erro ao guardar o ficheiro: " + e.getMessage());
            return;
        }

        showSuccessAlert("Exportar CSV", "Ficheiro CSV gravado com sucesso.");
    }

    /** Escapa aspas dentro de campos CSV ( " -> "" ). */
    private String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    private String cleanCsvText(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
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

    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Docente");
        dialog.setHeaderText(null);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Avatar
        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        Label initials = new Label(getInitials(userName != null ? userName : userEmail));
        initials.getStyleClass().add("profile-avatar-initials");
        avatarCircle.getChildren().add(initials);

        Label nameLabel = new Label(userName != null ? userName : userEmail);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Label roleLabel = new Label("Docente");
        roleLabel.setFont(Font.font("Arial", 12));

        Label emailLabel = new Label(userEmail);
        emailLabel.setFont(Font.font("Arial", 12));

        VBox infoBox = new VBox(4, nameLabel, roleLabel, emailLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(20, avatarCircle, infoBox);
        header.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(
                "Os dados de perfil são geridos pela instituição.\n" +
                        "Para alterar o seu nome ou email, contacte a secretaria."
        );
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7f8c8d;");

        content.getChildren().addAll(header, new Separator(), hint);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private String getInitials(String text) {
        if (text == null || text.isBlank()) return "?";
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, 1).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
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
                    clientManager.getAuthService().logout();  // envia LOGOUT ao servidor
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                clientManager.getService().logout();          // limpa estado local
                application.showAuthentication();             // volta ao ecrã de login
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

    private void openEditDialogForQuestion(Question q, Dialog<?> detailsDialog) {
        Dialog<ButtonType> dialog = new Dialog<>();
        // definir owner para evitar problemas de foco com diálogos aninhados
        try {
            if (detailsDialog != null && detailsDialog.getDialogPane() != null &&
                    detailsDialog.getDialogPane().getScene() != null) {
                dialog.initOwner(detailsDialog.getDialogPane().getScene().getWindow());
                dialog.initModality(Modality.WINDOW_MODAL);
            } else if (questionsTable != null && questionsTable.getScene() != null) {
                dialog.initOwner(questionsTable.getScene().getWindow());
                dialog.initModality(Modality.WINDOW_MODAL);
            } else if (stage != null) {
                dialog.initOwner(stage);
                dialog.initModality(Modality.WINDOW_MODAL);
            }
        } catch (Exception ignored) {
            // se não for possível definir o owner, continua sem owner
        }
        dialog.setTitle("Editar Pergunta - " + q.getAccessCode());
        dialog.setHeaderText("Edite os dados da pergunta");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        Label statementLabel = new Label("Enunciado:");
        statementLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        TextArea statementField = new TextArea(q.getStatement());
        statementField.setPrefRowCount(3);
        statementField.setPrefWidth(500);
        statementField.setPromptText("Escreva o enunciado da pergunta...");

        Label numOptionsLabel = new Label("Número de Opções:");
        numOptionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 4, 4);
        numOptionsSpinner.setPrefWidth(100);

        Label optionsLabel = new Label("Opções:");
        optionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField(); optA.setPromptText("Opção A");
        TextField optB = new TextField(); optB.setPromptText("Opção B");
        TextField optC = new TextField(); optC.setPromptText("Opção C");
        TextField optD = new TextField(); optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        numOptionsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int n = newVal == null ? 2 : newVal;
            optionsBox.getChildren().clear();
            if (n >= 1) optionsBox.getChildren().add(optA);
            if (n >= 2) optionsBox.getChildren().add(optB);
            if (n >= 3) optionsBox.getChildren().add(optC);
            if (n >= 4) optionsBox.getChildren().add(optD);
        });

        // forçar layout inicial consistente com o valor inicial
        int init = numOptionsSpinner.getValue();
        optionsBox.getChildren().clear();
        if (init >= 1) optionsBox.getChildren().add(optA);
        if (init >= 2) optionsBox.getChildren().add(optB);
        if (init >= 3) optionsBox.getChildren().add(optC);
        if (init >= 4) optionsBox.getChildren().add(optD);


        Label correctLabel = new Label("Resposta Correta:");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue(q.getCorrectOption() != null ? q.getCorrectOption().name() : "A");

        Label periodLabel = new Label("Período de Disponibilidade:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        HBox periodBox = new HBox(10);
        DatePicker startDate = new DatePicker(q.getStartAt().toLocalDate());
        startDate.setPromptText("Data início");
        TextField startTime = new TextField(); startTime.setPromptText("HH:MM"); startTime.setPrefWidth(80);
        Label toLabel = new Label(" até ");
        DatePicker endDate = new DatePicker(q.getEndAt().toLocalDate()); endDate.setPromptText("Data fim");
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

        // Marcar opções existentes
        List<Option> currentOptions = q.getOptions();
        if (currentOptions != null) {
            int i = 0;
            for (Option opt : currentOptions) {
                if (i >= 4) break;
                TextField tf = (TextField) optionsBox.getChildren().get(i);
                tf.setText(opt.getText());
                i++;
            }
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);
        // Criar um HBox de loading não-modal que ficará visível apenas enquanto aguardamos a resposta do servidor
        HBox loadingBox = new HBox(10);
        loadingBox.setPadding(new Insets(10));
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(20, 20);
        Label loadingLabel = new Label("Aguarde... a actualizar a pergunta...");
        loadingBox.getChildren().addAll(pi, loadingLabel);
        loadingBox.setVisible(false);

        VBox container = new VBox(10);
        container.getChildren().addAll(scrollPane, loadingBox);
        dialog.getDialogPane().setContent(container);
        // Guarda referência para o listener poder manipular
        this.dialogLoadingBox = loadingBox;

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Atualizar Pergunta");
        okButton.setOnAction(ev -> {
            String statement = statementField.getText().trim();
            if (statement.isEmpty()) {
                showErrorAlert("Erro", "O enunciado não pode estar vazio.");
                ev.consume(); // mantém a janela aberta
                return;
            }

            int numOptions = numOptionsSpinner.getValue();
            List<Option> options = new ArrayList<>();
            TextField[] allOptions = {optA, optB, optC, optD};
            OptionLetter[] letters = OptionLetter.values();

            // 1) contar respostas preenchidas
            int filledCount = 0;
            for (int i = 0; i < numOptions; i++) {
                if (!allOptions[i].getText().trim().isEmpty())
                    filledCount++;
            }
            if (filledCount < 2) {
                showErrorAlert("Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                ev.consume();
                return;
            }

            // 2) garantir que todas as respostas até ao nº escolhido estão preenchidas
            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    showErrorAlert("Erro", "Preencha todas as respostas até ao número escolhido.");
                    ev.consume();
                    return;
                }
                options.add(new Option(letters[i], optText));
            }

            // 3) datas obrigatórias
            LocalDate startD = startDate.getValue();
            LocalDate endD   = endDate.getValue();
            String startT    = startTime.getText().trim();
            String endT      = endTime.getText().trim();
            if (startD == null || endD == null || startT.isEmpty() || endT.isEmpty()) {
                showErrorAlert("Erro", "Datas e horas de início e fim são obrigatórias.");
                ev.consume();
                return;
            }

            try {
                DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
                LocalTime sTime = LocalTime.parse(startT, timeFormat);
                LocalTime eTime = LocalTime.parse(endT, timeFormat);
                LocalDateTime startAt = LocalDateTime.of(startD, sTime);
                LocalDateTime endAt   = LocalDateTime.of(endD, eTime);

                // 4) validar ordem das datas
                if (!endAt.isAfter(startAt)) {
                    showErrorAlert("Erro", "A data/hora de fim deve ser posterior à data/hora de início.");
                    ev.consume();
                    return;
                }

                Integer teacherId = clientManager.getUserId();
                if (teacherId == null) {
                    showErrorAlert("Erro", "Sessão inválida. Faça login novamente.");
                    ev.consume();
                    return;
                }

                awaitingUpdateQuestion = true;
                // mostra indicador não-modal no diálogo de edição e desabilita botão OK para evitar múltiplos pedidos
                try {
                    if (dialogLoadingBox != null) dialogLoadingBox.setVisible(true);
                    okButton.setDisable(true);
                } catch (Exception ignored) {}

                // envia pedido de edição — o listener updateQuestionListener processará a resposta
                clientManager.getQuestionService().editQuestion(new EditQuestionDTO(
                        q.getId(), teacherId, statement, options, OptionLetter.valueOf(correctCombo.getValue()), startAt, endAt));

                // manter o diálogo aberto até receber confirmação (evita que feche imediatamente)
                ev.consume();
            } catch (Exception e) {
                showErrorAlert("Erro", "Formato de hora inválido (utilize HH:MM).");
                ev.consume(); // não fecha a janela
            }
        });

        // Mostra o diálogo de forma modal (mas mantendo-o aberto enquanto aguardamos a resposta do servidor)
        dialog.showAndWait();
        // limpeza local: garante que as referências ao loading desta instância são removidas
        try { if (this.dialogLoadingBox == loadingBox) this.dialogLoadingBox = null; } catch (Exception ignored) {}
        if (this.currentDetailsDialog == dialog) this.currentDetailsDialog = null;
    }

}
