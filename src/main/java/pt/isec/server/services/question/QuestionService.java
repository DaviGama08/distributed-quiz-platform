package pt.isec.server.services.question;

import pt.isec.common.dto.question.*;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service responsible for creating, editing, listing and accessing questions.
 */
public class QuestionService implements IQuestionService {
    private final IQuestionAnswerContext context;
    private final DbCommands dbCommands;

    /**
     * Creates a new {@link QuestionService}.
     *
     * @param context    question/answer context used for replication
     * @param dbCommands database access helper
     */
    public QuestionService(IQuestionAnswerContext context, DbCommands dbCommands) {
        this.context = context;
        this.dbCommands = dbCommands;
    }

    /**
     * Creates a question for a teacher, validates input, inserts it into the database
     * and enqueues SQL for replication.
     *
     * @param dto question creation data
     * @return response containing the question ID and access code
     * @throws Exception if validation or DB operations fail
     */
    @Override
    public CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da pergunta inválidos.");
        }

        String statement      = dto.statement();
        Integer teacherId     = dto.teacherId();
        List<Option> options  = dto.options();
        OptionLetter correct  = dto.correctOption();
        LocalDateTime startAt = dto.startAt();
        LocalDateTime endAt   = dto.endAt();

        // ---------- validações básicas ----------
        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("Identificador de docente inválido.");
        }
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("Preencha o enunciado.");
        }
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("A pergunta deve ter, pelo menos, duas opções de resposta.");
        }
        if (correct == null) {
            throw new IllegalArgumentException("Tem de indicar qual é a opção correta.");
        }

        // ---------- opções: texto obrigatório + sem duplicados ----------
        Set<String> normalizedTexts = new LinkedHashSet<>();
        for (Option o : options) {
            if (o == null || o.getText() == null || o.getText().isBlank()) {
                throw new IllegalArgumentException("Faltou preencher todas as opções da pergunta.");
            }
            String normalized = o.getText().trim().toLowerCase(Locale.ROOT);
            if (!normalizedTexts.add(normalized)) {
                throw new IllegalArgumentException("As opções de resposta não podem ser iguais.");
            }
        }

        // garantir que a letra correta existe nas opções
        boolean correctExists = options.stream()
                .anyMatch(o -> o != null && correct.equals(o.getLetter()));
        if (!correctExists) {
            throw new IllegalArgumentException(
                    "A opção correta tem de corresponder a uma das opções disponíveis.");
        }

        // ---------- datas/horas ----------
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("Data/hora de início e fim são obrigatórias.");
        }

        LocalDateTime now   = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = startAt.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime end   = endAt.truncatedTo(ChronoUnit.MINUTES);

        if (start.isBefore(now)) {
            throw new IllegalArgumentException("A pergunta não pode começar no passado.");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim deve ser posterior à data/hora de início.");
        }

        if (!end.isAfter(now)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim deve ser posterior à data/hora atual.");
        }

        // ---------- pergunta duplicada ----------
        Map<String,Object> existing = dbCommands.selectOne(
                "SELECT id FROM question " +
                        "WHERE teacher_id = ? AND LOWER(TRIM(statement)) = LOWER(TRIM(?)) " +
                        "LIMIT 1",
                teacherId, statement
        );
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Já existe uma pergunta com o mesmo enunciado para este docente.");
        }

        // ---------- inserção + replicação ----------
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

        long qId = qIdArr[0];

        List<String> aux = new ArrayList<>();
        aux.add("INSERT INTO question (id, statement, teacher_id, correct_option, start_at, end_at, access_code) " +
                "VALUES (" + qId + ", '" + escape(statement) + "', " + teacherId + ", '" +
                correct.name() + "', '" + startAt + "', '" + endAt + "', '" + accessCode + "');");
        for (Option o : options) {
            aux.add("INSERT INTO option (question_id, letter, text) VALUES (" +
                    qId + ", '" + o.getLetter().name() + "', '" + escape(o.getText()) + "');");
        }
        context.queue().add(aux);

        return new CreateQuestionResponseDTO((int) qId, accessCode);
    }


    /**
     * Lists questions for a teacher with an optional filter (active, future, expired or null).
     *
     * @param dto list parameters (teacher and filter)
     * @return list of questions
     * @throws Exception if DB access fails
     */
    @Override
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

        List<Map<String, Object>> rows = new ArrayList<>();
        try (var con = DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
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
        for (Map<String, Object> r : rows) {
            int qId = ((Number) r.get("id")).intValue();
            List<Option> opts = loadOptions(qId);
            OptionLetter corr = OptionLetter.valueOf((String) r.get("correct_option"));
            LocalDateTime startAt = LocalDateTime.parse((String) r.get("start_at"));
            LocalDateTime endAt = LocalDateTime.parse((String) r.get("end_at"));
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

    /**
     * Retrieves a question by access code for a student joining it.
     *
     * @param dto join parameters containing the access code
     * @return the question, or {@code null} if not found
     * @throws Exception if DB access fails
     */
    @Override
    public Question joinQuestion(JoinQuestionDTO dto) throws Exception {
        String access = dto.accessCode();
        Map<String, Object> r = dbCommands.selectOne(
                "SELECT id, statement, teacher_id, correct_option, start_at, end_at, access_code " +
                        "FROM question WHERE access_code = ? LIMIT 1",
                access
        );
        if (r == null) {
            return null;
        }

        int qId = ((Number) r.get("id")).intValue();
        List<Option> opts = loadOptions(qId);
        OptionLetter corr = OptionLetter.valueOf((String) r.get("correct_option"));
        LocalDateTime startAt = LocalDateTime.parse((String) r.get("start_at"));
        LocalDateTime endAt = LocalDateTime.parse((String) r.get("end_at"));
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

    // Class: pt.isec.server.services.question.QuestionService

    /**
     * Edits an existing question (when there are no answers registered),
     * validates the new data, checks for duplicates and enqueues SQL for replication.
     * <p>
     * The same business rules as in {@link #createQuestion(CreateQuestionDTO)} are enforced:
     * <ul>
     *     <li>Owner teacher must be valid</li>
     *     <li>Statement mandatory</li>
     *     <li>At least two options</li>
     *     <li>No duplicated option letters or texts</li>
     *     <li>Correct option must exist in the list</li>
     *     <li>Valid time window (end after start, not in the past)</li>
     *     <li>No other question for the same teacher with the same statement
     *         and start/end window (excluding the current one)</li>
     * </ul>
     *
     * @param dto edit parameters
     * @return {@code true} if the question was updated
     * @throws Exception if DB access fails or answers already exist
     */
    @Override
    public boolean editQuestion(EditQuestionDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da pergunta inválidos.");
        }

        Integer quizId    = dto.questionId();
        Integer teacherId = dto.teacherId();
        String statement  = dto.statement();
        List<Option> options = dto.options();
        OptionLetter correct = dto.correctOption();
        LocalDateTime startAt = dto.startAt();
        LocalDateTime endAt   = dto.endAt();

        if (quizId == null || quizId <= 0) {
            throw new IllegalArgumentException("ID da pergunta inválido.");
        }
        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("ID do docente inválido.");
        }

        // Carregar pergunta original para confirmar ownership e obter as datas
        Map<String, Object> qRow = dbCommands.selectOne(
                "SELECT id, start_at, end_at FROM question WHERE id = ? AND teacher_id = ?",
                quizId,
                teacherId
        );
        if (qRow == null) {
            throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente.");
        }

        LocalDateTime originalStart =
                LocalDateTime.parse((String) qRow.get("start_at"));
        LocalDateTime originalEnd =
                LocalDateTime.parse((String) qRow.get("end_at"));

        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("Preencha o enunciado.");
        }
        String normalizedStatement = statement.trim();

        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("A pergunta deve ter, pelo menos, duas opções.");
        }
        if (correct == null) {
            throw new IllegalArgumentException("Opção correta obrigatória.");
        }

        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("Data/hora de início e fim são obrigatórias.");
        }

        // Sempre: fim depois do início
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim tem de ser posterior à data/hora de início.");
        }

        // Só aplicar regras em relação ao "agora" se o período for ALTERADO
        boolean timeWindowChanged =
                !startAt.equals(originalStart) || !endAt.equals(originalEnd);

        if (timeWindowChanged) {
            LocalDateTime now = LocalDateTime.now();

            if (startAt.isBefore(now)) {
                throw new IllegalArgumentException("A pergunta não pode começar no passado.");
            }

            if (!endAt.isAfter(now)) {
                throw new IllegalArgumentException(
                        "A data/hora de fim tem de ser posterior à data/hora atual.");
            }
        }

        // Não pode editar se já existirem respostas
        Map<String, Object> ans = dbCommands.selectOne(
                "SELECT 1 as one FROM answer WHERE question_id = ? LIMIT 1",
                quizId
        );
        if (ans != null) {
            throw new IllegalStateException("Não é possível editar pergunta com respostas registadas.");
        }

        /* --------- Validar opções --------- */

        List<Option> cleanedOptions = new ArrayList<>();
        java.util.Set<OptionLetter> usedLetters = new java.util.HashSet<>();
        java.util.Set<String> usedTexts = new java.util.HashSet<>();
        boolean correctOptionExists = false;

        for (Option opt : options) {
            if (opt == null) {
                throw new IllegalArgumentException("Opção inválida.");
            }

            OptionLetter letter = opt.getLetter();
            if (letter == null) {
                throw new IllegalArgumentException("Letra da opção não pode ser nula.");
            }

            String text = opt.getText();
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Todas as opções devem ter texto.");
            }

            String trimmedText = text.trim();
            String textKey = trimmedText.toLowerCase();

            if (!usedLetters.add(letter)) {
                throw new IllegalArgumentException("Não podem existir opções com a mesma letra.");
            }

            if (!usedTexts.add(textKey)) {
                throw new IllegalArgumentException("Não podem existir opções com o mesmo texto.");
            }

            if (letter == correct) {
                correctOptionExists = true;
            }

            cleanedOptions.add(new Option(letter, trimmedText));
        }

        if (cleanedOptions.size() < 2) {
            throw new IllegalArgumentException("A pergunta deve ter, pelo menos, duas opções.");
        }

        if (!correctOptionExists) {
            throw new IllegalArgumentException("A opção correta escolhida não existe na lista de opções.");
        }

        /* --------- Verificar duplicado (outro registo) --------- */

        Map<String, Object> duplicate = dbCommands.selectOne(
                "SELECT id FROM question " +
                        "WHERE teacher_id = ? " +
                        "  AND id <> ? " +
                        "  AND lower(trim(statement)) = lower(trim(?)) " +
                        "  AND start_at = ? " +
                        "  AND end_at   = ? " +
                        "LIMIT 1",
                teacherId,
                quizId,
                normalizedStatement,
                startAt.toString(),
                endAt.toString()
        );
        if (duplicate != null) {
            throw new IllegalArgumentException(
                    "Já existe outra pergunta idêntica (mesmo enunciado e período) para este docente."
            );
        }

        /* --------- Atualização em transação + replicação --------- */

        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate(
                    "UPDATE question SET statement = ?, correct_option = ?, start_at = ?, end_at = ? " +
                            "WHERE id = ? AND teacher_id = ?",
                    normalizedStatement,
                    correct.name(),
                    startAt.toString(),
                    endAt.toString(),
                    quizId,
                    teacherId
            );
            tx.executeUpdate("DELETE FROM option WHERE question_id = ?", quizId);
            for (Option o : cleanedOptions) {
                tx.executeUpdate(
                        "INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)",
                        quizId,
                        o.getLetter().name(),
                        o.getText()
                );
            }
        });

        List<String> aux = new ArrayList<>();

        aux.add(
                "UPDATE question SET statement='" + escape(normalizedStatement) +
                        "', correct_option='" + correct.name() +
                        "', start_at='" + startAt +
                        "', end_at='" + endAt +
                        "' WHERE id=" + quizId +
                        " AND teacher_id=" + teacherId + ";"
        );

        aux.add("DELETE FROM option WHERE question_id=" + quizId + ";");

        for (Option o : cleanedOptions) {
            aux.add(
                    "INSERT INTO option (question_id, letter, text) VALUES (" +
                            quizId + ", '" + o.getLetter().name() + "', '" + escape(o.getText()) + "');"
            );
        }

        context.queue().add(aux);
        return true;
    }

    /**
     * Deletes a question if there are no answers registered.
     * Also enqueues SQL for replication.
     *
     * @param dto delete parameters
     * @return {@code true} if the question was deleted
     * @throws Exception if DB access fails or answers already exist
     */
    @Override
    public boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception {
        Integer qId = dto.questionId();
        Integer teacherId = dto.teacherId();

        Map<String, Object> ans = dbCommands.selectOne(
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

        List<String> aux = new ArrayList<>();

        aux.add("DELETE FROM option WHERE question_id=" + qId + ";");
        aux.add("DELETE FROM question WHERE id=" + qId + " AND teacher_id=" + teacherId + ";");

        context.queue().add(aux);

        return true;
    }

    /**
     * Loads the options of a question.
     *
     * @param questionId question ID
     * @return list of options
     * @throws Exception if DB access fails
     */
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

    /**
     * Escapes single quotes for safe SQL string literal construction.
     *
     * @param s input string
     * @return escaped string (or empty string if {@code s} is null)
     */
    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /**
     * Retrieves the teacher ID associated with a given question.
     * <p>
     * Executes a lookup in the database for the question with the specified ID.
     * If no matching record is found, this method returns {@code null}.
     *
     * @param questionId the ID of the question to look up
     * @return the teacher ID, or {@code null} if the question does not exist
     * @throws Exception if a database error occurs
     */
    @Override
    public Integer findTeacherIdByQuestionId(int questionId) throws Exception {
        Map<String, Object> row = dbCommands.selectOne(
                "SELECT teacher_id FROM question WHERE id = ? LIMIT 1",
                questionId
        );
        if (row == null) {
            return null;
        }
        return ((Number) row.get("teacher_id")).intValue();
    }

}
