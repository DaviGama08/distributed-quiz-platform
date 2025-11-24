package pt.isec.server.services.question;

import pt.isec.common.dto.question.*;
import pt.isec.server.IServerManager;
import pt.isec.server.db.DbCommands;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Serviço que trata da criação, edição, listagem e acesso de perguntas.
 */
public class QuestionService {
    private final IServerManager server;
    private final DbCommands dbCommands;

    public QuestionService(IServerManager server, DbCommands dbCommands) {
        this.server = server;
        this.dbCommands = dbCommands;
    }

    /** Cria uma pergunta para um docente. */
    public CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) throws Exception {
        if (dto == null)
            throw new IllegalArgumentException("Dados inválidos");

        String statement     = dto.statement();
        Integer teacherId    = dto.teacherId();
        List<Option> options = dto.options();
        OptionLetter correct = dto.correctOption();
        LocalDateTime startAt = dto.startAt();
        LocalDateTime endAt   = dto.endAt();

        if (statement == null || statement.isBlank())
            throw new IllegalArgumentException("Enunciado obrigatório");
        if (teacherId == null || teacherId <= 0)
            throw new IllegalArgumentException("ID do docente inválido");
        if (options == null || options.size() < 2)
            throw new IllegalArgumentException("Mínimo de duas opções necessário");
        if (correct == null)
            throw new IllegalArgumentException("Opção correcta obrigatória");
        if (options.stream().noneMatch(o -> o.getLetter() == correct))
            throw new IllegalArgumentException("Opção correcta deve constar das opções");
        if (startAt == null || endAt == null || !endAt.isAfter(startAt))
            throw new IllegalArgumentException("Período inválido");

        String accessCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        final long[] qIdArr = new long[1];
        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate(
                    "INSERT INTO question (statement, teacher_id, correct_option, start_at, end_at, access_code) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    statement, teacherId, correct.name(), startAt.toString(), endAt.toString(), accessCode
            );
            qIdArr[0] = tx.getLastInsertId();
            for (Option o : options) {
                tx.executeUpdate(
                        "INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)",
                        qIdArr[0], o.getLetter().name(), o.getText()
                );
            }
        });

        // replicação incremental
        long qId = qIdArr[0];
        server.recordSqlUpdate(
                "INSERT INTO question (id, statement, teacher_id, correct_option, start_at, end_at, access_code) VALUES (" +
                        qId + ", '" + escape(statement) + "', " + teacherId + ", '" + correct.name() + "', '" +
                        startAt + "', '" + endAt + "', '" + accessCode + "');"
        );
        for (Option o : options) {
            server.recordSqlUpdate(
                    "INSERT INTO option (question_id, letter, text) VALUES (" +
                            qId + ", '" + o.getLetter().name() + "', '" + escape(o.getText()) + "');"
            );
        }
        server.setDbVersion(server.dbVersion() + 1);

        return new CreateQuestionResponseDTO((int) qId, accessCode);
    }

    /** Lista perguntas por docente, com filtro opcional (active, future, expired ou null). */
    public List<Question> listQuestions(ListQuestionsDTO dto) throws Exception {
        Integer teacherId = dto.teacherId();
        String filter = dto.filter();

        StringBuilder sql = new StringBuilder(
                "SELECT id, statement, teacher_id, correct_option, start_at, end_at, access_code " +
                        "FROM question WHERE teacher_id = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(teacherId);

        LocalDateTime now = LocalDateTime.now();
        if ("active".equalsIgnoreCase(filter)) {
            sql.append(" AND start_at <= ? AND end_at >= ?");
            params.add(now.toString());
            params.add(now.toString());
        } else if ("future".equalsIgnoreCase(filter)) {
            sql.append(" AND start_at > ?");
            params.add(now.toString());
        } else if ("expired".equalsIgnoreCase(filter)) {
            sql.append(" AND end_at < ?");
            params.add(now.toString());
        }

        List<Map<String,Object>> rows = new ArrayList<>();
        try (var con = DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("statement", rs.getString("statement"));
                    m.put("teacher_id", rs.getInt("teacher_id"));
                    m.put("correct_option", rs.getString("correct_option"));
                    m.put("start_at", rs.getString("start_at"));
                    m.put("end_at", rs.getString("end_at"));
                    m.put("access_code", rs.getString("access_code"));
                    rows.add(m);
                }
            }
        }

        List<Question> out = new ArrayList<>();
        for (Map<String,Object> r : rows) {
            int qId = ((Number) r.get("id")).intValue();
            List<Option> opts = loadOptions(qId);
            OptionLetter corr = OptionLetter.valueOf((String) r.get("correct_option"));
            LocalDateTime startAt = LocalDateTime.parse((String) r.get("start_at"));
            LocalDateTime endAt   = LocalDateTime.parse((String) r.get("end_at"));
            out.add(new Question(
                    qId,
                    (String) r.get("statement"),
                    ((Number) r.get("teacher_id")).intValue(),
                    opts,
                    startAt,
                    endAt,
                    corr,
                    (String) r.get("access_code")
            ));
        }
        return out;
    }

    /** Carrega opções de uma pergunta. */
    private List<Option> loadOptions(int questionId) throws Exception {
        List<Option> opts = new ArrayList<>();
        try (var con = java.sql.DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT letter, text FROM option WHERE question_id = ? ORDER BY letter")) {
            ps.setInt(1, questionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    OptionLetter letter = OptionLetter.valueOf(rs.getString("letter"));
                    String text = rs.getString("text");
                    opts.add(new Option(letter, text));
                }
            }
        }
        return opts;
    }

    /** Acessa pergunta por código. */
    public Question joinQuestion(JoinQuestionDTO dto) throws Exception {
        String access = dto.accessCode();
        Map<String,Object> r = dbCommands.selectOne(
                "SELECT id, statement, teacher_id, correct_option, start_at, end_at, access_code " +
                        "FROM question WHERE access_code = ? LIMIT 1",
                access
        );
        if (r == null) return null;

        int qId = ((Number) r.get("id")).intValue();
        List<Option> opts = loadOptions(qId);
        OptionLetter corr = OptionLetter.valueOf((String) r.get("correct_option"));
        LocalDateTime startAt = LocalDateTime.parse((String) r.get("start_at"));
        LocalDateTime endAt   = LocalDateTime.parse((String) r.get("end_at"));
        return new Question(
                qId,
                (String) r.get("statement"),
                ((Number) r.get("teacher_id")).intValue(),
                opts,
                startAt,
                endAt,
                corr,
                (String) r.get("access_code")
        );
    }

    /** Edita pergunta se não existirem respostas. */
    public boolean editQuestion(EditQuestionDTO dto) throws Exception {
        Integer quizId = dto.questionId();
        Integer teacherId = dto.teacherId();
        String statement  = dto.statement();
        List<Option> options = dto.options();
        OptionLetter correct = dto.correctOption();
        LocalDateTime startAt = dto.startAt();
        LocalDateTime endAt   = dto.endAt();

        Map<String,Object> ans = dbCommands.selectOne(
                "SELECT 1 as one FROM answer WHERE question_id = ? LIMIT 1",
                quizId
        );
        if (ans != null) {
            throw new IllegalStateException("Não é possível editar pergunta com respostas registadas");
        }

        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate(
                    "UPDATE question SET statement = ?, correct_option = ?, start_at = ?, end_at = ? " +
                            "WHERE id = ? AND teacher_id = ?",
                    statement, correct.name(), startAt.toString(), endAt.toString(), quizId, teacherId
            );
            tx.executeUpdate("DELETE FROM option WHERE question_id = ?", quizId);
            for (Option o : options) {
                tx.executeUpdate(
                        "INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)",
                        quizId, o.getLetter().name(), o.getText()
                );
            }
        });

        server.recordSqlUpdate(
                "UPDATE question SET statement='" + escape(statement) + "', correct_option='" + correct.name() +
                        "', start_at='" + startAt + "', end_at='" + endAt + "' WHERE id=" + quizId + " AND teacher_id=" + teacherId + ";"
        );
        server.recordSqlUpdate("DELETE FROM option WHERE question_id=" + quizId + ";");
        for (Option o : options) {
            server.recordSqlUpdate(
                    "INSERT INTO option (question_id, letter, text) VALUES (" +
                            quizId + ", '" + o.getLetter().name() + "', '" + escape(o.getText()) + "');"
            );
        }
        server.setDbVersion(server.dbVersion() + 1);
        return true;
    }

    /** Elimina pergunta se não tiver respostas. */
    public boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception {
        Integer qId = dto.questionId();
        Integer teacherId = dto.teacherId();

        Map<String,Object> ans = dbCommands.selectOne(
                "SELECT 1 as one FROM answer WHERE question_id = ? LIMIT 1",
                qId
        );
        if (ans != null) {
            throw new IllegalStateException("Não é possível eliminar pergunta com respostas registadas");
        }

        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate("DELETE FROM option WHERE question_id = ?", qId);
            tx.executeUpdate("DELETE FROM question WHERE id = ? AND teacher_id = ?", qId, teacherId);
        });

        server.recordSqlUpdate("DELETE FROM option WHERE question_id=" + qId + ";");
        server.recordSqlUpdate("DELETE FROM question WHERE id=" + qId + " AND teacher_id=" + teacherId + ";");
        server.setDbVersion(server.dbVersion() + 1);
        return true;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
