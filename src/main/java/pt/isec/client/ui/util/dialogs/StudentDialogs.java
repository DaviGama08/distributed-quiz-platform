package pt.isec.client.ui.util.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable dialogs for the student side.
 * <p>
 * Controllers only pass callbacks (DTOs, etc.) to these helpers.
 */
public final class StudentDialogs {

    private StudentDialogs() {
    }

    // --------------------------------------------------------------
    //  QUESTION CODE INPUT
    // --------------------------------------------------------------

    /**
     * Shows a dialog to enter the question access code.
     * <p>
     * When the user confirms, {@code onCodeEntered} is called with the trimmed code.
     *
     * @param owner         window owner for modality
     * @param onCodeEntered callback invoked with the entered code
     */
    public static void showEnterQuestionCodeDialog(Window owner,
                                                   Consumer<String> onCodeEntered) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Responder Pergunta");
        dialog.setHeaderText("Insira o código da pergunta");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label label = new Label("Código da Pergunta:");
        TextField codeField = new TextField();
        codeField.setPromptText("Código (ex: ABC123)");
        codeField.setPrefWidth(250);

        content.getChildren().addAll(label, codeField);
        dialog.getDialogPane().setContent(content);

        ButtonType searchButtonType =
                new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != searchButtonType) {
                return;
            }
            String code = codeField.getText();
            if (code == null || code.trim().isEmpty()) {
                AlertUtils.showError(owner, "Erro", "Código da pergunta é obrigatório.");
                return;
            }
            if (onCodeEntered != null) {
                onCodeEntered.accept(code.trim());
            }
        });
    }

    // --------------------------------------------------------------
    //  ANSWER QUESTION
    // --------------------------------------------------------------

    /**
     * Shows a question dialog with options for the student to choose.
     * <p>
     * On confirmation, calls {@code onSubmit} with a populated {@link SubmitAnswerDTO}
     * (questionId, studentId, selected option).
     *
     * @param owner     window owner
     * @param question  question to show
     * @param studentId student identifier
     * @param onSubmit  callback invoked when the user submits an answer
     */
    public static void showAnswerQuestionDialog(Window owner,
                                                Question question,
                                                Integer studentId,
                                                Consumer<SubmitAnswerDTO> onSubmit) {
        if (studentId == null) {
            AlertUtils.showError(owner, "Erro",
                    "Sessão inválida. Faça login novamente.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Pergunta - " + question.getAccessCode());
        dialog.setHeaderText(question.getStatement());

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();
        List<RadioButton> radioButtons = new ArrayList<>();

        List<Option> opts = question.getOptions();
        if (opts != null) {
            for (Option opt : opts) {
                RadioButton rb = new RadioButton(
                        opt.getLetter().name() + ") " + opt.getText()
                );
                rb.setToggleGroup(group);
                radioButtons.add(rb);
            }
        }

        VBox optionsBox = new VBox(10);
        optionsBox.getChildren().addAll(radioButtons);
        content.getChildren().add(optionsBox);
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");

        okButton.setOnAction(ev -> {
            if (group.getSelectedToggle() == null) {
                AlertUtils.showError(owner, "Erro",
                        "Selecione uma opção antes de submeter.");
                ev.consume();
                return;
            }

            RadioButton selected = (RadioButton) group.getSelectedToggle();
            String answerLetter = selected.getText().substring(0, 1);
            OptionLetter selectedOption;

            try {
                selectedOption = OptionLetter.valueOf(answerLetter);
            } catch (Exception ex) {
                AlertUtils.showError(owner, "Erro",
                        "Opção selecionada inválida.");
                ev.consume();
                return;
            }

            if (onSubmit != null) {
                SubmitAnswerDTO dto = new SubmitAnswerDTO(
                        question.getId(),
                        studentId,
                        selectedOption
                );
                onSubmit.accept(dto);
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  ANSWER HISTORY
    // --------------------------------------------------------------

    /**
     * Shows a dialog with the student's answer history.
     *
     * @param owner   window owner
     * @param history list of answers to display
     */
    public static void showHistoryDialog(Window owner,
                                         List<Answer> history) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        if (history == null || history.isEmpty()) {
            Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
            noDataLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14;");
            content.getChildren().add(noDataLabel);
        } else {
            TableView<Answer> table = new TableView<>();
            table.setPrefHeight(300);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<Answer, String> dateCol = new TableColumn<>("Data/Hora");
            dateCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getAnsweredAt()
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    )
            );

            TableColumn<Answer, String> questionCol = new TableColumn<>("Pergunta");
            questionCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getQuestionStatement() != null
                                    ? data.getValue().getQuestionStatement()
                                    : String.valueOf(data.getValue().getQuestionId())
                    )
            );

            TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
            answerCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getSelectedOption().name()
                    )
            );

            TableColumn<Answer, String> resultCol = new TableColumn<>("Correta?");
            resultCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().isCorrect() ? "Sim" : "Não"
                    )
            );

            table.getColumns().addAll(dateCol, questionCol, answerCol, resultCol);
            table.getItems().addAll(history);

            content.getChildren().add(table);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
