package pt.isec.client.ui.util.dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Utility class that provides modal dialogs used by the teacher UI:
 * <ul>
 *     <li>Create question dialog</li>
 *     <li>Edit question dialog</li>
 *     <li>Question answers/details dialog</li>
 * </ul>
 */
public final class TeacherDialogs {
    private TeacherDialogs() {}

    // ==============================================================
    //  CREATE QUESTION DIALOG
    // ==============================================================

    /**
     * Shows a dialog for creating a new question.
     * <p>
     * This dialog is responsible for collecting user input and building the
     * {@link CreateQuestionDTO}. It performs only minimal UI validation
     * (required fields) so that {@link Option} objects can be created without
     * throwing exceptions. All business rules are enforced on the server.
     *
     * @param owner     owner window (may be {@code null})
     * @param teacherId current teacher id (used in the DTO)
     * @param onSubmit  callback invoked with the created {@link CreateQuestionDTO} if the user confirms
     */
    public static void showCreateQuestionDialog(Window owner,
                                                Integer teacherId,
                                                Consumer<CreateQuestionDTO> onSubmit,
                                                Consumer<String> onValidationError) {

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Criar Nova Pergunta");
        dialog.setHeaderText("Preencha os dados da pergunta");

        GridPane grid = buildBaseGrid();

        // --- Statement -------------------------------------------------------
        Label statementLabel = createSectionLabel("Enunciado:");
        TextArea statementField = createStatementArea();

        // --- Options count + fields -----------------------------------------
        Label numOptionsLabel = createSectionLabel("Número de Opções:");
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 4, 4);
        numOptionsSpinner.setPrefWidth(100);

        Label optionsLabel = createSectionLabel("Opções:");
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField();
        optA.setPromptText("Opção A");
        TextField optB = new TextField();
        optB.setPromptText("Opção B");
        TextField optC = new TextField();
        optC.setPromptText("Opção C");
        TextField optD = new TextField();
        optD.setPromptText("Opção D");

        configureOptionsSpinner(numOptionsSpinner, optionsBox, optA, optB, optC, optD);

        // --- Correct option --------------------------------------------------
        Label correctLabel = createSectionLabel("Resposta Correta:");
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue("A");

        // --- Availability period --------------------------------------------
        Label periodLabel = createSectionLabel("Período de Disponibilidade:");
        HBox periodBox = new HBox(8);
        periodBox.setAlignment(Pos.CENTER_LEFT);

        DatePicker startDate = new DatePicker(LocalDate.now());
        startDate.setPromptText("Data início");
        DatePicker endDate = new DatePicker(LocalDate.now());
        endDate.setPromptText("Data fim");

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        TextField startTimeField = new TextField("09:00");
        startTimeField.setPromptText("HH:mm");
        startTimeField.setPrefWidth(70);

        TextField endTimeField = new TextField("10:00");
        endTimeField.setPromptText("HH:mm");
        endTimeField.setPrefWidth(70);

        UnaryOperator<TextFormatter.Change> timeFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.length() > 5) return null;          // "HH:mm"
            if (!newText.matches("[0-9:]*")) return null;   // only digits and ':'
            return change;
        };
        startTimeField.setTextFormatter(new TextFormatter<>(timeFilter));
        endTimeField.setTextFormatter(new TextFormatter<>(timeFilter));

        Label toLabel = new Label(" até ");

        periodBox.getChildren().addAll(
                startDate, startTimeField,
                toLabel,
                endDate, endTimeField
        );

        // --- Layout into grid -----------------------------------------------
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

        ScrollPane scrollPane = createScrollPane(grid);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Criar Pergunta");

        okButton.setOnAction(ev -> {
            // ---------- minimal UI validation (required fields) ----------

            String statement = statementField.getText();
            if (statement == null || statement.trim().isEmpty()) {
                String msg = "Preencha o enunciado.";
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }
            statement = statement.trim();

            Integer numOptionsVal = numOptionsSpinner.getValue();
            int numOptions = normaliseOptionCount(numOptionsVal);

            TextField[] allOptions = {optA, optB, optC, optD};
            if (!validateOptionTexts(allOptions, numOptions, onValidationError)) {
                ev.consume();
                return;
            }

            // ---------- build DTO (no business rules here) -----------------
            List<Option> options;
            try {
                options = buildOptions(allOptions, numOptions);
            } catch (IllegalArgumentException ex) {
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) {
                    msg = "Não foi possível criar as opções de resposta.";
                }
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }

            OptionLetter correct = parseCorrectLetter(correctCombo.getValue());

            // Require both dates and times to be filled before sending to server
            if (startDate.getValue() == null
                    || startTimeField.getText() == null || startTimeField.getText().isBlank()
                    || endDate.getValue() == null
                    || endTimeField.getText() == null || endTimeField.getText().isBlank()) {

                String msg = "Preencha a data e hora de início e fim.";
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }

            LocalDateTime startAt;
            LocalDateTime endAt;
            try {
                startAt = parseDateTime(owner, startDate.getValue(), startTimeField.getText(), timeFormatter,
                        "Hora de início inválida", "Use o formato HH:mm, por exemplo 09:30.", ev, onValidationError);
                if (ev.isConsumed()) return;

                endAt = parseDateTime(owner, endDate.getValue(), endTimeField.getText(), timeFormatter,
                        "Hora de fim inválida", "Use o formato HH:mm, por exemplo 10:15.", ev, onValidationError);
                if (ev.isConsumed()) return;
            } catch (IllegalStateException ignored) {
                // parseDateTime already mostrou a mensagem e consumiu o evento
                return;
            }

            if (onSubmit != null) {
                CreateQuestionDTO dto = new CreateQuestionDTO(
                        statement,
                        teacherId,
                        options,
                        correct,
                        startAt,
                        endAt
                );
                onSubmit.accept(dto);
            }
        });

        dialog.showAndWait();
    }

    // ==============================================================
    //  EDIT QUESTION DIALOG
    // ==============================================================

    /**
     * Shows a dialog for editing an existing question.
     * <p>
     * This dialog gathers the updated data and creates an {@link EditQuestionDTO}.
     * It only enforces required-field checks so that {@link Option} instances
     * are created with valid text; all business logic is delegated to the server.
     *
     * @param owner     owner window (may be {@code null})
     * @param question  question to edit
     * @param teacherId current teacher id (used in the DTO)
     * @param onSubmit  callback invoked with the created {@link EditQuestionDTO} if the user confirms
     */
    public static void showEditQuestionDialog(Window owner,
                                              Question question,
                                              Integer teacherId,
                                              Consumer<EditQuestionDTO> onSubmit,
                                              Consumer<String> onValidationError) {

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        dialog.setTitle("Editar Pergunta - " + question.getAccessCode());
        dialog.setHeaderText("Edite os dados da pergunta");

        GridPane grid = buildBaseGrid();

        // --- Statement -------------------------------------------------------
        Label statementLabel = createSectionLabel("Enunciado:");
        TextArea statementField = createStatementArea();
        statementField.setText(question.getStatement());

        // --- Options count + fields -----------------------------------------
        Label numOptionsLabel = createSectionLabel("Número de Opções:");
        int initialOptionsCount = (question.getOptions() == null
                ? 4
                : Math.max(2, Math.min(4, question.getOptions().size())));
        Spinner<Integer> numOptionsSpinner = new Spinner<>(2, 4, initialOptionsCount);
        numOptionsSpinner.setPrefWidth(100);

        Label optionsLabel = createSectionLabel("Opções:");
        VBox optionsBox = new VBox(10);
        TextField optA = new TextField();
        optA.setPromptText("Opção A");
        TextField optB = new TextField();
        optB.setPromptText("Opção B");
        TextField optC = new TextField();
        optC.setPromptText("Opção C");
        TextField optD = new TextField();
        optD.setPromptText("Opção D");

        configureOptionsSpinner(numOptionsSpinner, optionsBox, optA, optB, optC, optD);

        // Preenche opções existentes
        List<Option> existingOptions = question.getOptions();
        if (existingOptions != null) {
            int i = 0;
            for (Option opt : existingOptions) {
                if (i >= 4) break;
                switch (i) {
                    case 0 -> optA.setText(opt.getText());
                    case 1 -> optB.setText(opt.getText());
                    case 2 -> optC.setText(opt.getText());
                    case 3 -> optD.setText(opt.getText());
                    default -> { }
                }
                i++;
            }
        }

        // --- Correct option --------------------------------------------------
        Label correctLabel = createSectionLabel("Resposta Correta:");
        ComboBox<String> correctCombo = new ComboBox<>();
        correctCombo.getItems().addAll("A", "B", "C", "D");
        correctCombo.setValue(question.getCorrectOption() != null
                ? question.getCorrectOption().name()
                : "A");

        // --- Availability period --------------------------------------------
        Label periodLabel = createSectionLabel("Período de Disponibilidade:");
        HBox periodBox = new HBox(8);
        periodBox.setAlignment(Pos.CENTER_LEFT);

        DatePicker startDate = new DatePicker(question.getStartAt().toLocalDate());
        startDate.setPromptText("Data início");
        DatePicker endDate = new DatePicker(question.getEndAt().toLocalDate());
        endDate.setPromptText("Data fim");

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        TextField startTimeField = new TextField(question.getStartAt().toLocalTime().format(timeFormatter));
        startTimeField.setPromptText("HH:mm");
        startTimeField.setPrefWidth(70);

        TextField endTimeField = new TextField(question.getEndAt().toLocalTime().format(timeFormatter));
        endTimeField.setPromptText("HH:mm");
        endTimeField.setPrefWidth(70);

        UnaryOperator<TextFormatter.Change> timeFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.length() > 5) return null;
            if (!newText.matches("[0-9:]*")) return null;
            return change;
        };
        startTimeField.setTextFormatter(new TextFormatter<>(timeFilter));
        endTimeField.setTextFormatter(new TextFormatter<>(timeFilter));

        Label toLabel = new Label(" até ");

        periodBox.getChildren().addAll(
                startDate, startTimeField,
                toLabel,
                endDate, endTimeField
        );

        // --- Layout into grid -----------------------------------------------
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

        ScrollPane scrollPane = createScrollPane(grid);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Atualizar Pergunta");

        okButton.setOnAction(ev -> {
            String statement = statementField.getText();
            if (statement == null || statement.trim().isEmpty()) {
                String msg = "Preencha o enunciado.";
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }
            statement = statement.trim();

            Integer numOptionsVal = numOptionsSpinner.getValue();
            int numOptions = normaliseOptionCount(numOptionsVal);

            TextField[] allOptions = {optA, optB, optC, optD};
            if (!validateOptionTexts(allOptions, numOptions, onValidationError)) {
                ev.consume();
                return;
            }

            List<Option> options;
            try {
                options = buildOptions(allOptions, numOptions);
            } catch (IllegalArgumentException ex) {
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) {
                    msg = "Não foi possível criar as opções de resposta.";
                }
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }

            OptionLetter correct = parseCorrectLetter(correctCombo.getValue());

            // Obrigatório ter data + hora
            if (startDate.getValue() == null
                    || startTimeField.getText() == null || startTimeField.getText().isBlank()
                    || endDate.getValue() == null
                    || endTimeField.getText() == null || endTimeField.getText().isBlank()) {

                String msg = "Preencha a data e hora de início e fim.";
                notifyValidationError(onValidationError, msg);
                ev.consume();
                return;
            }

            LocalDateTime startAt;
            LocalDateTime endAt;
            try {
                startAt = parseDateTime(owner, startDate.getValue(), startTimeField.getText(), timeFormatter,
                        "Hora de início inválida", "Use o formato HH:mm, por exemplo 09:30.", ev, onValidationError);
                if (ev.isConsumed()) return;

                endAt = parseDateTime(owner, endDate.getValue(), endTimeField.getText(), timeFormatter,
                        "Hora de fim inválida", "Use o formato HH:mm, por exemplo 10:15.", ev, onValidationError);
                if (ev.isConsumed()) return;
            } catch (IllegalStateException ignored) {
                // parseDateTime já tratou da mensagem e consumiu o evento
                return;
            }

            if (onSubmit != null) {
                EditQuestionDTO dto = new EditQuestionDTO(
                        question.getId(),
                        teacherId,
                        statement,
                        options,
                        correct,
                        startAt,
                        endAt
                );
                onSubmit.accept(dto);
            }
        });

        dialog.showAndWait();
    }

    // ==============================================================
    //  VIEW QUESTION + ANSWERS DIALOG
    // ==============================================================

    /**
     * Shows a dialog with question details and its answers.
     * <p>
     * The "Eliminar Pergunta" button triggers the {@code onDelete} callback; the controller is
     * responsible for communicating with the service layer.
     *
     * @param owner    owner window (may be {@code null})
     * @param question question to display
     * @param answers  list of answers for the question
     * @param onDelete callback invoked when the user confirms the deletion of the question
     */
    public static void showAnswersDialog(Window owner,
                                         Question question,
                                         List<Answer> answers,
                                         Runnable onDelete) {

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        String code = question.getAccessCode();
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

        Label questionLabel = new Label("Pergunta: " + question.getStatement());
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label correctLabel = new Label("Resposta correta: " + question.getCorrectOption().name());
        correctLabel.setFont(Font.font("Arial", 13));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String periodStr = question.getStartAt().format(fmt) + " - " + question.getEndAt().format(fmt);
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
            CsvExportUtils.exportAnswersToCsv(owner, question, answers);
            ev.consume(); // keep dialog open
        });

        // Delete question – delegate actual action to controller via callback
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setOnAction(_ -> {
            boolean confirm = AlertUtils.showConfirmation(
                    owner,
                    "Confirmar Eliminação",
                    "Tem certeza que deseja eliminar a pergunta " + question.getAccessCode() + "?",
                    "Esta ação não pode ser desfeita."
            );
            if (confirm && onDelete != null) {
                onDelete.run();
                dialog.close();
            }
        });

        dialog.showAndWait();
    }

    // ==============================================================
    //  PRIVATE HELPER METHODS
    // ==============================================================

    /**
     * Creates a base {@link GridPane} used by the create/edit dialogs.
     */
    private static GridPane buildBaseGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));
        return grid;
    }

    /**
     * Creates a common section label with bold font.
     *
     * @param text label text
     * @return configured label
     */
    private static Label createSectionLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        return label;
    }

    /**
     * Creates the multi-line text area used for question statements.
     *
     * @return configured text area
     */
    private static TextArea createStatementArea() {
        TextArea statementField = new TextArea();
        statementField.setPrefRowCount(3);
        statementField.setPrefWidth(500);
        statementField.setPromptText("Escreva o enunciado da pergunta...");
        return statementField;
    }

    /**
     * Wraps a {@link GridPane} in a {@link ScrollPane}.
     *
     * @param grid base grid
     * @return configured scroll pane
     */
    private static ScrollPane createScrollPane(GridPane grid) {
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);
        return scrollPane;
    }

    /**
     * Normalises the selected number of options to the valid range [2..4].
     *
     * @param value value from spinner (may be {@code null})
     * @return normalised value between 2 and 4
     */
    private static int normaliseOptionCount(Integer value) {
        int numOptions = (value == null ? 0 : value);
        if (numOptions < 2) numOptions = 2;
        if (numOptions > 4) numOptions = 4;
        return numOptions;
    }

    /**
     * Configures the spinner that controls the visible option text fields.
     * Removes duplicated logic between create and edit dialogs.
     *
     * @param spinner    spinner with values from 2 to 4
     * @param optionsBox container where option fields are displayed
     * @param optionFields ordered option fields (A..D)
     */
    private static void configureOptionsSpinner(Spinner<Integer> spinner,
                                                VBox optionsBox,
                                                TextField... optionFields) {

        spinner.valueProperty().addListener((_, _, newVal) -> {
            // reference parameters so IDE does not warn about them being unused
            int n = normaliseOptionCount(newVal);
            updateOptionsBox(optionsBox, n, optionFields);
        });

        // initial state
        int initial = spinner.getValue();
        updateOptionsBox(optionsBox, normaliseOptionCount(initial), optionFields);
    }

    /**
     * Updates the {@link VBox} that holds the visible option fields, based on
     * the selected number of options.
     */
    private static void updateOptionsBox(VBox optionsBox,
                                         int numOptions,
                                         TextField... optionFields) {
        optionsBox.getChildren().clear();
        int limit = Math.max(0, Math.min(numOptions, optionFields.length));
        for (int i = 0; i < limit; i++) {
            optionsBox.getChildren().add(optionFields[i]);
        }
    }

    /**
     * Validates that all visible option fields contain non-blank text.
     *
     * @param optionFields     array with all option fields
     * @param numOptions       number of options selected in the spinner
     * @param onValidationError callback invoked with the error message, if any
     * @return {@code true} if all required fields are filled
     */
    private static boolean validateOptionTexts(TextField[] optionFields,
                                               int numOptions,
                                               Consumer<String> onValidationError) {

        int limit = Math.min(numOptions, optionFields.length);

        for (int i = 0; i < limit; i++) {
            String txt = optionFields[i].getText();
            if (txt == null || txt.trim().isEmpty()) {
                String msg = "Preencha todas as opções até ao número escolhido.";
                notifyValidationError(onValidationError, msg);
                return false;
            }
        }
        return true;
    }

    /**
     * Builds the list of {@link Option} objects from the option fields.
     *
     * @param optionFields array with all option fields
     * @param numOptions   number of options selected
     * @return immutable list of options
     * @throws IllegalArgumentException if option construction fails
     */
    private static List<Option> buildOptions(TextField[] optionFields,
                                             int numOptions) {
        OptionLetter[] letters = OptionLetter.values();
        int limit = Math.min(Math.min(numOptions, optionFields.length), letters.length);
        List<Option> options = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            String txt = optionFields[i].getText().trim();
            options.add(new Option(letters[i], txt));
        }
        return options;
    }

    /**
     * Parses the correct option letter from the combo-box value.
     *
     * @param value combo-box selected value (may be {@code null})
     * @return corresponding {@link OptionLetter}, or {@code null} if invalid
     */
    private static OptionLetter parseCorrectLetter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OptionLetter.valueOf(value);
        } catch (IllegalArgumentException ex) {
            // let the server validate an unexpected value
            return null;
        }
    }

    /**
     * Parses a {@link LocalDateTime} from a date and a textual time, showing
     * an error dialog on failure and optionally notifying the caller.
     *
     * @param owner           owner window
     * @param date            date value (may be {@code null})
     * @param timeText        time text (may be {@code null})
     * @param formatter       formatter used to parse time
     * @param errorTitle      dialog title for invalid time
     * @param errorMessage    dialog message for invalid time
     * @param ev              action event (will be consumed on error)
     * @param onValidationError callback invoked with the error message when parsing fails
     * @return parsed {@link LocalDateTime}, or {@code null} if date/time not provided
     * @throws IllegalStateException if parsing fails (the event is already consumed)
     */
    private static LocalDateTime parseDateTime(Window owner,
                                               LocalDate date,
                                               String timeText,
                                               DateTimeFormatter formatter,
                                               String errorTitle,
                                               String errorMessage,
                                               javafx.event.ActionEvent ev,
                                               Consumer<String> onValidationError) {
        if (date == null || timeText == null || timeText.isBlank()) {
            return null;
        }
        try {
            LocalTime time = LocalTime.parse(timeText, formatter);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException ex) {
            AlertUtils.showError(owner, errorTitle, errorMessage);
            notifyValidationError(onValidationError, errorMessage);
            ev.consume();
            throw new IllegalStateException("Invalid time");
        }
    }
    /**
     * Helper to safely invoke the local validation error callback.
     *
     * @param callback callback provided by the caller (may be {@code null})
     * @param msg      error message (ignored if {@code null} or blank)
     */
    private static void notifyValidationError(Consumer<String> callback, String msg) {
        if (callback != null && msg != null && !msg.isBlank()) {
            callback.accept(msg);
        }
    }

}
