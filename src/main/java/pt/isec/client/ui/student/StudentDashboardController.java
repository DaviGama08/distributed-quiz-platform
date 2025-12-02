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
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.UpdateStudentDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Controller for the student dashboard.
 * <p>
 * Interacts with the server to:
 * <ul>
 *     <li>Join questions and submit answers</li>
 *     <li>Fetch the student's answer history</li>
 *     <li>Update profile information</li>
 * </ul>
 * Uses the event-based model of {@link ClientService}, with all requests enqueued
 * and responses delivered via property change events.
 */
public class StudentDashboardController implements IDisposableProp {

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private String userEmail;
    private String userName;
    private final StudentDashboardView view;

    // Listeners
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener submitAnswerOkListener;
    private final PropertyChangeListener submitAnswerFailListener;
    private final PropertyChangeListener listAnsweredListener;
    private final PropertyChangeListener updateProfileOkListener;
    private final PropertyChangeListener updateProfileFailListener;
    private final PropertyChangeListener userNameListener;
    private final PropertyChangeListener userEmailListener;
    private final PropertyChangeListener studentNumberListener;

    // Waiting flags
    private volatile boolean awaitingJoinQuestion = false;
    private volatile boolean awaitingSubmitAnswer = false;
    private volatile boolean awaitingHistory = false;

    /**
     * Creates a new controller for the student dashboard.
     *
     * @param stage          primary stage
     * @param clientManager  client manager with all services
     * @param application    reference to the main application
     * @param userName       initial student name
     * @param userEmail      initial student email
     */
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
        notificationListener = evt -> {
            String notification = (String) evt.getNewValue();
            if (notification != null) {
                Platform.runLater(() -> {
                    view.addNotification(notification);
                    view.update();
                });
            }
        };

        joinQuestionListener = evt -> {
            if (!awaitingJoinQuestion) {
                return;
            }
            awaitingJoinQuestion = false;
            Question q = (Question) evt.getNewValue();

            UiUtils.runOnUiThread(() -> {
                try {
                    view.hideLoading();
                } catch (Exception ignored) {
                }
                if (q == null) {
                    showErrorAlert("Código inválido ou pergunta não existente.");
                    view.addNotification("Falha ao carregar pergunta para o código indicado.");
                } else if (!q.isActive()) {
                    showErrorAlert("Não é possivel responder à pergunta.");
                    view.addNotification("Pergunta " + q.getAccessCode() + " não está ativa para resposta.");
                } else {
                    view.addNotification("Pergunta " + q.getAccessCode() + " carregada para resposta.");
                    openQuestionDialog(q);
                }
            });
        };

        submitAnswerOkListener = evt -> {
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                showSuccessAlert("Resposta submetida com sucesso!", msg == null ? "" : msg);
                view.addNotification("Resposta submetida com sucesso.");
            });
        };

        submitAnswerFailListener = evt -> {
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                String full = "Falha ao submeter a resposta: " + (msg == null ? "" : msg);
                showErrorAlert(full);
                view.addNotification(full);
            });
        };

        listAnsweredListener = evt -> {
            if (!awaitingHistory) {
                return;
            }
            awaitingHistory = false;
            @SuppressWarnings("unchecked")
            List<Answer> history = (List<Answer>) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                StudentDialogs.showHistoryDialog(getOwnerWindow(), history);
                view.addNotification("Histórico de respostas carregado (" +
                        (history == null ? 0 : history.size()) + " registos).");
            });
        };

        updateProfileOkListener = evt -> {
            AuthResponseDTO dto = (AuthResponseDTO) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                // Update local fields
                this.userName = dto.name();
                this.userEmail = dto.email();
                // Update view
                view.updateUserInfo(this.userName, this.userEmail);

                AlertUtils.showInfo(getOwnerWindow(),
                        "Perfil atualizado",
                        "Os dados do perfil foram atualizados com sucesso.");
                view.addNotification("Perfil atualizado: " + this.userName + " (" + this.userEmail + ").");
            });
        };

        updateProfileFailListener = evt -> {
            Object v = evt.getNewValue();
            String msg = (v == null) ? "Erro desconhecido" : v.toString();
            UiUtils.runOnUiThread(() -> {
                AlertUtils.showError(getOwnerWindow(),
                        "Falha ao actualizar perfil",
                        msg);
                view.addNotification("Falha ao atualizar perfil: " + msg);
            });
        };

        userNameListener = evt -> {
            this.userName = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> view.updateUserInfo(this.userName, this.userEmail));
        };

        userEmailListener = evt -> {
            this.userEmail = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> view.updateUserInfo(this.userName, this.userEmail));
        };

        studentNumberListener = evt -> {
            // No direct UI update needed for student number in the main dashboard view.
            // Internal state is maintained in ClientManager/Service.
        };

        setupPropertyChangeListeners();
    }

    /**
     * Shows the student dashboard.
     */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /**
     * Registers all property change listeners in the {@link ClientService}.
     */
    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        service.addPropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.addPropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.addPropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        service.addPropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        service.addPropertyChangeListener(ClientService.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        service.addPropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        service.addPropertyChangeListener(ClientService.PROP_USER_NAME, userNameListener);
        service.addPropertyChangeListener(ClientService.PROP_USER_EMAIL, userEmailListener);
        service.addPropertyChangeListener(ClientService.PROP_STUDENT_NUMBER, studentNumberListener);
    }

    /**
     * Removes all listeners and hides any loading indicator.
     */
    @Override
    public void dispose() {
        ClientService service = clientManager.getService();
        if (service == null) {
            return;
        }

        service.removePropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.removePropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        service.removePropertyChangeListener(ClientService.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        service.removePropertyChangeListener(ClientService.PROP_USER_NAME, userNameListener);
        service.removePropertyChangeListener(ClientService.PROP_USER_EMAIL, userEmailListener);
        service.removePropertyChangeListener(ClientService.PROP_STUDENT_NUMBER, studentNumberListener);

        try {
            view.hideLoading();
        } catch (Exception ignored) {
        }
    }

    // ----------------------------------------------------------
    // PROFILE (view only)
    // ----------------------------------------------------------

    /**
     * Opens a read-only dialog with the student's profile information.
     */
    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        String nameForInitials =
                clientManager.getService().getUserName() != null
                        ? clientManager.getService().getUserName()
                        : clientManager.getService().getUserEmail();

        Label initials = new Label(
                UiUtils.getInitials(nameForInitials)
        );
        initials.getStyleClass().add("profile-avatar-initials");
        avatarCircle.getChildren().add(initials);

        String nameLabelValue =
                clientManager.getService().getUserName() != null
                        ? clientManager.getService().getUserName()
                        : clientManager.getService().getUserEmail();

        Label nameLabel = new Label(nameLabelValue);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Label roleLabel = new Label("Estudante");
        roleLabel.setFont(Font.font("Arial", 12));

        Label emailLabel = new Label(clientManager.getService().getUserEmail());
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
    // ANSWER QUESTION
    // ----------------------------------------------------------

    /**
     * Handler to request a question by access code and open the answering dialog.
     */
    public void onAnswerQuestion() {
        StudentDialogs.showEnterQuestionCodeDialog(getOwnerWindow(), code -> {
            Integer studentId = clientManager.getUserId();
            if (studentId == null) {
                showErrorAlert("Sessão inválida. Faça login novamente.");
                view.addNotification("Falha ao procurar pergunta: sessão inválida.");
                return;
            }
            awaitingJoinQuestion = true;
            try {
                view.showLoading("A carregar pergunta...");
                clientManager.getQuestionService()
                        .joinQuestion(new JoinQuestionDTO(code, studentId));
            } catch (Exception e) {
                try {
                    view.hideLoading();
                } catch (Exception ignored) {
                }
                awaitingJoinQuestion = false;
                String msg = "Erro ao procurar pergunta: " + e.getMessage();
                showErrorAlert(msg);
                view.addNotification(msg);
            }
        });
    }

    /**
     * Opens the dialog with the question and sends the selected answer when submitted.
     *
     * @param question question to answer
     */
    private void openQuestionDialog(Question question) {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            view.addNotification("Sessão inválida ao tentar responder à pergunta.");
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
                        String msg = "Erro ao submeter resposta: " + ex.getMessage();
                        showErrorAlert(msg);
                        view.addNotification(msg);
                    }
                });
    }

    // ----------------------------------------------------------
    // HISTORY
    // ----------------------------------------------------------

    /**
     * Handler to request and display the student's answer history.
     */
    public void onShowHistory() {
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            view.addNotification("Falha ao obter histórico: sessão inválida.");
            return;
        }
        awaitingHistory = true;
        try {
            clientManager.getAnswerService().viewAnswersForStudent(studentId);
        } catch (Exception e) {
            awaitingHistory = false;
            String msg = "Erro ao obter histórico: " + e.getMessage();
            showErrorAlert(msg);
            view.addNotification(msg);
        }
    }

    // ----------------------------------------------------------
    // PROFILE (edit + password)
    // ----------------------------------------------------------

    /**
     * Opens the editable student profile dialog (name, email, student number and password).
     */
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
        nameField.setText(clientManager.getService().getUserName() != null
                ? clientManager.getService().getUserName() : "");

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        emailField.setText(clientManager.getService().getUserEmail() != null
                ? clientManager.getService().getUserEmail() : "");

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
            if (bt != saveButtonType) {
                return;
            }

            try {
                Integer number = Integer.parseInt(numberField.getText().trim());
                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String oldPw = oldPwField.getText();
                String newPw = newPwField.getText();

                if (name.isBlank() || email.isBlank()) {
                    showErrorAlert("Nome e email são obrigatórios.");
                    view.addNotification("Edição de perfil falhou: nome/email em falta.");
                    return;
                }

                UpdateStudentDTO dto = new UpdateStudentDTO(
                        clientManager.getUserId(), number, name, email,
                        (oldPw == null || oldPw.isBlank()) ? null : oldPw,
                        (newPw == null || newPw.isBlank()) ? null : newPw
                );
                clientManager.getAuthService().updateStudent(dto);
                // Feedback is delivered via updateProfile listeners
                view.addNotification("Pedido de atualização de perfil enviado.");
            } catch (NumberFormatException nfe) {
                showErrorAlert("Número de estudante inválido.");
                view.addNotification("Número de estudante inválido ao editar perfil.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // ----------------------------------------------------------
    // LOGOUT
    // ----------------------------------------------------------

    /**
     * Handles the logout action, including confirmation, service logout and navigation.
     */
    public void onLogout() {
        boolean confirm = AlertUtils.showConfirmation(
                getOwnerWindow(),
                "Confirmar Logout",
                "Deseja realmente sair?",
                "Será necessário fazer login novamente."
        );
        if (!confirm) {
            return;
        }

        try {
            clientManager.getAuthService().logout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dispose();
        clientManager.getService().logout();
        application.showAuthentication();
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------

    /**
     * Returns the owner window for dialogs (either from the view scene or the stage).
     *
     * @return owner window
     */
    private Window getOwnerWindow() {
        if (view != null && view.getScene() != null) {
            return view.getScene().getWindow();
        }
        return stage;
    }

    /**
     * Shows a success information dialog.
     *
     * @param title   dialog title
     * @param message dialog message
     */
    private void showSuccessAlert(String title, String message) {
        AlertUtils.showInfo(getOwnerWindow(), title, message);
    }

    /**
     * Shows an error dialog with a standard title.
     *
     * @param message error message
     */
    private void showErrorAlert(String message) {
        AlertUtils.showError(getOwnerWindow(), "Erro", message);
    }
}
