package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.server.IServerManager;
import pt.isec.server.db.DbCommands;
import pt.isec.server.model.question.Answer;
import pt.isec.server.model.question.OptionLetter;

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

    /** Regista uma resposta e actualiza a replicação. */
    public boolean submitAnswer(SubmitAnswerDTO dto) throws Exception {
        Integer questionId = dto.questionId();
        Integer studentId  = dto.studentId();
        OptionLetter selected = dto.selectedOption();
        LocalDateTime now = LocalDateTime.now();

        // valida pergunta
        Map<String,Object> q = dbCommands.selectOne(
                "SELECT correct_option, start_at, end_at FROM question WHERE id = ?",
                questionId
        );
        if (q == null) throw new IllegalArgumentException("Pergunta inexistente");
        LocalDateTime startAt = LocalDateTime.parse((String) q.get("start_at"));
        LocalDateTime endAt   = LocalDateTime.parse((String) q.get("end_at"));
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

    /** Devolve respostas de uma pergunta (docente). Calcula isCorrect em memória. */
    public List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception {
        Integer questionId = dto.questionId();
        Integer teacherId  = dto.teacherId();

        // verifica docência
        Map<String,Object> rec = dbCommands.selectOne(
                "SELECT correct_option FROM question WHERE id = ? AND teacher_id = ?",
                questionId, teacherId
        );
        if (rec == null) throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente");
        OptionLetter correct = OptionLetter.valueOf((String) rec.get("correct_option"));

        List<Answer> out = new ArrayList<>();
        try (var con = java.sql.DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT student_number, chosen_option, created_at FROM answer WHERE question_id = ? ORDER BY created_at")) {
            ps.setInt(1, questionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer stuId = rs.getInt("student_number");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at  = LocalDateTime.parse(rs.getString("created_at"));
                    boolean isCorrect = sel.equals(correct);
                    out.add(new Answer(null, stuId, questionId, sel, at, isCorrect));
                }
            }
        }
        return out;
    }

    /** Histórico de respostas de um estudante. Calcula isCorrect pelo correcto_option da pergunta. */
    public List<Answer> getStudentHistory(Integer studentId) throws Exception {
        List<Answer> out = new ArrayList<>();
        try (var con = java.sql.DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT question_id, chosen_option, created_at FROM answer WHERE student_number = ? ORDER BY created_at DESC")) {
            ps.setInt(1, studentId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer qId = rs.getInt("question_id");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at = LocalDateTime.parse(rs.getString("created_at"));
                    // verificar se a resposta é correcta
                    Map<String,Object> rec = dbCommands.selectOne(
                            "SELECT correct_option FROM question WHERE id = ?", qId
                    );
                    boolean isCorrect = false;
                    if (rec != null) {
                        OptionLetter corr = OptionLetter.valueOf((String) rec.get("correct_option"));
                        isCorrect = sel.equals(corr);
                    }
                    out.add(new Answer(null, studentId, qId, sel, at, isCorrect));
                }
            }
        }
        return out;
    }
}
