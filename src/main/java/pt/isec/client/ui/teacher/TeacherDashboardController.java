package pt.isec.client.ui.teacher;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.core.ClientService;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.client.ui.util.dialogs.TeacherDialogs;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.UpdateTeacherDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * Controlador do dashboard do docente. Lida com criação, listagem
 * e visualização de respostas de perguntas em regime assíncrono.
 * Todos os pedidos ao servidor são enfileirados; as respostas são tratadas
 * por eventos (property changes) de ClientService.
 *
 * Esta classe implementa IDisposableProp para garantir que os listeners
 * são removidos em dispose() e evitar memory-leaks.
 */
public class TeacherDashboardController implements IDisposableProp {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final String userName;
    private final TeacherDashboardView view;

    // listeners (mantidos para permitir remove no dispose)
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener createQuestionListener;
    private final PropertyChangeListener listQuestionsListener;
    private final PropertyChangeListener viewAnswersListener;
    private final PropertyChangeListener answerSubmittedListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener updateQuestionListener;
    private final PropertyChangeListener updateProfileOkListener;
    private final PropertyChangeListener updateProfileFailListener;


    // última lista de perguntas recebidas
    private final List<Question> lastQuestions = new ArrayList<>();
    // nº de respostas por pergunta (idPergunta -> count)
    private final Map<Integer, Integer> answersCountByQuestion = new HashMap<>();

    // tabela de listagem (se diálogo de listar estiver aberto)
    private TableView<Question> questionsTable = null;

    // flags de espera
    private volatile boolean awaitingCreateQuestion = false;
    private volatile boolean awaitingListQuestions  = false;
    private volatile boolean awaitingViewAnswers    = false;
    private volatile boolean awaitingJoinQuestion   = false;
    private volatile boolean awaitingUpdateQuestion = false;

    // filtro actual na listagem (apenas cliente)
    private volatile String currentFilter = null;

    // pergunta pendente para ver respostas
    private volatile Question pendingViewQuestion = null;

    private enum PendingAction { NONE, EDIT, DELETE }
    private volatile PendingAction pendingAction = PendingAction.NONE;
    private volatile Question pendingActionQuestion = null;

    // diálogo de detalhes actualmente aberto (para poder fechar após edição)
    private volatile Dialog<?> currentDetailsDialog = null;
    private volatile Dialog<?> currentEditProfileDialog = null;


    // caixas de loading (quando existem)
    private volatile HBox listLoadingBox = null;
    private volatile HBox dialogLoadingBox = null;

    // flag para carregamento em massa de contagens de respostas
    private volatile boolean bulkLoadingAnswers = false;

    public TeacherDashboardController(Stage stage,
                                      ClientManager clientManager,
                                      ClientApplication application,
                                      String userName,
                                      String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.userName = userName;
        this.userEmail = userEmail;

        this.view = new TeacherDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        // ----------------- listeners -----------------
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
                Window owner = getCurrentOwnerWindow();
                String code = (resp != null && resp.accessCode() != null) ? resp.accessCode() : "";
                AlertUtils.showInfo(owner, "Pergunta Criada",
                        "A pergunta foi criada com sucesso!\nCódigo: " + code);
                refreshQuestions();
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
                refreshQuestionsTableView();
                updateDashboardStats();
                fetchAnswerCountsSequentially();
            });
        };

        this.viewAnswersListener = evt -> {
            if (!awaitingViewAnswers) return;
            awaitingViewAnswers = false;
            @SuppressWarnings("unchecked")
            List<Answer> answers = (List<Answer>) evt.getNewValue();
            final Question q = pendingViewQuestion; // q is the question for which answers were fetched
            pendingViewQuestion = null; // Clear pending question after processing

            Platform.runLater(() -> {
                if (q == null) {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Erro", "Pergunta não encontrada.");
                    return;
                }

                int count = (answers == null ? 0 : answers.size());
                answersCountByQuestion.put(q.getId(), count); // Update the count for this specific question
                updateDashboardStats();

                if (bulkLoadingAnswers) {
                    // during bulk load only update counters
                    return;
                }

                // there was a pending action (EDIT/DELETE)? handle it now
                if (pendingAction != null && pendingAction != PendingAction.NONE &&
                        pendingActionQuestion != null &&
                        pendingActionQuestion.getId().equals(q.getId())) {

                    PendingAction action = pendingAction;
                    Question target = pendingActionQuestion;

                    pendingAction = PendingAction.NONE;
                    pendingActionQuestion = null;

                    try {
                        if (listLoadingBox != null) listLoadingBox.setVisible(false);
                    } catch (Exception ignored) {}

                    if (action == PendingAction.EDIT) {
                        if (count > 0) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Editar Indisponível",
                                    "A pergunta já tem respostas e não pode ser editada.");
                            return;
                        }
                        // no answers -> request details from server
                        Integer teacherId = clientManager.getUserId();
                        if (teacherId == null) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Erro",
                                    "Sessão inválida. Faça login novamente.");
                            return;
                        }
                        awaitingJoinQuestion = true;
                        try {
                            if (listLoadingBox != null) listLoadingBox.setVisible(true);
                        } catch (Exception ignored) {}
                        clientManager.getQuestionService()
                                .joinQuestion(new JoinQuestionDTO(target.getAccessCode(), teacherId));
                        return;
                    } else if (action == PendingAction.DELETE) {
                        if (count > 0) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Eliminar Indisponível",
                                    "A pergunta já tem respostas e não pode ser eliminada.");
                            return;
                        }
                        Integer teacherId = clientManager.getUserId();
                        if (teacherId == null) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Erro",
                                    "Sessão inválida. Faça login novamente.");
                            return;
                        }
                        clientManager.getQuestionService()
                                .deleteQuestion(new DeleteQuestionDTO(target.getId(), teacherId));
                        AlertUtils.showInfo(getCurrentOwnerWindow(),
                                "Pedido enviado",
                                "A pergunta " + target.getAccessCode()
                                        + " será eliminada (caso não tenha respostas).");
                        refreshQuestions();
                        return;
                    }
                }

                // normal flow: show answers dialog (TeacherDialogs)
                Window owner = getCurrentOwnerWindow();
                TeacherDialogs.showAnswersDialog(owner, q, answers, () -> {
                    // callback for "Delete question" inside the dialog
                    Integer teacherId = clientManager.getUserId();
                    if (teacherId == null) {
                        AlertUtils.showError(owner, "Erro",
                                "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    clientManager.getQuestionService()
                            .deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                    AlertUtils.showInfo(owner, "Pedido enviado",
                            "A pergunta " + q.getAccessCode()
                                    + " será eliminada (caso não tenha respostas).");
                    refreshQuestions();
                });
            });
        };

        this.answerSubmittedListener = evt -> {
            Integer qid = null;
            Object v = evt.getNewValue();
            if (v instanceof Integer) qid = (Integer) v;
            else if (v instanceof String) {
                try { qid = Integer.parseInt((String) v); } catch (Exception ignored) {}
            }
            if (qid == null) return;

            final Integer finalQid = qid;
            Platform.runLater(() -> {
                Integer teacherId = clientManager.getUserId();
                if (teacherId == null) {
                    System.err.println("[TeacherDashboardController] Cannot re-fetch answer count: teacher ID is null.");
                    return;
                }

                // Create a minimal Question object for pendingViewQuestion.
                // The viewAnswersListener only needs the ID from this object to update answersCountByQuestion.
                Question dummyQuestion = new Question(finalQid, null, teacherId, null, null, null, null, null);

                pendingViewQuestion = dummyQuestion; // Set the pending question
                awaitingViewAnswers = true; // Indicate that we are waiting for answers
                try {
                    // Request the answers for this specific question from the server
                    clientManager.getAnswerService()
                            .viewAnswersForTeacher(new ViewAnswersDTO(finalQid, teacherId));
                } catch (Exception e) {
                    awaitingViewAnswers = false; // Clear flag on error
                    System.err.println("[TeacherDashboardController] Failed to re-fetch answer count for qid=" + finalQid + ": " + e.getMessage());
                    // Fallback: if re-fetch fails, a full refresh might be needed
                    refreshQuestions();
                }
            });
        };

        this.joinQuestionListener = evt -> {
            if (!awaitingJoinQuestion) return;
            awaitingJoinQuestion = false;
            Question q = (Question) evt.getNewValue();
            Platform.runLater(() -> {
                try {
                    if (listLoadingBox != null) listLoadingBox.setVisible(false);
                } catch (Exception ignored) {}

                if (q == null) {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Erro", "Pergunta não encontrada no servidor.");
                    return;
                }

                Integer teacherId = clientManager.getUserId();
                if (teacherId == null) {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Erro", "Sessão inválida. Faça login novamente.");
                    return;
                }

                TeacherDialogs.showEditQuestionDialog(
                        getCurrentOwnerWindow(),
                        q,
                        teacherId,
                        dto -> {
                            awaitingUpdateQuestion = true;
                            clientManager.getQuestionService().editQuestion(dto);
                        });
            });
        };

        this.updateQuestionListener = evt -> {
            if (!awaitingUpdateQuestion) return;
            awaitingUpdateQuestion = false;
            Object payload = evt.getNewValue();
            String result = (payload instanceof String s) ? s : null;

            Platform.runLater(() -> {
                try {
                    if (dialogLoadingBox != null) dialogLoadingBox.setVisible(false);
                } catch (Exception ignored) {}
                try {
                    if (listLoadingBox != null) listLoadingBox.setVisible(false);
                } catch (Exception ignored) {}

                Window owner = getCurrentOwnerWindow();
                if ("edit-ok".equalsIgnoreCase(result)) {
                    AlertUtils.showInfo(owner,
                            "Pergunta Atualizada",
                            "A pergunta foi atualizada com sucesso.");
                    refreshQuestions();
                    if (currentDetailsDialog != null) {
                        try { currentDetailsDialog.close(); } catch (Exception ignored) {}
                        currentDetailsDialog = null;
                    }
                } else {
                    String msg = (result != null ? result : "Falha ao actualizar a pergunta.");
                    AlertUtils.showError(owner, "Erro ao Actualizar", msg);
                    if (currentDetailsDialog != null) {
                        try {
                            Button ok = (Button) currentDetailsDialog.getDialogPane()
                                    .lookupButton(ButtonType.OK);
                            if (ok != null) ok.setDisable(false);
                        } catch (Exception ignored) {}
                    }
                }
            });
        };

        this.updateProfileOkListener = evt -> {
            Object v = evt.getNewValue();
            String msg = (v == null ? "Os dados do perfil foram atualizados com sucesso." : v.toString());
            Platform.runLater(() -> {
                if (currentEditProfileDialog != null) {
                    try {
                        currentEditProfileDialog.close();
                    } catch (Exception ignored) {}
                    currentEditProfileDialog = null;
                }
                AlertUtils.showInfo(getCurrentOwnerWindow(), "Perfil atualizado", msg);
            });
        };

        this.updateProfileFailListener = evt -> {
            Object v = evt.getNewValue();
            String msg = (v == null ? "Erro desconhecido" : v.toString());
            Platform.runLater(() -> {
                if (currentEditProfileDialog != null) {
                    try {
                        currentEditProfileDialog.close();
                    } catch (Exception ignored) {}
                    currentEditProfileDialog = null;
                }
                AlertUtils.showError(getCurrentOwnerWindow(), "Falha ao actualizar perfil", msg);
            });
        };


        setupPropertyChangeListeners();
        updateDashboardStats();
        refreshQuestions();
    }

    // ----------------------------------------------------------
    //  LIFECYCLE
    // ----------------------------------------------------------

    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);

        Platform.runLater(() -> {
            try { updateDashboardStats(); } catch (Exception ignored) {}
            try { refreshQuestions(); } catch (Exception ignored) {}
        });
    }

    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        service.addPropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.addPropertyChangeListener(ClientService.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);
        service.addPropertyChangeListener(ClientService.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);
        service.addPropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.addPropertyChangeListener(ClientService.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);
        service.addPropertyChangeListener(ClientService.PROP_ANSWER_SUBMITTED, answerSubmittedListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        service.addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, updateProfileFailListener);


        // Listener temporário para fazer refreshQuestions assim que estiver CONNECTED
        PropertyChangeListener connectListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object val = evt.getNewValue();
                if (val instanceof String s && "CONNECTED".equalsIgnoreCase(s)) {
                    Platform.runLater(() -> {
                        try { refreshQuestions(); } catch (Exception ignored) {}
                    });
                    try {
                        clientManager.getService().removePropertyChangeListener(
                                ClientService.PROP_CONNECTION_STATUS, this);
                    } catch (Exception ignored) {}
                }
            }
        };
        service.addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectListener);
    }

    @Override
    public void dispose() {
        ClientService service = clientManager.getService();
        if (service == null) return;

        try {
            service.removePropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
            service.removePropertyChangeListener(ClientService.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);
            service.removePropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);
            service.removePropertyChangeListener(ClientService.PROP_ANSWER_SUBMITTED, answerSubmittedListener);
            service.removePropertyChangeListener(ClientService.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);
            service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
            service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);

        } catch (Exception ignored) {
        }

        try { if (listLoadingBox != null) listLoadingBox.setVisible(false); } catch (Exception ignored) {}
        try { if (dialogLoadingBox != null) dialogLoadingBox.setVisible(false); } catch (Exception ignored) {}
    }

    // ----------------------------------------------------------
    //  MÉTRICAS / DASHBOARD
    // ----------------------------------------------------------

    private void updateDashboardStats() {
        int total = lastQuestions.size();
        int active = (int) lastQuestions.stream()
                .filter(q -> "ACTIVE".equalsIgnoreCase(q.getState().name()))
                .count();

        int totalAnswers = answersCountByQuestion.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        view.updateStats(total, active, totalAnswers);
    }

    // ----------------------------------------------------------
    //  CRIAR PERGUNTA
    // ----------------------------------------------------------

    /** Handler para criar nova pergunta */
    public void onCreateQuestion() {
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }

        Window owner = getCurrentOwnerWindow();
        TeacherDialogs.showCreateQuestionDialog(owner, teacherId, (CreateQuestionDTO dto) -> {
            awaitingCreateQuestion = true;
            clientManager.getQuestionService().createQuestion(dto);
        });
    }

    // ----------------------------------------------------------
    //  LISTAR PERGUNTAS
    // ----------------------------------------------------------

    /** Handler para listar perguntas, usando filtros (filtro só no cliente). */
    public void onListQuestions() {
        Dialog<Void> dialog = new Dialog<>();
        try {
            dialog.initOwner(stage);
            dialog.initModality(Modality.WINDOW_MODAL);
        } catch (Exception ignored) {}

        dialog.setTitle("Listar Perguntas");
        dialog.setHeaderText("Suas perguntas criadas");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(700);

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        Label filterLabel = new Label("Filtrar:");
        filterLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Todas", "Ativas", "Futuras", "Expiradas");
        filterCombo.setValue("Todas");
        filters.getChildren().addAll(filterLabel, filterCombo);

        TableView<Question> table = new TableView<>();
        table.setPrefHeight(400);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // double-click numa linha
        table.setRowFactory(tv -> {
            TableRow<Question> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {

                    Question selected = row.getItem();
                    if (selected == null) return;

                    if ("EXPIRED".equalsIgnoreCase(selected.getState().name())) {
                        openAnswersForQuestion(selected);
                        return;
                    }

                    ContextMenu menu = new ContextMenu();
                    MenuItem editItem = new MenuItem("Editar Pergunta");
                    MenuItem deleteItem = new MenuItem("Eliminar Pergunta");
                    MenuItem copyCodeItem = new MenuItem("Copiar Código"); // novo item

                    editItem.setOnAction(ae -> handleEditFromList(selected));
                    deleteItem.setOnAction(ae -> handleDeleteFromList(selected, table));
                    copyCodeItem.setOnAction(ae -> { // ação para copiar código
                        if (selected.getAccessCode() != null && !selected.getAccessCode().isEmpty()) {
                            Clipboard clipboard = Clipboard.getSystemClipboard();
                            ClipboardContent clipboardContent = new ClipboardContent();
                            clipboardContent.putString(selected.getAccessCode());
                            clipboard.setContent(clipboardContent);
                            AlertUtils.showInfo(
                                    getCurrentOwnerWindow(),
                                    "Código Copiado",
                                    "O código da pergunta foi copiado para a área de transferência."
                            );
                        } else {
                            // aqui estava showWarning -> trocado por showInfo (método existente)
                            AlertUtils.showInfo(
                                    getCurrentOwnerWindow(),
                                    "Código Indisponível",
                                    "Não há código de acesso para copiar."
                            );
                        }
                    });

                    menu.getItems().addAll(editItem, deleteItem, copyCodeItem);
                    menu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });

        TableColumn<Question, String> codeCol = new TableColumn<>("Código");
        codeCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        Objects.toString(data.getValue().getAccessCode(), "")));

        TableColumn<Question, String> stmtCol = new TableColumn<>("Enunciado");
        stmtCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        Objects.toString(data.getValue().getStatement(), "")));

        TableColumn<Question, String> periodCol = new TableColumn<>("Período");
        periodCol.setCellValueFactory(data -> {
            Question q = data.getValue();
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String s = q.getStartAt().format(fmt);
            String e = q.getEndAt().format(fmt);
            return new SimpleStringProperty(s + " - " + e);
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

        this.questionsTable = table;

        filterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            String sel = (newVal != null ? newVal : "Todas");
            if ("Ativas".equalsIgnoreCase(sel)) currentFilter = "active";
            else if ("Futuras".equalsIgnoreCase(sel)) currentFilter = "future";
            else if ("Expiradas".equalsIgnoreCase(sel)) currentFilter = "expired";
            else currentFilter = null;
            refreshQuestionsTableView();
        });

        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
        } else {
            awaitingListQuestions = true;
            clientManager.getQuestionService()
                    .listQuestions(new ListQuestionsDTO(teacherId, null));
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();

        this.questionsTable = null;
    }

    private void handleEditFromList(Question selected) {
        Integer known = answersCountByQuestion.get(selected.getId());
        if (known != null) {
            if (known > 0) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Editar Indisponível",
                        "A pergunta já tem respostas e não pode ser editada.");
                return;
            }
            Integer teacherId = clientManager.getUserId();
            if (teacherId == null) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Erro", "Sessão inválida. Faça login novamente.");
                return;
            }
            awaitingJoinQuestion = true;
            try {
                if (listLoadingBox != null) listLoadingBox.setVisible(true);
            } catch (Exception ignored) {}
            clientManager.getQuestionService()
                    .joinQuestion(new JoinQuestionDTO(selected.getAccessCode(), teacherId));
            return;
        }

        // não sabemos nº respostas -> pedir ao servidor via viewAnswers
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }
        pendingAction = PendingAction.EDIT;
        pendingActionQuestion = selected;
        pendingViewQuestion = selected;
        awaitingViewAnswers = true;
        try {
            if (listLoadingBox != null) listLoadingBox.setVisible(true);
        } catch (Exception ignored) {}
        clientManager.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(selected.getId(), teacherId));
    }

    private void handleDeleteFromList(Question selected, TableView<Question> table) {
        Integer knownDel = answersCountByQuestion.get(selected.getId());
        if (knownDel != null) { // We know the count
            if (knownDel > 0) { // And it has answers
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Eliminar Indisponível",
                        "A pergunta já tem respostas e não pode ser eliminada.");
                return;
            }
            // No answers, proceed with delete confirmation
            Window owner = (table.getScene() != null ? table.getScene().getWindow() : getCurrentOwnerWindow());
            boolean confirm = AlertUtils.showConfirmation(
                    owner,
                    "Confirmar Eliminação",
                    "Tem certeza que deseja eliminar a pergunta " + selected.getAccessCode() + "?",
                    "Esta ação não pode ser desfeita."
            );
            if (!confirm) return;

            Integer teacherId = clientManager.getUserId();
            if (teacherId == null) {
                AlertUtils.showError(owner, "Erro", "Sessão inválida. Faça login novamente.");
                return;
            }
            clientManager.getQuestionService()
                    .deleteQuestion(new DeleteQuestionDTO(selected.getId(), teacherId));
            AlertUtils.showInfo(owner,
                    "Pedido enviado",
                    "A pergunta " + selected.getAccessCode()
                            + " será eliminada (caso não tenha respostas).");
            refreshQuestions();
            return;
        }

        // We don't know the answer count -> request it and then handle
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }

        pendingAction = PendingAction.DELETE;
        pendingActionQuestion = selected;
        pendingViewQuestion = selected;
        awaitingViewAnswers = true;
        try {
            if (listLoadingBox != null) listLoadingBox.setVisible(true);
        } catch (Exception ignored) {}
        clientManager.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(selected.getId(), teacherId));
    }

    private boolean isExpired(Question q) {
        return q != null && "EXPIRED".equalsIgnoreCase(q.getState().name());
    }

    private void openAnswersForQuestion(Question q) {
        if (!isExpired(q)) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Só pode consultar respostas depois de a pergunta expirar.");
            return;
        }

        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }
        pendingViewQuestion = q;
        awaitingViewAnswers = true;
        clientManager.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
    }

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

    private void refreshQuestions() {
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) return;
        awaitingListQuestions = true;
        clientManager.getQuestionService()
                .listQuestions(new ListQuestionsDTO(teacherId, null));
    }

    // ----------------------------------------------------------
    //  VER RESPOSTAS POR CÓDIGO
    // ----------------------------------------------------------

    public void onViewAnswers() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ver Respostas");
        dialog.setHeaderText("Ver respostas de uma pergunta");
        dialog.setContentText("Código da pergunta:");
        dialog.showAndWait().ifPresent(code -> {
            if (code == null || code.trim().isEmpty()) return;

            String trimmed = code.trim();
            Integer teacherId = clientManager.getUserId();
            if (teacherId == null) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Erro", "Sessão inválida. Faça login novamente.");
                return;
            }

            Question q = findQuestionByCode(trimmed);
            if (q == null) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Erro", "Pergunta não encontrada ou não é sua.");
                return;
            }

            if (!"EXPIRED".equalsIgnoreCase(q.getState().name())) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Respostas indisponíveis",
                        "As respostas só podem ser consultadas quando a pergunta estiver expirada.");
                return;
            }

            pendingViewQuestion = q;
            awaitingViewAnswers = true;
            clientManager.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
        });
    }

    private Question findQuestionByCode(String accessCode) {
        if (accessCode == null || accessCode.isBlank()) return null;
        for (Question q : lastQuestions) {
            if (accessCode.equalsIgnoreCase(q.getAccessCode())) {
                return q;
            }
        }
        return null;
    }

    // ----------------------------------------------------------
    //  PERFIL / LOGOUT
    // ----------------------------------------------------------

    /** Mostra o perfil do docente (informativo) */
    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Docente");
        dialog.setHeaderText(null);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Avatar + iniciais via UiUtils
        String initialsText = UiUtils.getInitials(userName != null ? userName : userEmail);

        javafx.scene.layout.StackPane avatarCircle = new javafx.scene.layout.StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");
        Label initials = new Label(initialsText);
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

    public void onEditProfile() {
        Dialog<ButtonType> dialog = new Dialog<>();
        this.currentEditProfileDialog = dialog;
        dialog.setTitle("Editar Perfil");
        dialog.setHeaderText("Editar dados pessoais");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label nameLabel = new Label("Nome:");
        TextField nameField = new TextField(userName != null ? userName : "");

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField(userEmail != null ? userEmail : "");

        Label oldPwLabel = new Label("Password atual (só se alterar):");
        PasswordField oldPw = new PasswordField();
        Label newPwLabel = new Label("Nova password (deixe em branco para não alterar):");
        PasswordField newPw = new PasswordField();

        content.getChildren().addAll(nameLabel, nameField,
                emailLabel, emailField,
                oldPwLabel, oldPw,
                newPwLabel, newPw);

        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != save) return;
            String n = nameField.getText().trim();
            String e = emailField.getText().trim();
            String opw = oldPw.getText();
            String npw = newPw.getText();

            if (n.isBlank() || e.isBlank()) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Erro", "Nome e email são obrigatórios.");
                return;
            }

            try {
                Integer uid = clientManager.getUserId();
                UpdateTeacherDTO dto = new UpdateTeacherDTO(
                        uid, uid, n, e,
                        (opw == null || opw.isBlank()) ? null : opw,
                        (npw == null || npw.isBlank()) ? null : npw
                );
                clientManager.getAuthService().updateTeacher(dto);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void onLogout() {
        Window owner = getCurrentOwnerWindow();
        boolean confirm = AlertUtils.showConfirmation(
                owner,
                "Confirmar Logout",
                "Deseja realmente sair?",
                "Será necessário fazer login novamente."
        );
        if (!confirm) return;

        try {
            clientManager.getAuthService().logout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dispose(); // Limpa os listeners antes de fazer logout
        clientManager.getService().logout();
        application.showAuthentication();
    }

    // ----------------------------------------------------------
    //  OWNER WINDOW / AUX
    // ----------------------------------------------------------

    private Window getCurrentOwnerWindow() {
        try {
            if (currentDetailsDialog != null &&
                    currentDetailsDialog.getDialogPane() != null &&
                    currentDetailsDialog.getDialogPane().getScene() != null)
                return currentDetailsDialog.getDialogPane().getScene().getWindow();
        } catch (Exception ignored) {}

        try {
            if (questionsTable != null && questionsTable.getScene() != null)
                return questionsTable.getScene().getWindow();
        } catch (Exception ignored) {}

        try {
            if (view != null && view.getScene() != null)
                return view.getScene().getWindow();
        } catch (Exception ignored) {}

        return stage;
    }

    // ----------------------------------------------------------
    //  BULK LOAD DE CONTAGEM DE RESPOSTAS
    // ----------------------------------------------------------

    private void fetchAnswerCountsSequentially() {
        Integer teacherId = clientManager.getUserId();
        if (teacherId == null) return;
        if (bulkLoadingAnswers) return;

        new Thread(() -> {
            bulkLoadingAnswers = true;
            try {
                List<Question> snapshot = List.copyOf(lastQuestions);
                for (Question q : snapshot) {
                    if (q == null) continue;
                    if (answersCountByQuestion.containsKey(q.getId())) continue;

                    // não pedir respostas de perguntas futuras (não faz sentido)
                    if ("FUTURE".equalsIgnoreCase(q.getState().name())) continue;

                    pendingViewQuestion = q;
                    awaitingViewAnswers = true;
                    try {
                        clientManager.getAnswerService()
                                .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
                    } catch (Exception e) {
                        awaitingViewAnswers = false;
                        System.err.println("[TeacherController] falha a pedir respostas para q="
                                + q.getId() + ": " + e.getMessage());
                        continue;
                    }

                    long startWait = System.currentTimeMillis();
                    while (awaitingViewAnswers) {
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (System.currentTimeMillis() - startWait > 2000) {
                            // timeout de segurança ~2s
                            awaitingViewAnswers = false;
                            break;
                        }
                    }
                }
            } finally {
                bulkLoadingAnswers = false;
                Platform.runLater(this::updateDashboardStats);
            }
        }, "FetchAnswerCounts").start();
    }
}
