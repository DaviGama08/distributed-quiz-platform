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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import pt.isec.client.ClientApplication;
import pt.isec.client.core.ClientManager;
import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.client.ui.util.dialogs.TeacherDialogs;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.UpdateTeacherDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;

import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * Controller for the teacher dashboard.
 * <p>
 * Handles creation, listing, editing and answer visualization for questions
 * in an asynchronous/event-driven way. All server operations are performed
 * via client services and results are propagated through property changes.
 */
public class TeacherDashboardController implements IDisposableProp {

    private final Stage stage;
    private final IClientControllerContext clientControllerContext;
    private final ClientApplication application;
    private String userEmail;
    private String userName;
    private final TeacherDashboardView view;

    // listeners
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

    // diálogos
    private volatile Dialog<?> currentDetailsDialog = null;
    private volatile Dialog<?> currentEditProfileDialog = null;

    // caixas de loading (quando existirem)
    private volatile HBox listLoadingBox = null;
    private volatile HBox dialogLoadingBox = null;

    // flag para carregamento em massa de contagens de respostas
    private volatile boolean bulkLoadingAnswers = false;

    /**
     * Creates a new teacher dashboard controller and wires up all listeners.
     *
     * @param stage         main application stage
     * @param clientControllerContext client manager with services
     * @param application   main application
     * @param userName      teacher name
     * @param userEmail     teacher email
     */
    public TeacherDashboardController(Stage stage,
                                      ClientManager clientControllerContext,
                                      ClientApplication application,
                                      String userName,
                                      String userEmail) {
        this.stage = stage;
        this.clientControllerContext = clientControllerContext;
        this.application = application;
        this.userName = userName;
        this.userEmail = userEmail;

        this.view = new TeacherDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        /* ===================== LISTENERS ===================== */

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
                view.addNotification("Pergunta criada com código " + code + ".");
                refreshQuestions();
            });
        };

        this.listQuestionsListener = evt -> {
            if (!awaitingListQuestions) return;
            awaitingListQuestions = false;

            @SuppressWarnings("unchecked")
            List<Question> list = (List<Question>) evt.getNewValue();

            Platform.runLater(() -> {
                boolean changed = true;
                if (list != null && list.size() == lastQuestions.size()) {
                    changed = false;
                    for (int i = 0; i < list.size(); i++) {
                        if (!Objects.equals(lastQuestions.get(i).getId(), list.get(i).getId())) {
                            changed = true;
                            break;
                        }
                    }
                }

                lastQuestions.clear();
                if (list != null) lastQuestions.addAll(list);
                refreshQuestionsTableView();
                updateDashboardStats();

                if (changed) {
                    view.addNotification("Lista de perguntas atualizada. Total: " + lastQuestions.size());
                }

                // recarrega contagem de respostas após qualquer update
                fetchAnswerCountsSequentially();
            });
        };

        this.viewAnswersListener = evt -> {
            if (!awaitingViewAnswers) return;
            awaitingViewAnswers = false;
            @SuppressWarnings("unchecked")
            List<Answer> answers = (List<Answer>) evt.getNewValue();
            Question q = pendingViewQuestion;
            pendingViewQuestion = null;

            Platform.runLater(() -> {
                if (q == null) {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Erro", "Pergunta não encontrada.");
                    return;
                }

                int count = (answers == null ? 0 : answers.size());
                answersCountByQuestion.put(q.getId(), count);
                updateDashboardStats();

                if (bulkLoadingAnswers) {
                    // durante bulk load só atualiza contadores
                    return;
                }

                // havia uma ação pendente (EDIT/DELETE)?
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
                            view.addNotification("Tentativa de editar pergunta " + target.getAccessCode() +
                                    " falhou: já tem respostas.");
                            return;
                        }
                        Integer teacherId = clientControllerContext.getUserId();
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
                        clientControllerContext.getQuestionService()
                                .joinQuestion(new JoinQuestionDTO(target.getAccessCode(), teacherId));
                        return;
                    } else if (action == PendingAction.DELETE) {
                        if (count > 0) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Eliminar Indisponível",
                                    "A pergunta já tem respostas e não pode ser eliminada.");
                            view.addNotification("Tentativa de eliminar pergunta " + target.getAccessCode() +
                                    " falhou: já tem respostas.");
                            return;
                        }
                        Integer teacherId = clientControllerContext.getUserId();
                        if (teacherId == null) {
                            AlertUtils.showError(getCurrentOwnerWindow(),
                                    "Erro",
                                    "Sessão inválida. Faça login novamente.");
                            return;
                        }
                        clientControllerContext.getQuestionService()
                                .deleteQuestion(new DeleteQuestionDTO(target.getId(), teacherId));
                        AlertUtils.showInfo(getCurrentOwnerWindow(),
                                "Pedido enviado",
                                "A pergunta " + target.getAccessCode()
                                        + " será eliminada (caso não tenha respostas).");
                        view.addNotification("Pedido de eliminação da pergunta " +
                                target.getAccessCode() + " enviado.");
                        refreshQuestions();
                        return;
                    }
                }

                view.addNotification("Respostas carregadas para a pergunta " +
                        q.getAccessCode() + " (total: " + count + ").");

                Window owner = getCurrentOwnerWindow();
                TeacherDialogs.showAnswersDialog(owner, q, answers, () -> {
                    Integer teacherId = clientControllerContext.getUserId();
                    if (teacherId == null) {
                        AlertUtils.showError(owner, "Erro",
                                "Sessão inválida. Faça login novamente.");
                        return;
                    }
                    clientControllerContext.getQuestionService()
                            .deleteQuestion(new DeleteQuestionDTO(q.getId(), teacherId));
                    AlertUtils.showInfo(owner, "Pedido enviado",
                            "A pergunta " + q.getAccessCode()
                                    + " será eliminada (caso não tenha respostas).");
                    view.addNotification("Pedido de eliminação da pergunta " +
                            q.getAccessCode() + " enviado a partir do ecrã de respostas.");
                    refreshQuestions();
                });
            });
        };

        // Listener disparado quando o servidor envia ANSWER_SUBMITTED
        this.answerSubmittedListener = evt -> {
            Integer qid = null;
            Object v = evt.getNewValue();
            if (v instanceof Integer) {
                qid = (Integer) v;
            } else if (v instanceof String) {
                try {
                    qid = Integer.parseInt((String) v);
                } catch (Exception ignored) {
                }
            }
            if (qid == null) return;

            Integer finalQid = qid;
            Platform.runLater(() -> {
                // incrementa contador local da pergunta
                answersCountByQuestion.merge(finalQid, 1, Integer::sum);
                // recalcula métricas do dashboard
                updateDashboardStats();
                // log visível no painel
                view.addNotification("Uma nova resposta foi submetida à pergunta ID " + finalQid + ".");
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

                Integer teacherId = clientControllerContext.getUserId();
                if (teacherId == null) {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Erro", "Sessão inválida. Faça login novamente.");
                    return;
                }

                view.addNotification("Detalhes da pergunta " + q.getAccessCode() +
                        " carregados para edição.");

                TeacherDialogs.showEditQuestionDialog(
                        getCurrentOwnerWindow(),
                        q,
                        teacherId,
                        dto -> {
                            awaitingUpdateQuestion = true;
                            clientControllerContext.getQuestionService().editQuestion(dto);
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
                    view.addNotification("Pergunta atualizada com sucesso.");
                    refreshQuestions();
                    if (currentDetailsDialog != null) {
                        try {
                            currentDetailsDialog.close();
                        } catch (Exception ignored) {}
                        currentDetailsDialog = null;
                    }
                } else {
                    String msg = (result != null ? result : "Falha ao actualizar a pergunta.");
                    AlertUtils.showError(owner, "Erro ao Actualizar", msg);
                    view.addNotification("Falha ao atualizar pergunta: " + msg);
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
            Platform.runLater(() -> {
                AuthResponseDTO dto = (v instanceof AuthResponseDTO)
                        ? (AuthResponseDTO) v
                        : null;

                if (dto != null) {
                    this.userName  = dto.name();
                    this.userEmail = dto.email();

                    view.updateUserInfo(this.userName, this.userEmail);

                    if (currentEditProfileDialog != null) {
                        try {
                            currentEditProfileDialog.close();
                        } catch (Exception ignored) {}
                        currentEditProfileDialog = null;
                    }

                    AlertUtils.showInfo(getCurrentOwnerWindow(),
                            "Perfil atualizado",
                            "Os dados do perfil foram atualizados com sucesso.");
                    view.addNotification("Perfil atualizado: " + this.userName + " (" + this.userEmail + ").");
                } else {
                    String msg = (v == null ? "Perfil atualizado." : v.toString());
                    if (currentEditProfileDialog != null) {
                        try {
                            currentEditProfileDialog.close();
                        } catch (Exception ignored) {}
                        currentEditProfileDialog = null;
                    }
                    AlertUtils.showInfo(getCurrentOwnerWindow(), "Perfil atualizado", msg);
                    view.addNotification(msg);
                }
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
                view.addNotification("Falha ao atualizar perfil: " + msg);
            });
        };

        setupPropertyChangeListeners();
        updateDashboardStats();
        refreshQuestions();
    }

    /* ===================== LIFECYCLE ===================== */

    /**
     * Shows the teacher dashboard on the main stage and triggers an initial
     * refresh of stats and questions.
     */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);

        Platform.runLater(() -> {
            try { updateDashboardStats(); } catch (Exception ignored) {}
            try { refreshQuestions(); } catch (Exception ignored) {}
        });
    }

    private void setupPropertyChangeListeners() {
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_NOTIFICATION, notificationListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_ANSWER_SUBMITTED, answerSubmittedListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);

        // ⚠️ ConnectListener removido daqui conforme pedido
    }

    @Override
    public void dispose() {
        try {
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_NOTIFICATION, notificationListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_CREATE_QUESTION_RESPONSE, createQuestionListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_LIST_QUESTIONS_RESPONSE, listQuestionsListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_VIEW_ANSWERS_RESPONSE, viewAnswersListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_ANSWER_SUBMITTED, answerSubmittedListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_UPDATE_QUESTION_RESPONSE, updateQuestionListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
            clientControllerContext.removePropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        } catch (Exception ignored) { }

        try { if (listLoadingBox != null) listLoadingBox.setVisible(false); } catch (Exception ignored) {}
        try { if (dialogLoadingBox != null) dialogLoadingBox.setVisible(false); } catch (Exception ignored) {}
    }

    /* ===================== MÉTRICAS / DASHBOARD ===================== */

    /**
     * Updates the top-level dashboard metrics:
     * total questions, active questions and total answers received.
     */
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

    /* ===================== CRIAR PERGUNTA ===================== */

    /**
     * Handler for the "Create Question" action.
     * Opens the creation dialog and sends the request when confirmed.
     */
    public void onCreateQuestion() {
        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }

        Window owner = getCurrentOwnerWindow();
        TeacherDialogs.showCreateQuestionDialog(owner, teacherId, (CreateQuestionDTO dto) -> {
            awaitingCreateQuestion = true;
            clientControllerContext.getQuestionService().createQuestion(dto);
            view.addNotification("Pedido para criar nova pergunta enviado.");
        });
    }

    /* ===================== GERIR / LISTAR PERGUNTAS ===================== */

    /**
     * Opens the "Manage Questions" dialog, showing the teacher's questions,
     * with context menu actions for viewing answers, editing and deleting.
     */
    public void onManageQuestions() {
        Dialog<Void> dialog = new Dialog<>();
        try {
            dialog.initOwner(stage);
            dialog.initModality(Modality.WINDOW_MODAL);
        } catch (Exception ignored) {}

        dialog.setTitle("Gerir Perguntas");
        dialog.setHeaderText("Gerir as suas perguntas");

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

        table.setRowFactory(tv -> {
            TableRow<Question> row = new TableRow<>();

            MenuItem viewAnswersItem = new MenuItem("Ver Respostas (se expirada)");
            MenuItem editItem        = new MenuItem("Editar Pergunta");
            MenuItem deleteItem      = new MenuItem("Eliminar Pergunta");
            MenuItem copyCodeItem    = new MenuItem("Copiar Código");

            ContextMenu menu = new ContextMenu(viewAnswersItem, editItem, deleteItem, copyCodeItem);
            row.setContextMenu(menu);

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getClickCount() == 2
                        && event.getButton() == MouseButton.PRIMARY) {

                    Question selected = row.getItem();
                    if (selected == null) return;

                    if ("EXPIRED".equalsIgnoreCase(selected.getState().name())) {
                        openAnswersForQuestion(selected);
                    }
                }
            });

            viewAnswersItem.setOnAction(e -> {
                Question selected = row.getItem();
                if (selected == null) return;
                openAnswersForQuestion(selected);
            });

            editItem.setOnAction(e -> {
                Question selected = row.getItem();
                if (selected == null) return;
                handleEditFromList(selected);
            });

            deleteItem.setOnAction(e -> {
                Question selected = row.getItem();
                if (selected == null) return;
                handleDeleteFromList(selected, table);
            });

            copyCodeItem.setOnAction(e -> {
                Question selected = row.getItem();
                if (selected == null) return;
                if (selected.getAccessCode() != null && !selected.getAccessCode().isEmpty()) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(selected.getAccessCode());
                    clipboard.setContent(clipboardContent);
                    AlertUtils.showInfo(getCurrentOwnerWindow(),
                            "Código Copiado",
                            "O código da pergunta foi copiado para a área de transferência.");
                    view.addNotification("Código da pergunta " + selected.getAccessCode() +
                            " copiado para a área de transferência.");
                } else {
                    AlertUtils.showError(getCurrentOwnerWindow(),
                            "Código Indisponível",
                            "Não há código de acesso para copiar.");
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

        questionsTable = table;

        filterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            String sel = (newVal != null ? newVal : "Todas");
            if ("Ativas".equalsIgnoreCase(sel)) currentFilter = "active";
            else if ("Futuras".equalsIgnoreCase(sel)) currentFilter = "future";
            else if ("Expiradas".equalsIgnoreCase(sel)) currentFilter = "expired";
            else currentFilter = null;
            refreshQuestionsTableView();
        });

        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
        } else {
            awaitingListQuestions = true;
            clientControllerContext.getQuestionService()
                    .listQuestions(new ListQuestionsDTO(teacherId, null));
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();

        questionsTable = null;
    }

    /** Método antigo mantido por compatibilidade: delega para onManageQuestions(). */
    public void onListQuestions() {
        onManageQuestions();
    }

    private void handleEditFromList(Question selected) {
        Integer known = answersCountByQuestion.get(selected.getId());
        if (known != null) {
            if (known > 0) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Editar Indisponível",
                        "A pergunta já tem respostas e não pode ser editada.");
                view.addNotification("Tentou editar pergunta " + selected.getAccessCode() +
                        ", mas ela já tem respostas.");
                return;
            }
            Integer teacherId = clientControllerContext.getUserId();
            if (teacherId == null) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Erro", "Sessão inválida. Faça login novamente.");
                return;
            }
            awaitingJoinQuestion = true;
            clientControllerContext.getQuestionService()
                    .joinQuestion(new JoinQuestionDTO(selected.getAccessCode(), teacherId));
            return;
        }

        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }
        pendingAction = PendingAction.EDIT;
        pendingActionQuestion = selected;
        pendingViewQuestion = selected;
        awaitingViewAnswers = true;
        clientControllerContext.getAnswerService()
                .viewAnswersForTeacher(new ViewAnswersDTO(selected.getId(), teacherId));
    }

    private void handleDeleteFromList(Question selected, TableView<Question> table) {
        Integer knownDel = answersCountByQuestion.get(selected.getId());
        if (knownDel != null) {
            if (knownDel > 0) {
                AlertUtils.showError(getCurrentOwnerWindow(),
                        "Eliminar Indisponível",
                        "A pergunta já tem respostas e não pode ser eliminada.");
                view.addNotification("Tentou eliminar pergunta " + selected.getAccessCode() +
                        ", mas ela já tem respostas.");
                return;
            }
            Window owner = (table.getScene() != null ? table.getScene().getWindow() : getCurrentOwnerWindow());
            boolean confirm = AlertUtils.showConfirmation(
                    owner,
                    "Confirmar Eliminação",
                    "Tem certeza que deseja eliminar a pergunta " + selected.getAccessCode() + "?",
                    "Esta ação não pode ser desfeita."
            );
            if (!confirm) return;

            Integer teacherId = clientControllerContext.getUserId();
            if (teacherId == null) {
                AlertUtils.showError(owner, "Erro", "Sessão inválida. Faça login novamente.");
                return;
            }
            clientControllerContext.getQuestionService()
                    .deleteQuestion(new DeleteQuestionDTO(selected.getId(), teacherId));
            AlertUtils.showInfo(owner,
                    "Pedido enviado",
                    "A pergunta " + selected.getAccessCode()
                            + " será eliminada (caso não tenha respostas).");
            view.addNotification("Pedido de eliminação da pergunta " +
                    selected.getAccessCode() + " enviado.");
            refreshQuestions();
            return;
        }

        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }

        pendingAction = PendingAction.DELETE;
        pendingActionQuestion = selected;
        pendingViewQuestion = selected;
        awaitingViewAnswers = true;
        clientControllerContext.getAnswerService()
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

        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) {
            AlertUtils.showError(getCurrentOwnerWindow(),
                    "Erro", "Sessão inválida. Faça login novamente.");
            return;
        }
        pendingViewQuestion = q;
        awaitingViewAnswers = true;
        clientControllerContext.getAnswerService()
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
        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) return;
        awaitingListQuestions = true;
        clientControllerContext.getQuestionService()
                .listQuestions(new ListQuestionsDTO(teacherId, null));
    }

    /* ===================== VER RESPOSTAS POR CÓDIGO ===================== */

    /**
     * Handler for the "View Answers" by access code.
     * Requests the corresponding question (if owned by this teacher) and,
     * if expired, loads and shows its answers.
     */
    public void onViewAnswers() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ver Respostas");
        dialog.setHeaderText("Ver respostas de uma pergunta");
        dialog.setContentText("Código da pergunta:");
        dialog.showAndWait().ifPresent(code -> {
            if (code == null || code.trim().isEmpty()) return;

            String trimmed = code.trim();
            Integer teacherId = clientControllerContext.getUserId();
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
            clientControllerContext.getAnswerService()
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

    /* ===================== PERFIL / LOGOUT ===================== */

    /**
     * Shows a read-only profile dialog for the teacher.
     */
    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Docente");
        dialog.setHeaderText(null);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        String initialsText = UiUtils.getInitials(userName != null ? userName : userEmail);

        StackPane avatarCircle = new StackPane();
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

    /**
     * Opens an editable profile dialog for the teacher and sends an update
     * request when the user confirms.
     */
    public void onEditProfile() {
        Dialog<ButtonType> dialog = new Dialog<>();
        currentEditProfileDialog = dialog;
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
                view.addNotification("Edição de perfil falhou: nome/email em falta.");
                return;
            }

            try {
                Integer uid = clientControllerContext.getUserId();
                UpdateTeacherDTO dto = new UpdateTeacherDTO(
                        uid, uid, n, e,
                        (opw == null || opw.isBlank()) ? null : opw,
                        (npw == null || npw.isBlank()) ? null : npw
                );
                clientControllerContext.getAuthService().updateTeacher(dto);
                view.addNotification("Pedido de atualização de perfil enviado.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Handles teacher logout: asks for confirmation, sends the logout request,
     * clears local state and returns to the authentication screen.
     */
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
            clientControllerContext.getAuthService().logout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dispose();
        clientControllerContext.logout();
        application.showAuthentication();
    }

    /* ===================== OWNER WINDOW / AUX ===================== */

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

    /* ===================== BULK LOAD DE CONTAGEM DE RESPOSTAS ===================== */

    /**
     * Fetches answer counts for the teacher's questions in the background,
     * one by one, in order to keep dashboard metrics updated without blocking
     * the UI thread.
     */
    private void fetchAnswerCountsSequentially() {
        Integer teacherId = clientControllerContext.getUserId();
        if (teacherId == null) return;
        if (bulkLoadingAnswers) return;

        new Thread(() -> {
            bulkLoadingAnswers = true;
            try {
                List<Question> snapshot = List.copyOf(lastQuestions);
                for (Question q : snapshot) {
                    if (q == null) continue;
                    if (answersCountByQuestion.containsKey(q.getId())) continue;
                    if ("FUTURE".equalsIgnoreCase(q.getState().name())) continue;

                    pendingViewQuestion = q;
                    awaitingViewAnswers = true;
                    try {
                        clientControllerContext.getAnswerService()
                                .viewAnswersForTeacher(new ViewAnswersDTO(q.getId(), teacherId));
                    } catch (Exception e) {
                        awaitingViewAnswers = false;
                        Log.error(
                                TeacherDashboardController.class,
                                "Failed to request answers for question " + q.getId() + ": " + e.getMessage(),
                                e
                        );
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
