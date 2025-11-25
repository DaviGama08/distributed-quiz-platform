package pt.isec.client.ui.util;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.Question;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CsvExportUtils {

    private CsvExportUtils() { }

    /**
     * Exporta as respostas de uma pergunta para CSV no formato definido no enunciado.
     */
    public static void exportAnswersToCsv(Window ownerWindow,
                                          Question q,
                                          List<Answer> answers) {

        if (q == null) {
            AlertUtils.showError(ownerWindow, "Exportar CSV", "Pergunta inválida.");
            return;
        }

        if (answers == null || answers.isEmpty()) {
            AlertUtils.showError(ownerWindow, "Exportar CSV", "Ainda não existem respostas para esta pergunta.");
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

        var file = fileChooser.showSaveDialog(ownerWindow);
        if (file == null) {
            // utilizador cancelou
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        try (OutputStream fos = Files.newOutputStream(file.toPath())) {
            // BOM UTF-8 para Excel em Windows
            fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (BufferedWriter writer =
                         new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

                // 1ª linha: cabeçalho da pergunta
                writer.write("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");
                writer.newLine();

                // 2ª linha: dados da pergunta
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

                // Bloco das opções
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

                // Bloco das respostas
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
            }
        } catch (IOException e) {
            System.out.println("CSV não exportado: " + e.getMessage());
            Platform.runLater(() ->
                AlertUtils.showError(ownerWindow, "Exportar CSV", "Erro ao guardar o ficheiro!")
            );
            return;
        }

        System.out.println("CSV exportado!");
        Platform.runLater(() ->
            AlertUtils.showInfo(ownerWindow, "Exportar CSV", "Ficheiro CSV gravado com sucesso")
        );
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
