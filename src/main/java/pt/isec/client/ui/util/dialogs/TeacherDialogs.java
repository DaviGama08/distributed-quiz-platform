package pt.isec.client.ui.util.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.CsvExportUtils;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.EditQuestionDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility class that provides modal dialogs used by the teacher UI:
 * <ul>
 *     <li>Create question dialog</li>
 *     <li>Edit question dialog</li>
 *     <li>Question answers/details dialog</li>
 * </ul>
 */
public final class TeacherDialogs {

    /**
     * CSS style used to visually mark fields with validation errors.
     */
    private static final String ERROR_STYLE =
            "-fx-border-color: #e74c3c; -fx-border-width: 2; -fx-border-radius: 4; -fx-background-insets: 0;";

    private TeacherDialogs() {
        // utility class
    }

    // --------------------------------------------------------------
    //  CREATE QUESTION
    // --------------------------------------------------------------

    /**
     * Shows a dialog for creating a new question.
     *
     * @param owner    owner window (may be {@code null})
     * @param teacherId current teacher id (used for the DTO)
     * @param onSubmit callback invoked with the created {@link CreateQuestionDTO} if the user confirms
     */
    public static void showCreateQuestionDialog(Window owner,
                                                Integer teacherId,
                                                Consumer<CreateQuestionDTO> onSubmit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

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
        TextField optA = new TextField();
        optA.setPromptText("Opção A");
        TextField optB = new TextField();
        optB.setPromptText("Opção B");
        TextField optC = new TextField();
        optC.setPromptText("Opção C");
        TextField optD = new TextField();
        optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        numOptionsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int n = newVal == null ? 2 : newVal;
            optionsBox.getChildren().clear();
            if (n >= 1) {
                optionsBox.getChildren().add(optA);
            }
            if (n >= 2) {
                optionsBox.getChildren().add(optB);
            }
            if (n >= 3) {
                optionsBox.getChildren().add(optC);
            }
            if (n >= 4) {
                optionsBox.getChildren().add(optD);
            }
        });

        int init = numOptionsSpinner.getValue();
        optionsBox.getChildren().clear();
        if (init >= 1) {
            optionsBox.getChildren().add(optA);
        }
        if (init >= 2) {
            optionsBox.getChildren().add(optB);
        }
        if (init >= 3) {
            optionsBox.getChildren().add(optC);
        }
        if (init >= 4) {
            optionsBox.getChildren().add(optD);
        }

        Label correctLabel = new Label("Resposta Correta:");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue("A");

        Label periodLabel = new Label("Período de Disponibilidade:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        HBox periodBox = new HBox(8);
        DatePicker startDate = new DatePicker(LocalDate.now());
        startDate.setPromptText("Data início");
        Spinner<Integer> startHour = new Spinner<>(0, 23, 9);
        startHour.setPrefWidth(70);
        Spinner<Integer> startMinute = new Spinner<>(0, 59, 0);
        startMinute.setPrefWidth(70);
        Label toLabel = new Label(" até ");
        DatePicker endDate = new DatePicker(LocalDate.now());
        endDate.setPromptText("Data fim");
        Spinner<Integer> endHour = new Spinner<>(0, 23, 9);
        endHour.setPrefWidth(70);
        Spinner<Integer> endMinute = new Spinner<>(0, 59, 0);
        endMinute.setPrefWidth(70);
        periodBox.getChildren().addAll(startDate, startHour, new Label(":"), startMinute,
                toLabel, endDate, endHour, new Label(":"), endMinute);

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
            // Clear previous error highlights
            clearErrors(statementField, optA, optB, optC, optD, startDate, endDate, startHour, endHour);

            String statement = statementField.getText().trim();
            if (statement.isEmpty()) {
                AlertUtils.showError(owner, "Erro", "O enunciado não pode estar vazio.");
                markError(statementField);
                ev.consume();
                return;
            }

            int numOptions = numOptionsSpinner.getValue();
            List<Option> options = new ArrayList<>();
            TextField[] allOptions = {optA, optB, optC, optD};
            OptionLetter[] letters = OptionLetter.values();

            int filledCount = 0;
            for (int i = 0; i < numOptions; i++) {
                if (!allOptions[i].getText().trim().isEmpty()) {
                    filledCount++;
                }
            }
            if (filledCount < 2) {
                AlertUtils.showError(owner, "Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                // highlight all visible options
                for (int i = 0; i < numOptions; i++) {
                    markError(allOptions[i]);
                }
                ev.consume();
                return;
            }

            // Collect text and validate empties
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    AlertUtils.showError(owner, "Erro", "Preencha todas as respostas até ao número escolhido.");
                    for (int j = 0; j < numOptions; j++) {
                        markError(allOptions[i]);
                    }
                    ev.consume();
                    return;
                }
                texts.add(optText);
            }

            // Check for duplicated answers (case-insensitive)
            Set<String> seen = new HashSet<>();
            for (String t : texts) {
                String normal = t.toLowerCase();
                if (!seen.add(normal)) {
                    AlertUtils.showError(owner, "Erro", "As opções não podem conter respostas duplicadas");
                    ev.consume();
                    return;
                }
            }

            for (int i = 0; i < numOptions; i++) {
                options.add(new Option(letters[i], texts.get(i)));
            }

            LocalDate startD = startDate.getValue();
            LocalDate endD = endDate.getValue();
            if (startD == null || endD == null) {
                AlertUtils.showError(owner, "Erro", "Datas de início e fim são obrigatórias.");
                if (startD == null) {
                    markError(startDate);
                }
                if (endD == null) {
                    markError(endDate);
                }
                ev.consume();
                return;
            }

            try {
                LocalTime sTime = LocalTime.of(startHour.getValue(), startMinute.getValue());
                LocalTime eTime = LocalTime.of(endHour.getValue(), endMinute.getValue());
                LocalDateTime startAt = LocalDateTime.of(startD, sTime);
                LocalDateTime endAt = LocalDateTime.of(endD, eTime);

                if (!endAt.isAfter(startAt)) {
                    AlertUtils.showError(owner, "Erro",
                            "A data/hora de fim deve ser posterior à data/hora de início.");
                    markError(startDate);
                    markError(endDate);
                    ev.consume();
                    return;
                }

                if (teacherId == null) {
                    AlertUtils.showError(owner, "Erro", "Sessão inválida. Faça login novamente.");
                    ev.consume();
                    return;
                }

                CreateQuestionDTO dto = new CreateQuestionDTO(
                        statement, teacherId, options,
                        OptionLetter.valueOf(correctCombo.getValue()),
                        startAt, endAt
                );
                if (onSubmit != null) {
                    onSubmit.accept(dto);
                }
                // let dialog close
            } catch (Exception e) {
                AlertUtils.showError(owner, "Erro",
                        "Formato de hora inválido (utilize HH:MM).");
                markError(startHour);
                markError(endHour);
                ev.consume();
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  EDIT QUESTION
    // --------------------------------------------------------------

    /**
     * Shows a dialog for editing an existing question.
     *
     * @param owner     owner window (may be {@code null})
     * @param q         question to edit
     * @param teacherId current teacher id (used for the DTO)
     * @param onSubmit  callback invoked with the created {@link EditQuestionDTO} if the user confirms
     */
    public static void showEditQuestionDialog(Window owner,
                                              Question q,
                                              Integer teacherId,
                                              Consumer<EditQuestionDTO> onSubmit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
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
        int initialOptionsCount = (q.getOptions() == null
                ? 4
                : Math.max(2, Math.min(4, q.getOptions().size())));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 4, initialOptionsCount);
        numOptionsSpinner.setPrefWidth(100);

        Label optionsLabel = new Label("Opções:");
        optionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField();
        optA.setPromptText("Opção A");
        TextField optB = new TextField();
        optB.setPromptText("Opção B");
        TextField optC = new TextField();
        optC.setPromptText("Opção C");
        TextField optD = new TextField();
        optD.setPromptText("Opção D");
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        numOptionsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int n = newVal == null ? 2 : newVal;
            optionsBox.getChildren().clear();
            if (n >= 1) {
                optionsBox.getChildren().add(optA);
            }
            if (n >= 2) {
                optionsBox.getChildren().add(optB);
            }
            if (n >= 3) {
                optionsBox.getChildren().add(optC);
            }
            if (n >= 4) {
                optionsBox.getChildren().add(optD);
            }
        });

        int init = numOptionsSpinner.getValue();
        optionsBox.getChildren().clear();
        if (init >= 1) {
            optionsBox.getChildren().add(optA);
        }
        if (init >= 2) {
            optionsBox.getChildren().add(optB);
        }
        if (init >= 3) {
            optionsBox.getChildren().add(optC);
        }
        if (init >= 4) {
            optionsBox.getChildren().add(optD);
        }

        // Fill existing options
        List<Option> existingOptions = q.getOptions();
        if (existingOptions != null) {
            int i = 0;
            for (Option opt : existingOptions) {
                if (i >= optionsBox.getChildren().size()) {
                    break;
                }
                TextField tf = (TextField) optionsBox.getChildren().get(i);
                tf.setText(opt.getText());
                i++;
            }
        }

        Label correctLabel = new Label("Resposta Correta:");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue(q.getCorrectOption() != null
                ? q.getCorrectOption().name()
                : "A");

        Label periodLabel = new Label("Período de Disponibilidade:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        HBox periodBox = new HBox(8);
        DatePicker startDate = new DatePicker(q.getStartAt().toLocalDate());
        startDate.setPromptText("Data início");
        int qStartHour = q.getStartAt().getHour();
        int qStartMinute = q.getStartAt().getMinute();
        Spinner<Integer> startHour = new Spinner<>(0, 23, qStartHour);
        startHour.setPrefWidth(70);
        Spinner<Integer> startMinute = new Spinner<>(0, 59, qStartMinute);
        startMinute.setPrefWidth(70);
        Label toLabel = new Label(" até ");
        DatePicker endDate = new DatePicker(q.getEndAt().toLocalDate());
        endDate.setPromptText("Data fim");
        int qEndHour = q.getEndAt().getHour();
        int qEndMinute = q.getEndAt().getMinute();
        Spinner<Integer> endHour = new Spinner<>(0, 23, qEndHour);
        endHour.setPrefWidth(70);
        Spinner<Integer> endMinute = new Spinner<>(0, 59, qEndMinute);
        endMinute.setPrefWidth(70);
        periodBox.getChildren().addAll(startDate, startHour, new Label(":"), startMinute,
                toLabel, endDate, endHour, new Label(":"), endMinute);

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
        okButton.setText("Atualizar Pergunta");
        okButton.setOnAction(ev -> {
            String statement = statementField.getText().trim();
            if (statement.isEmpty()) {
                AlertUtils.showError(owner, "Erro", "O enunciado não pode estar vazio.");
                ev.consume();
                return;
            }

            int numOptions = numOptionsSpinner.getValue();
            List<Option> options = new ArrayList<>();
            TextField[] allOptions = {optA, optB, optC, optD};
            OptionLetter[] letters = OptionLetter.values();

            int filledCount = 0;
            for (int i = 0; i < numOptions; i++) {
                if (!allOptions[i].getText().trim().isEmpty()) {
                    filledCount++;
                }
            }
            if (filledCount < 2) {
                AlertUtils.showError(owner, "Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                ev.consume();
                return;
            }

            List<String> texts = new ArrayList<>();
            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    AlertUtils.showError(owner, "Erro", "Preencha todas as respostas até ao número escolhido.");
                    ev.consume();
                    return;
                }
                texts.add(optText);
            }

            Set<String> seen = new HashSet<>();
            for (String t : texts) {
                String normal = t.toLowerCase();
                if (!seen.add(normal)) {
                    AlertUtils.showError(owner, "Erro", "As opções não podem conter respostas duplicadas");
                    ev.consume();
                    return;
                }
            }

            // Create options after validations
            for (int i = 0; i < numOptions; i++) {
                options.add(new Option(letters[i], texts.get(i)));
            }

            LocalDate startD = startDate.getValue();
            LocalDate endD = endDate.getValue();
            if (startD == null || endD == null) {
                AlertUtils.showError(owner, "Erro", "Datas de início e fim são obrigatórias.");
                ev.consume();
                return;
            }

            try {
                LocalTime sTime = LocalTime.of(startHour.getValue(), startMinute.getValue());
                LocalTime eTime = LocalTime.of(endHour.getValue(), endMinute.getValue());
                LocalDateTime startAt = LocalDateTime.of(startD, sTime);
                LocalDateTime endAt = LocalDateTime.of(endD, eTime);

                if (!endAt.isAfter(startAt)) {
                    AlertUtils.showError(owner, "Erro",
                            "A data/hora de fim deve ser posterior à data/hora de início.");
                    ev.consume();
                    return;
                }

                if (teacherId == null) {
                    AlertUtils.showError(owner, "Erro", "Sessão inválida. Faça login novamente.");
                    ev.consume();
                    return;
                }

                EditQuestionDTO dto = new EditQuestionDTO(
                        q.getId(), teacherId, statement, options,
                        OptionLetter.valueOf(correctCombo.getValue()),
                        startAt, endAt
                );
                if (onSubmit != null) {
                    onSubmit.accept(dto);
                }
                // let dialog close
            } catch (Exception e) {
                AlertUtils.showError(owner, "Erro",
                        "Formato de hora inválido (utilize HH:MM).");
                ev.consume();
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  VIEW QUESTION ANSWERS
    // --------------------------------------------------------------

    /**
     * Shows a dialog with question details and its answers.
     * <p>
     * The "Eliminar Pergunta" button triggers the {@code onDelete} callback; the controller is
     * responsible for communicating with the service layer.
     *
     * @param owner    owner window (may be {@code null})
     * @param q        question to display
     * @param answers  list of answers for the question
     * @param onDelete callback invoked when the user confirms the deletion of the question
     */
    public static void showAnswersDialog(Window owner,
                                         Question q,
                                         List<Answer> answers,
                                         Runnable onDelete) {

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        String code = q.getAccessCode();
        dialog.setTitle("Respostas - " + code);
        dialog.setHeaderText("Respostas submetidas pelos estudantes");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(750);

        // ---------- Question info box ----------
        VBox infoBox = new VBox(5);
        infoBox.setStyle(
                "-fx-background-color: #ecf0f1;" +
                        "-fx-padding: 15;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;");

        Label questionLabel = new Label("Pergunta: " + q.getStatement());
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label correctLabel = new Label("Resposta correta: " + q.getCorrectOption().name());
        correctLabel.setFont(Font.font("Arial", 13));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String periodStr = q.getStartAt().format(fmt) + " - " + q.getEndAt().format(fmt);
        Label periodLabel = new Label("Período: " + periodStr);
        periodLabel.setFont(Font.font("Arial", 13));

        infoBox.getChildren().addAll(questionLabel, correctLabel, periodLabel);

        // ---------- Statistics ----------
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(15));
        statsBox.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #bdc3c7;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;");

        int total = (answers == null ? 0 : answers.size());
        long correctCnt = (answers == null ? 0 : answers.stream().filter(Answer::isCorrect).count());
        int wrongCnt = total - (int) correctCnt;
        double correctPerc = total == 0 ? 0 : (100.0 * correctCnt / total);
        double wrongPerc = total == 0 ? 0 : (100.0 * wrongCnt / total);

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

        // ---------- Answers table ----------
        TableView<Answer> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Answer, String> studCol = new TableColumn<>("Nº Aluno");
        studCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(data.getValue().getStudentNumber()))
        );

        TableColumn<Answer, String> nameCol = new TableColumn<>("Nome");
        nameCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getStudentName() == null
                                ? ""
                                : data.getValue().getStudentName())
        );

        TableColumn<Answer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getStudentEmail() == null
                                ? ""
                                : data.getValue().getStudentEmail())
        );

        TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
        answerCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getSelectedOption().name())
        );

        table.getColumns().addAll(studCol, nameCol, emailCol, answerCol);
        if (answers != null) {
            table.getItems().addAll(answers);
        }

        content.getChildren().addAll(infoBox, statsBox, new Label("Respostas:"), table);
        dialog.getDialogPane().setContent(content);

        ButtonType exportButtonType = new ButtonType("Exportar CSV", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Eliminar Pergunta", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, deleteButtonType, ButtonType.CLOSE);

        // Export CSV
        Button exportButton = (Button) dialog.getDialogPane().lookupButton(exportButtonType);
        exportButton.setOnAction(ev -> {
            CsvExportUtils.exportAnswersToCsv(owner, q, answers);
            ev.consume(); // keep dialog open
        });

        // Delete question – delegate actual action to controller via callback
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setOnAction(ev -> {
            boolean confirm = AlertUtils.showConfirmation(
                    owner,
                    "Confirmar Eliminação",
                    "Tem certeza que deseja eliminar a pergunta " + q.getAccessCode() + "?",
                    "Esta ação não pode ser desfeita."
            );
            if (confirm && onDelete != null) {
                onDelete.run();
                dialog.close();
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  PRIVATE HELPER METHODS (validation styling)
    // --------------------------------------------------------------

    /**
     * Highlights a control with the error style.
     *
     * @param c control to highlight
     */
    private static void markError(Control c) {
        if (c != null) {
            String prev = c.getStyle();
            if (prev == null) {
                prev = "";
            }
            if (!prev.contains("-fx-border-color")) {
                c.setStyle(prev + ";" + ERROR_STYLE);
            } else {
                c.setStyle(ERROR_STYLE);
            }
        }
    }

    /**
     * Removes error highlighting from a control.
     *
     * @param c control to clear
     */
    private static void clearError(Control c) {
        if (c != null) {
            String s = c.getStyle();
            if (s == null || s.isEmpty()) {
                return;
            }
            c.setStyle(s.replace(ERROR_STYLE, "").replaceAll("^;|;$", ""));
        }
    }

    /**
     * Removes error highlighting from several controls.
     *
     * @param controls controls to clear
     */
    private static void clearErrors(Control... controls) {
        for (Control c : controls) {
            clearError(c);
        }
    }
}
