package pt.isec.client.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.services.ClientService;
import pt.isec.client.ui.view.StudentDashboardView;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.auth.UpdateStudentDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.beans.PropertyChangeListener;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    // listeners finais — inicializados no construtor para garantir que 'view' está pronto
    // (mantém referências fortes para poder remover no dispose())
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener submitAnswerOkListener;
    private final PropertyChangeListener submitAnswerFailListener;
    private final PropertyChangeListener listAnsweredListener;
    private final PropertyChangeListener updateProfileListener;

    // Flags de espera
    // Estas flags sincronizam pedidos assíncronos com as respostas recebidas
    // (evitam que respostas antigas/processadas afectem estados futuros)
    private volatile boolean awaitingJoinQuestion = false;
    private volatile boolean awaitingSubmitAnswer = false;
    private volatile boolean awaitingHistory      = false;

    // Nome para o header (perfil do aluno)
    private String studentDisplayName = "Estudante";

    public StudentDashboardController(Stage stage, ClientManager clientManager,
                                      ClientApplication application, String userName, String userEmail) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application   = application;
        this.userName      = userName;
        this.userEmail     = userEmail;
        this.view          = new StudentDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        // inicializar listeners depois de `view` existir
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
            Platform.runLater(() -> {
                // esconder loading visual no view
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
            Platform.runLater(() ->
                    showSuccessAlert("Resposta submetida com sucesso!",
                            msg == null ? "" : msg)
            );
        };

        this.submitAnswerFailListener = evt -> {
            if (!awaitingSubmitAnswer) return;
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            Platform.runLater(() ->
                    showErrorAlert("Falha ao submeter a resposta: " +
                            (msg == null ? "" : msg))
            );
        };

        this.listAnsweredListener = evt -> {
            if (!awaitingHistory) return;
            awaitingHistory = false;
            @SuppressWarnings("unchecked")
            List<Answer> history = (List<Answer>) evt.getNewValue();
            Platform.runLater(() ->
                    showHistoryDialog(history)
            );
        };

        this.updateProfileListener = evt -> {
            Object v = evt.getNewValue();
            String msg = v == null ? null : v.toString();
            Platform.runLater(() -> {
                if ("ok".equalsIgnoreCase(msg)) {
                    showSuccessAlert("Perfil atualizado", "Os dados do perfil foram atualizados com sucesso.");
                } else {
                    showErrorAlert("Falha ao actualizar perfil: " + (msg == null ? "Erro desconhecido" : msg));
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
        // Regista os listeners no serviço; chamado no construtor depois de 'view' existir
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
        // Remove os listeners do serviço para evitar memory-leaks quando o controller for descartado
        ClientService service = clientManager.getService();
        if (service == null) return;
        service.removePropertyChangeListener(ClientService.PROP_NOTIFICATION, notificationListener);
        service.removePropertyChangeListener(ClientService.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        service.removePropertyChangeListener(ClientService.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        service.removePropertyChangeListener(ClientService.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        service.removePropertyChangeListener(ClientService.PROP_UPDATE_PROFILE_RESPONSE, updateProfileListener);

        // garantir que o indicador de loading do view fica escondido (caso o controller seja descartado enquanto aguardava)
        try { view.hideLoading(); } catch (Exception ignored) {}
    }


    public void onOpenProfile() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        Label initials = new Label(getInitials(userName != null ? userName : userEmail));
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

    private String getInitials(String text) {
        if (text == null || text.isBlank()) return "?";
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, 1).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    /** Handler para solicitar uma pergunta (insere código e envia JoinQuestion) */
    public void onAnswerQuestion() {
        // Pede ao utilizador o código da pergunta e inicia um pedido assíncrono
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
                        // mostra loading na view (não-modal)
                        view.showLoading("A carregar pergunta...");
                        clientManager.getQuestionService()
                                .joinQuestion(new JoinQuestionDTO(code, studentId));
                    } catch (Exception e) {
                        try { view.hideLoading(); } catch (Exception ignored) {}
                        showErrorAlert("Erro ao procurar pergunta: " + e.getMessage());
                        awaitingJoinQuestion = false;
                    }
                }
            }
        });
    }

    /** Abre a janela com a pergunta e envia a resposta seleccionada */
    private void openQuestionDialog(Question question) {
        // Constrói diálogo com as opções da pergunta e submete a resposta escolhida
        // 1) Obter o id do estudante (necessário para enviar a resposta ao servidor)
        Integer studentId = clientManager.getUserId();
        if (studentId == null) {
            // Se não houver sessão válida, avisar e abortar
            showErrorAlert("Sessão inválida. Faça login novamente.");
            return;
        }

        // 2) Cria o diálogo e preenche com enunciado e opções
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pergunta - " + question.getAccessCode());
        dialog.setHeaderText(question.getStatement());
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 3) Prepara grupo de opções (radio buttons) - apenas uma escolha permitida
        ToggleGroup group = new ToggleGroup();
        List<RadioButton> radioButtons = new ArrayList<>();
        List<Option> opts = question.getOptions();
        for (Option opt : opts) {
            // Cada opção mostra a letra e o texto: "A) Texto da opção"
            RadioButton rb = new RadioButton(opt.getLetter().name() + ") " + opt.getText());
            rb.setToggleGroup(group);
            rb.setFont(Font.font("Arial", 13));
            radioButtons.add(rb);
        }
        VBox optionsBox = new VBox(10);
        optionsBox.getChildren().addAll(radioButtons);
        content.getChildren().add(optionsBox);
        dialog.getDialogPane().setContent(content);

        // 4) Botões OK/Cancelar — OK envia a resposta selecionada
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");

        // 5) Ao clicar em OK: transformar a escolha do radio-button numa OptionLetter
        //    e enviar o pedido ao serviço de respostas. O campo awaitingSubmitAnswer
        //    evita que respostas concorrentes sejam tratadas indevidamente.
        okButton.setOnAction(ev -> {
            if (group.getSelectedToggle() != null) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                // O texto do radio começa com a letra seguida de ") ", por isso substring(0,1)
                String answerLetter = selected.getText().substring(0, 1);
                OptionLetter selectedOption = OptionLetter.valueOf(answerLetter);

                // Marca que estamos à espera da resposta do servidor para esta submissão
                awaitingSubmitAnswer = true;
                try {
                    // Envia o DTO com id da pergunta, id do estudante e opção escolhida
                    clientManager.getAnswerService().submitAnswer(
                            new SubmitAnswerDTO(question.getId(), studentId, selectedOption));
                } catch (Exception ex) {
                    // Se ocorrer erro local na chamada, limpa a flag e informa o utilizador
                    showErrorAlert("Erro ao submeter resposta: " + ex.getMessage());
                    awaitingSubmitAnswer = false;
                }
            }
        });
        // 6) Mostra o diálogo de forma modal até o utilizador fechar
        dialog.showAndWait();
    }

    /** Handler para solicitar o histórico de respostas */
    public void onShowHistory() {
        // Inicia pedido para obter histórico; resposta chega em listAnsweredListener
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
        // Mostra tabela com o histórico recebido; chamada via Platform.runLater
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);
        if (history == null || history.isEmpty()) {
            // Caso vazio: mostra mensagem de que não há respostas
            Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
            noDataLabel.setFont(Font.font("Arial", 14));
            noDataLabel.setTextFill(Color.web("#7f8c8d"));
            content.getChildren().add(noDataLabel);
        } else {
            // 1) Cria a tabela que vai mostrar data, pergunta, resposta e se foi correta
            TableView<Answer> table = new TableView<>();
            table.setPrefHeight(300);
            // Nota: CONSTRAINED_RESIZE_POLICY ajusta colunas ao espaço disponível
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            // 2) Coluna Data/Hora: formata a data do Answer para uma string legível
            TableColumn<Answer, String> dateCol = new TableColumn<>("Data/Hora");
            dateCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getAnsweredAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));

            // 3) Coluna Pergunta: mostra o enunciado se disponível, senão o id
            TableColumn<Answer, String> questionCol = new TableColumn<>("Pergunta");
            questionCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getQuestionStatement() != null
                            ? data.getValue().getQuestionStatement()
                            : String.valueOf(data.getValue().getQuestionId())
            ));

            // 4) Coluna Resposta: mostra a letra seleccionada
            TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
            answerCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getSelectedOption().name()));

            // 5) Coluna Correta?: converte boolean para "Sim"/"Não"
            TableColumn<Answer, String> resultCol = new TableColumn<>("Correta?");
            resultCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().isCorrect() ? "Sim" : "Não"));

            // 6) Adiciona colunas e popula a tabela com os dados recebidos
            table.getColumns().addAll(dateCol, questionCol, answerCol, resultCol);
            table.getItems().addAll(history);
            content.getChildren().add(table);
        }
        // 7) Mostra o diálogo com botão de fechar
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** NOVO: perfil do estudante (edição local do nome) */
    public void onProfile() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label numberLabel = new Label("Número de estudante:");
        TextField numberField = new TextField();
        numberField.setPromptText("Número de estudante");
        numberField.setText(String.valueOf(clientManager.getUserId()));

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

        content.getChildren().addAll(numberLabel, numberField, nameLabel, nameField, emailLabel, emailField,
                oldPwLabel, oldPwField, newPwLabel, newPwField);

        dialog.getDialogPane().setContent(content);
        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CLOSE);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == saveButtonType) {
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

                    UpdateStudentDTO dto = new UpdateStudentDTO(clientManager.getUserId(), number, name, email,
                            (oldPw == null || oldPw.isBlank()) ? null : oldPw,
                            (newPw == null || newPw.isBlank()) ? null : newPw);
                    clientManager.getAuthService().updateStudent(dto);
                    // feedback será dado pelo updateProfileListener
                } catch (NumberFormatException nfe) {
                    showErrorAlert("Número de estudante inválido.");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
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
