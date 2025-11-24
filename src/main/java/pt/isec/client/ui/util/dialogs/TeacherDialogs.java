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
import java.util.List;
import java.util.function.Consumer;

public final class TeacherDialogs {

    private TeacherDialogs() { }

    // --------------------------------------------------------------
    //  CRIAR PERGUNTA
    // --------------------------------------------------------------
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
        HBox periodBox = new HBox(8);
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("Data início");
        Spinner<Integer> startHour = new Spinner<>(0, 23, 9);
        startHour.setPrefWidth(70);
        Spinner<Integer> startMinute = new Spinner<>(0, 59, 0);
        startMinute.setPrefWidth(70);
        Label toLabel = new Label(" até ");
        DatePicker endDate = new DatePicker();
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
                if (!allOptions[i].getText().trim().isEmpty()) filledCount++;
            }
            if (filledCount < 2) {
                AlertUtils.showError(owner, "Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                ev.consume();
                return;
            }

            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    AlertUtils.showError(owner, "Erro", "Preencha todas as respostas até ao número escolhido.");
                    ev.consume();
                    return;
                }
                options.add(new Option(letters[i], optText));
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

                CreateQuestionDTO dto = new CreateQuestionDTO(
                        statement, teacherId, options,
                        OptionLetter.valueOf(correctCombo.getValue()),
                        startAt, endAt
                );
                if (onSubmit != null) {
                    onSubmit.accept(dto);
                }
                // deixa o diálogo fechar
            } catch (Exception e) {
                AlertUtils.showError(owner, "Erro",
                        "Formato de hora inválido (utilize HH:MM).");
                ev.consume();
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  EDITAR PERGUNTA
    // --------------------------------------------------------------
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

        int init = numOptionsSpinner.getValue();
        optionsBox.getChildren().clear();
        if (init >= 1) optionsBox.getChildren().add(optA);
        if (init >= 2) optionsBox.getChildren().add(optB);
        if (init >= 3) optionsBox.getChildren().add(optC);
        if (init >= 4) optionsBox.getChildren().add(optD);

        // Preenche opções existentes
        List<Option> existingOptions = q.getOptions();
        if (existingOptions != null) {
            int i = 0;
            for (Option opt : existingOptions) {
                if (i >= optionsBox.getChildren().size()) break;
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
                if (!allOptions[i].getText().trim().isEmpty()) filledCount++;
            }
            if (filledCount < 2) {
                AlertUtils.showError(owner, "Erro", "A pergunta deve ter pelo menos duas respostas possíveis.");
                ev.consume();
                return;
            }

            for (int i = 0; i < numOptions; i++) {
                String optText = allOptions[i].getText().trim();
                if (optText.isEmpty()) {
                    AlertUtils.showError(owner, "Erro", "Preencha todas as respostas até ao número escolhido.");
                    ev.consume();
                    return;
                }
                options.add(new Option(letters[i], optText));
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
                // deixa o diálogo fechar
            } catch (Exception e) {
                AlertUtils.showError(owner, "Erro",
                        "Formato de hora inválido (utilize HH:MM).");
                ev.consume();
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  VER RESPOSTAS DE UMA PERGUNTA (DETALHE)
    // --------------------------------------------------------------
    /**
     * Mostra o diálogo com detalhes da pergunta + lista de respostas.
     * O botão "Eliminar Pergunta" chama o callback onDelete (o controller
     * é que fala com o serviço).
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

        // ---------- Caixa de informação da pergunta ----------
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

        // ---------- Estatísticas ----------
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

        // ---------- Tabela de respostas ----------
        TableView<Answer> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Answer, String> studCol = new TableColumn<>("Nº Aluno");
        studCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(data.getValue().getStudentId()))
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

        // Exportar CSV
        Button exportButton = (Button) dialog.getDialogPane().lookupButton(exportButtonType);
        exportButton.setOnAction(ev -> {
            CsvExportUtils.exportAnswersToCsv(owner, q, answers);
            ev.consume(); // mantemos o diálogo aberto
        });

        // Eliminar Pergunta – deixa a responsabilidade no controller
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
}
