package pt.isec.server.services.question;

import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.EditQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.ListQuestionsDTO;
import pt.isec.common.dto.question.StudentQuestionDTO;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for creating, editing, listing and accessing questions.
 * <p>
 * This service encapsulates all business rules for question management.
 */
@SuppressWarnings("ClassCanBeRecord")
public class QuestionService implements IQuestionService {

    private static final int MAX_ACCESS_CODE_ATTEMPTS = 8;

    /** Helper that provides higher-level database commands. */
    private final DbCommands dbCommands;

    /**
     * Creates a new {@link QuestionService}.
     *
     * @param context    server context retained for API compatibility
     * @param dbCommands database access helper
     */
    public QuestionService(IQuestionAnswerContext context, DbCommands dbCommands) {
        this.dbCommands = dbCommands;
    }

    /* =========================================================
     *                     CRUD OPERATIONS
     * ========================================================= */

    /**
     * Creates a new question for a teacher.
     * <p>
     * The client is responsible for validating all UI fields (statement, options,
     * correct option, start/end date and time). This method only:
     * <ul>
     *   <li>Performs defensive checks on the DTO and teacher id</li>
     *   <li>Applies business rules that are not duplicated on the client:
     *       <ul>
     *         <li>No duplicated option texts</li>
     *         <li>Correct option must exist in the options list</li>
     *         <li>Valid temporal window relative to the current time</li>
     *         <li>No other question with the same statement for the same teacher</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param dto question creation data sent by the client
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

        // Teacher id is not a UI field, so we still validate it explicitly.
        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("Identificador de docente inválido.");
        }

        /*
         * Defensive payload check: the client is responsible for validating
         * mandatory fields one by one. Here we only verify that the payload
         * is not clearly incomplete, without repeating per-field messages.
         */
        if (statement == null || statement.isBlank()
                || options == null || options.size() < 2
                || correct == null
                || startAt == null
                || endAt == null) {
            throw new IllegalArgumentException("Dados da pergunta inválidos (campos em falta).");
        }

        // ---------- business validations that are NOT duplicated on the client ----------

        String normalizedStatement = statement.trim();

        // Options: no duplicated texts (case-insensitive, trimmed).
        Set<OptionLetter> optionLetters = new LinkedHashSet<>();
        Set<String> normalizedTexts = new LinkedHashSet<>();
        for (Option o : options) {
            if (o == null || o.getLetter() == null || o.getText() == null || o.getText().isBlank()) {
                throw new IllegalArgumentException("Opção inválida recebida do cliente.");
            }
            if (!optionLetters.add(o.getLetter())) {
                throw new IllegalArgumentException("As letras das opções não podem ser repetidas.");
            }
            String normalized = o.getText().trim().toLowerCase(Locale.ROOT);
            if (!normalizedTexts.add(normalized)) {
                throw new IllegalArgumentException("As opções de resposta não podem ser iguais.");
            }
        }

        // Ensure the correct letter exists in the options list.
        boolean correctExists = options.stream()
                .anyMatch(o -> o != null && correct.equals(o.getLetter()));
        if (!correctExists) {
            throw new IllegalArgumentException(
                    "A opção correta tem de corresponder a uma das opções disponíveis."
            );
        }

        // Temporal rules: valid window and not in the past.
        LocalDateTime now   = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = startAt.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime end   = endAt.truncatedTo(ChronoUnit.MINUTES);

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim deve ser posterior à data/hora de início."
            );
        }

        if (start.isBefore(now)) {
            throw new IllegalArgumentException("A pergunta não pode começar no passado.");
        }

        if (!end.isAfter(now)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim deve ser posterior à data/hora atual."
            );
        }

        // A statement may be reused in another time window.
        Map<String, Object> existing = dbCommands.selectOne(
                "SELECT id FROM question " +
                        "WHERE teacher_id = ? " +
                        "AND LOWER(TRIM(statement)) = LOWER(TRIM(?)) " +
                        "AND start_at = ? AND end_at = ? " +
                        "LIMIT 1",
                teacherId, normalizedStatement, startAt.toString(), endAt.toString()
        );
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Já existe uma pergunta com o mesmo enunciado para este docente."
            );
        }

        // ---------- insertion + replication ----------

        String accessCode = null;
        long qId = -1L;
        for (int attempt = 1; attempt <= MAX_ACCESS_CODE_ATTEMPTS; attempt++) {
            String candidate = generateAccessCode();
            final long[] insertedId = new long[1];
            try {
                dbCommands.runInTransaction(tx -> {
                    tx.executeUpdate(
                            "INSERT INTO question (statement, teacher_id, correct_option, start_at, end_at, access_code) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)",
                            normalizedStatement, teacherId, correct.name(), startAt.toString(), endAt.toString(), candidate
                    );
                    insertedId[0] = tx.getLastInsertId();
                    for (Option o : options) {
                        tx.executeUpdate(
                                "INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)",
                                insertedId[0], o.getLetter().name(), o.getText().trim()
                        );
                    }
                });
                accessCode = candidate;
                qId = insertedId[0];
                break;
            } catch (Exception e) {
                if (!isAccessCodeCollision(e) || attempt == MAX_ACCESS_CODE_ATTEMPTS) {
                    throw e;
                }
            }
        }

        if (qId <= 0 || accessCode == null) {
            throw new IllegalStateException("Não foi possível gerar um código de acesso único.");
        }

        return new CreateQuestionResponseDTO((int) qId, accessCode);
    }

    /**
     * Edits an existing question (when there are no answers registered),
     * applies business rules and enqueues SQL for replication.
     * <p>
     * The client is responsible for UI validation of fields. This method:
     * <ul>
     *   <li>Checks ownership and prevents editing when answers exist</li>
     *   <li>Ensures no duplicated option letters or texts</li>
     *   <li>Ensures the correct option exists in the list</li>
     *   <li>Enforces a valid time window and prevents changing it into the past</li>
     *   <li>Ensures there is no other identical question for the same teacher</li>
     * </ul>
     *
     * @param dto edit parameters sent by the client
     * @return {@code true} if the question was updated
     * @throws Exception if DB access fails or business rules are violated
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

        /*
         * Defensive payload check: the client should send all fields already
         * validated. We just reject clearly incomplete payloads.
         */
        if (statement == null || statement.isBlank()
                || options == null || options.size() < 2
                || correct == null
                || startAt == null
                || endAt == null) {
            throw new IllegalArgumentException("Dados da pergunta inválidos (campos em falta).");
        }

        // Load original question to confirm ownership and get original dates.
        Map<String, Object> qRow = dbCommands.selectOne(
                "SELECT id, start_at, end_at FROM question WHERE id = ? AND teacher_id = ?",
                quizId,
                teacherId
        );
        if (qRow == null) {
            throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente.");
        }

        LocalDateTime originalStart = LocalDateTime.parse((String) qRow.get("start_at"));
        LocalDateTime originalEnd   = LocalDateTime.parse((String) qRow.get("end_at"));

        String normalizedStatement = statement.trim();

        // Always: end must be after start.
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "A data/hora de fim tem de ser posterior à data/hora de início."
            );
        }

        // Apply rules relative to "now" only if the time window has changed.
        boolean timeWindowChanged =
                !startAt.equals(originalStart) || !endAt.equals(originalEnd);

        if (timeWindowChanged) {
            LocalDateTime now = LocalDateTime.now();

            if (startAt.isBefore(now)) {
                throw new IllegalArgumentException("A pergunta não pode começar no passado.");
            }

            if (!endAt.isAfter(now)) {
                throw new IllegalArgumentException(
                        "A data/hora de fim tem de ser posterior à data/hora atual."
                );
            }
        }

        // Cannot edit if answers already exist.
        Map<String, Object> ans = dbCommands.selectOne(
                "SELECT 1 as one FROM answer WHERE question_id = ? LIMIT 1",
                quizId
        );
        if (ans != null) {
            throw new IllegalStateException("Não é possível editar pergunta com respostas registadas.");
        }

        /* --------- validate options (business rules only) --------- */

        List<Option> cleanedOptions = new ArrayList<>();
        Set<OptionLetter> usedLetters = new HashSet<>();
        Set<String> usedTexts = new HashSet<>();
        boolean correctOptionExists = false;

        for (Option opt : options) {
            if (opt == null) {
                throw new IllegalArgumentException("Opção inválida recebida do cliente.");
            }

            OptionLetter letter = opt.getLetter();
            String text = opt.getText();

            if (letter == null || text == null || text.isBlank()) {
                throw new IllegalArgumentException("Opção inválida recebida do cliente.");
            }

            String trimmedText = text.trim();
            String textKey = trimmedText.toLowerCase(Locale.ROOT);

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
            throw new IllegalArgumentException(
                    "A opção correta escolhida não existe na lista de opções."
            );
        }

        /* --------- check for duplicate (other record) --------- */

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

        /* --------- update in transaction + replication --------- */

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

        return true;
    }

    /**
     * Deletes a question if there are no answers registered
     * and enqueues SQL statements for replication.
     *
     * @param dto delete parameters
     * @return {@code true} if the question was deleted
     * @throws Exception if DB access fails or answers already exist
     */
    @Override
    public boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da pergunta inválidos.");
        }
        Integer qId = dto.questionId();
        Integer teacherId = dto.teacherId();
        if (qId == null || qId <= 0 || teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("Identificadores da pergunta inválidos.");
        }

        dbCommands.runInTransaction(tx -> {
            Map<String, Object> ownedQuestion = tx.selectOne(
                    "SELECT id FROM question WHERE id = ? AND teacher_id = ?",
                    qId,
                    teacherId
            );
            if (ownedQuestion == null) {
                throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente.");
            }

            Map<String, Object> answer = tx.selectOne(
                    "SELECT 1 AS one FROM answer WHERE question_id = ? LIMIT 1",
                    qId
            );
            if (answer != null) {
                throw new IllegalStateException("Não é possível eliminar pergunta com respostas registadas.");
            }

            int affectedRows = tx.executeUpdate(
                    "DELETE FROM question WHERE id = ? AND teacher_id = ?",
                    qId,
                    teacherId
            );
            if (affectedRows != 1) {
                throw new IllegalStateException("A pergunta não foi eliminada.");
            }
        });

        return true;
    }

    /* =========================================================
     *                        QUERIES
     * ========================================================= */

    /**
     * Lists questions for a teacher with an optional filter
     * (active, future, expired or {@code null} for all).
     *
     * @param dto list parameters (teacher and filter)
     * @return list of questions
     * @throws Exception if DB access fails
     */
    @Override
    public List<Question> listQuestions(ListQuestionsDTO dto) throws Exception {
        if (dto == null || dto.teacherId() == null || dto.teacherId() <= 0) {
            throw new IllegalArgumentException("Identificador de docente inválido.");
        }
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
            sql.append(" AND start_at <= ? AND end_at > ?");
            params.add(now.toString());
            params.add(now.toString());
        } else if ("future".equalsIgnoreCase(filter)) {
            sql.append(" AND start_at > ?");
            params.add(now.toString());
        } else if ("expired".equalsIgnoreCase(filter)) {
            sql.append(" AND end_at <= ?");
            params.add(now.toString());
        }

        List<Map<String, Object>> rows = dbCommands.selectList(sql.toString(), params.toArray());

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
    public StudentQuestionDTO joinQuestion(JoinQuestionDTO dto) throws Exception {
        if (dto == null || dto.studentId() == null || dto.studentId() <= 0) {
            throw new IllegalArgumentException("Dados de acesso à pergunta inválidos.");
        }
        String access = dto.accessCode();
        if (access == null || access.isBlank()) {
            throw new IllegalArgumentException("Código de acesso inválido.");
        }
        access = access.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> r = dbCommands.selectOne(
                "SELECT id, statement, start_at, end_at " +
                        "FROM question WHERE access_code = ? LIMIT 1",
                access
        );
        if (r == null) {
            return null;
        }

        int qId = ((Number) r.get("id")).intValue();
        LocalDateTime startAt = LocalDateTime.parse((String) r.get("start_at"));
        LocalDateTime endAt = LocalDateTime.parse((String) r.get("end_at"));
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt) || !now.isBefore(endAt)) {
            return null;
        }

        return new StudentQuestionDTO(
                qId,
                (String) r.get("statement"),
                loadOptions(qId),
                startAt,
                endAt
        );
    }

    /* =========================================================
     *                       PRIVATE HELPERS
     * ========================================================= */

    /**
     * Loads all answer options for a given question.
     *
     * @param questionId question ID
     * @return list of options
     * @throws Exception if DB access fails
     */
    private List<Option> loadOptions(int questionId) throws Exception {
        List<Option> opts = new ArrayList<>();
        for (Map<String, Object> row : dbCommands.selectList(
                "SELECT letter, text FROM option WHERE question_id = ? ORDER BY letter",
                questionId
        )) {
            OptionLetter letter = OptionLetter.valueOf((String) row.get("letter"));
            String text = (String) row.get("text");
            opts.add(new Option(letter, text));
        }
        return opts;
    }

    static String generateAccessCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
    }

    private static boolean isAccessCodeCollision(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException) {
                String message = current.getMessage();
                if (message != null
                        && message.toLowerCase(Locale.ROOT).contains("unique")
                        && message.toLowerCase(Locale.ROOT).contains("question.access_code")) {
                    return true;
                }
            }
        }
        return false;
    }

}
