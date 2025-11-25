package pt.isec.client.ui.student;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.core.ClientService;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.client.ui.util.dialogs.StudentDialogs;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.auth.UpdateStudentDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Controlador do dashboard do estudante. Interage com o servidor para
 * responder a perguntas e obter histórico, seguindo o modelo de eventos.
 * Todos os pedidos são enfileirados e as respostas são tratadas via
 * propriedades do ClientService.
 */
public class StudentDashboardController implements IDisposableProp {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final String userEmail;
    private final String userName;
    private final StudentDashboardView view;

    // Listeners
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener submitAnswerOkListener;
    private final PropertyChangeListener submitAnswerFailListener;
    private final PropertyChangeListener listAnsweredListener;
    private final PropertyChangeListener updateProfileListener;

    // Flags de espera
    private volatile boolean awaitingJoinQuestion   = false;
    private volatile boolean awaitingSubmitAnswer   = false;
    private volatile boolean awaitingHistory        = false;

    public StudentDashboardController(Stage stage,
                                      ClientManager clientManager,
                                      ClientApplication application,
                                      String userName,
                                      String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.userName = userName;
        this.userEmail = userEmail;

        this.view = new StudentDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        // ----------------- Listeners -----------------
        this.notificationListener = evt -> {
            String notification = (String) evt.getNewValue();
            if (notification != null) {
                Platform.runLater(() -> {
                    view.addNotification(notification);
                    view.update();
                });
            }
        };

        this.joinQuestionListener = evt -> {
            if (!awaitingJoinQuestion) return;
            awaitingJoinQuestion = false;
            Question q = (Question) evt.getNewValue();

            UiUtils.runOnUiThread(() -> {
                try { view.hideLoading(); } catch (Exception ignored) {}
                if (q == null) {
                    showErrorAlert("Código inválido ou pergunta não existente.");
                } else {
                    openQuestionDialog(q);
                }
            });
        };

        this.submitAnswerOkListener = evt -> {
            if (!awaitingSubmitAnswer) return;
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() ->
                    showSuccessAlert("Resposta submetida com sucesso!",
                            msg == null ? "" : msg)
            );
        };

        this.submitAnswerFailListener = evt -> {
            if (!awaitingSubmitAnswer) return;
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() ->
                    showErrorAlert("Falha ao submeter a resposta: " +
                            (msg == null ? "" : msg))
            );
        };

        this.listAnsweredListener = evt -> {
            if (!awaitingHistory) return;
            awaitingHistory = false;
            @SuppressWarnings("unchecked")
            List<Answer> history = (List<Answer>) evt.getNewValue();
            UiUtils.runOnUiThread(() ->
                    StudentDialogs.showHistoryDialog(getOwnerWindow(), history)
            );
        };

        this.updateProfileListener = evt -> {
            Object v = evt.getNewValue();
            String msg = v == null ? null : v.toString();
            UiUtils.runOnUiThread(() -> {
                if ("ok".equalsIgnoreCase(msg)) {
                    AlertUtils.showInfo(getOwnerWindow(),
                            "Perfil atualizado",
                            "Os dados do perfil foram atualizados com sucesso.");
                } else {
                    AlertUtils.showError(getOwnerWindow(),
                            "Falha ao actualizar perfil",
                            msg == null ? "Erro desconhecido" : msg);
                }
            });
        };

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

        service.addPropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.addPropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.addPropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        service.addPropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        service.addPropertyChangeListener(ClientService.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_RESPONSE, updateProfileListener);
    }

    @Override
    public void dispose() {
        ClientService service = clientManager.getService();
        if (service == null) return;

        service.removePropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.removePropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        service.removePropertyChangeListener(ClientService.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_RESPONSE, updateProfileListener);

        try { view.hideLoading(); } catch (Exception ignored) {}
    }

    // ----------------------------------------------------------
    // PERFIL (apenas ver)
    // ----------------------------------------------------------

    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        Label initials = new Label(
                UiUtils.getInitials(userName != null ? userName : userEmail)
        );
        initials.getStyleClass().add("profile-avatar-initials");
        avatarCircle.getChildren().add(initials);

        Label nameLabel = new Label(userName != null ? userName : userEmail);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Label roleLabel = new Label("Estudante");
        roleLabel.setFont(Font.font("Arial", 12));

        Label emailLabel = new Label(userEmail);
        emailLabel.setFont(Font.font("Arial", 12));

        VBox infoBox = new VBox(4, nameLabel, roleLabel, emailLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(20, avatarCircle, infoBox);
        header.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("O nome e o email são definidos pela instituição.\n" +
                "Se precisar de alterar, contacte a secretaria.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7f8c8d;");

        content.getChildren().addAll(header, new Separator(), hint);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ----------------------------------------------------------
    // RESPONDER PERGUNTA
    // ----------------------------------------------------------

    /** Handler para solicitar uma pergunta (insere código e envia JoinQuestion) */
    public void onAnswerQuestion() {
        StudentDialogs.showEnterQuestionCodeDialog(getOwnerWindow(), code -> {
            Integer studentId = clientManager.getUserId();
            if (studentId == null) {
                showErrorAlert("Sessão inválida. Faça login novamente.");
                return;
            }
            awaitingJoinQuestion = true;
            try {
                view.showLoading("A carregar pergunta...");
                clientManager.getQuestionService()
                        .joinQuestion(new JoinQuestionDTO(code, studentId));
            } catch (Exception e) {
                try { view.hideLoading(); } catch (Exception ignored) {}
                awaitingJoinQuestion = false;
                showErrorAlert("Erro ao procurar pergunta: " + e.getMessage());
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

        StudentDialogs.showAnswerQuestionDialog(
                getOwnerWindow(),
                question,
                studentId,
                (SubmitAnswerDTO dto) -> {
                    awaitingSubmitAnswer = true;
                    try {
                        clientManager.getAnswerService().submitAnswer(dto);
                    } catch (Exception ex) {
                        awaitingSubmitAnswer = false;
                        showErrorAlert("Erro ao submeter resposta: " + ex.getMessage());
                    }
                });
    }

    // ----------------------------------------------------------
    // HISTÓRICO
    // ----------------------------------------------------------

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
            awaitingHistory = false;
            showErrorAlert("Erro ao obter histórico: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------
    // PERFIL (edição de dados + password)
    // ----------------------------------------------------------

    /** Edição do perfil do estudante */
    public void onProfile() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label numberLabel = new Label("Número de estudante:");
        TextField numberField = new TextField();
        numberField.setPromptText("Número de estudante");
        numberField.setText(String.valueOf(clientManager.getStudentNumber()));

        Label nameLabel = new Label("Nome:");
        TextField nameField = new TextField();
        nameField.setText(userName != null ? userName : "");

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        emailField.setText(userEmail != null ? userEmail : "");

        Label oldPwLabel = new Label("Password atual (só necessária se pretende alterar):");
        PasswordField oldPwField = new PasswordField();

        Label newPwLabel = new Label("Nova password (deixe vazio para não alterar):");
        PasswordField newPwField = new PasswordField();

        content.getChildren().addAll(
                numberLabel, numberField,
                nameLabel, nameField,
                emailLabel, emailField,
                oldPwLabel, oldPwField,
                newPwLabel, newPwField
        );

        dialog.getDialogPane().setContent(content);
        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CLOSE);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != saveButtonType) return;

            try {
                Integer number = Integer.parseInt(numberField.getText().trim());
                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String oldPw = oldPwField.getText();
                String newPw = newPwField.getText();

                if (name.isBlank() || email.isBlank()) {
                    showErrorAlert("Nome e email são obrigatórios.");
                    return;
                }

                UpdateStudentDTO dto = new UpdateStudentDTO(
                        clientManager.getUserId(), number, name, email,
                        (oldPw == null || oldPw.isBlank()) ? null : oldPw,
                        (newPw == null || newPw.isBlank()) ? null : newPw
                );
                clientManager.getAuthService().updateStudent(dto);
                // feedback vem via updateProfileListener
            } catch (NumberFormatException nfe) {
                showErrorAlert("Número de estudante inválido.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // ----------------------------------------------------------
    // LOGOUT
    // ----------------------------------------------------------

    public void onLogout() {
        boolean confirm = AlertUtils.showConfirmation(
                getOwnerWindow(),
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
        clientManager.getService().logout();
        application.showAuthentication();
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------

    private Window getOwnerWindow() {
        if (view != null && view.getScene() != null) {
            return view.getScene().getWindow();
        }
        return stage;
    }

    private void showSuccessAlert(String title, String message) {
        AlertUtils.showInfo(getOwnerWindow(), title, message);
    }

    private void showErrorAlert(String message) {
        AlertUtils.showError(getOwnerWindow(), "Erro", message);
    }
}
