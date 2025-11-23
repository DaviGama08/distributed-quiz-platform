package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.server.IServerManager;
import pt.isec.server.db.DbCommands;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.OptionLetter;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serviço para submissão e consulta de respostas.
 * Usa colunas correctas da tabela answer (student_number, question_id, chosen_option, created_at).
 */
public class AnswerService {
    private final IServerManager server;
    private final DbCommands dbCommands;

    public AnswerService(IServerManager server, DbCommands dbCommands) {
        this.server = server;
        this.dbCommands = dbCommands;
    }

    /**
     * Regista uma resposta e actualiza a replicação.
     */
    public boolean submitAnswer(SubmitAnswerDTO dto) throws Exception {
        Integer questionId = dto.questionId();
        Integer studentId = dto.studentId();
        OptionLetter selected = dto.selectedOption();
        LocalDateTime now = LocalDateTime.now();

        // valida pergunta
        Map<String, Object> q = dbCommands.selectOne(
                "SELECT correct_option, start_at, end_at FROM question WHERE id = ?",
                questionId
        );
        if (q == null) throw new IllegalArgumentException("Pergunta inexistente");
        LocalDateTime startAt = LocalDateTime.parse((String) q.get("start_at"));
        LocalDateTime endAt = LocalDateTime.parse((String) q.get("end_at"));
        if (now.isBefore(startAt) || now.isAfter(endAt)) {
            throw new IllegalStateException("Pergunta fora do período de disponibilidade");
        }

        // insere a resposta
        dbCommands.executeUpdate(
                "INSERT INTO answer (student_number, question_id, chosen_option, created_at) VALUES (?, ?, ?, ?)",
                studentId, questionId, selected.name(), now.toString()
        );

        // replicação
        server.recordSqlUpdate(
                "INSERT INTO answer (student_number, question_id, chosen_option, created_at) VALUES (" +
                        studentId + ", " + questionId + ", '" + selected.name() + "', '" + now + "');"
        );
        server.setDbVersion(server.dbVersion() + 1);
        return true;
    }

    /**
     * Devolve respostas de uma pergunta (docente). Calcula isCorrect em memória.
     */
    public List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception {
        Integer questionId = dto.questionId();
        Integer teacherId = dto.teacherId();

        // verifica docência
        Map<String, Object> rec = dbCommands.selectOne(
                "SELECT correct_option FROM question WHERE id = ? AND teacher_id = ?",
                questionId, teacherId
        );
        if (rec == null) throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente");
        OptionLetter correct = OptionLetter.valueOf((String) rec.get("correct_option"));

        List<Answer> out = new ArrayList<>();
        try (var con = java.sql.DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT a.student_number, a.chosen_option, a.created_at, " +
                             "s.name AS student_name, s.email AS student_email " +
                             "FROM answer a " +
                             "JOIN student s ON s.student_number = a.student_number " +
                             "WHERE a.question_id = ? " +
                             "ORDER BY a.created_at")) {
            ps.setInt(1, questionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer stuId = rs.getInt("student_number");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at = LocalDateTime.parse(rs.getString("created_at"));
                    boolean isCorrect = sel.equals(correct);

                    String studentName = rs.getString("student_name");
                    String studentEmail = rs.getString("student_email");

                    out.add(new Answer(
                            null,
                            stuId,
                            questionId,
                            sel,
                            at,
                            isCorrect,
                            studentName,
                            studentEmail,
                            null
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Histórico de respostas de um estudante. Calcula isCorrect pelo correcto_option da pergunta.
     */
    public List<Answer> getStudentHistory(Integer studentId) throws Exception {
        List<Answer> out = new ArrayList<>();
        try (var con = DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT a.question_id, a.chosen_option, a.created_at, " +
                             "q.correct_option, q.statement " +
                             "FROM answer a " +
                             "JOIN question q ON q.id = a.question_id " +
                             "WHERE a.student_number = ? " +
                             "ORDER BY a.created_at DESC")) {
            ps.setInt(1, studentId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer qId = rs.getInt("question_id");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at = LocalDateTime.parse(rs.getString("created_at"));

                    String stmt = rs.getString("statement");
                    String corrStr = rs.getString("correct_option");
                    boolean isCorrect = false;
                    if (corrStr != null) {
                        OptionLetter corr = OptionLetter.valueOf(corrStr);
                        isCorrect = sel.equals(corr);
                    }

                    out.add(new Answer(
                            null,
                            studentId,
                            qId,
                            sel,
                            at,
                            isCorrect,
                            null,              // studentName
                            null,              // studentEmail
                            stmt               // questionStatement
                    ));
                }
            }
            return out;
        }
    }
}

