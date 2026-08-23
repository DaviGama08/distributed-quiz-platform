package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;
import pt.isec.common.util.Log;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that handles answer submission and queries.
 */
@SuppressWarnings("ClassCanBeRecord")
public class AnswerService implements IAnswerService {
    private final IQuestionAnswerContext context;
    private final DbCommands dbCommands;

    /**
     * Creates a new {@link AnswerService}.
     *
     * @param context    question/answer context used for replication and notifications
     * @param dbCommands database access helper
     */
    public AnswerService(IQuestionAnswerContext context, DbCommands dbCommands) {
        this.context = context;
        this.dbCommands = dbCommands;
    }

    /**
     * Registers a new answer and updates replication.
     *
     * @param dto answer data
     * @return {@code true} if successfully recorded
     */
    @Override
    public boolean submitAnswer(SubmitAnswerDTO dto)  {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da resposta inválidos.");
        }
        Integer questionId = dto.questionId();
        Integer studentId = dto.studentId();
        OptionLetter selected = dto.selectedOption();
        if (questionId == null || questionId <= 0
                || studentId == null || studentId <= 0
                || selected == null) {
            throw new IllegalArgumentException("Dados da resposta inválidos.");
        }
        LocalDateTime now = LocalDateTime.now();

        // validate question (existence + active period) and retrieve teacher_id
        Map<String, Object> q = dbCommands.selectOne(
                "SELECT teacher_id, correct_option, start_at, end_at FROM question WHERE id = ?",
                questionId
        );
        if (q == null) {
            throw new IllegalArgumentException("Pergunta inexistente");
        }

        LocalDateTime startAt = LocalDateTime.parse((String) q.get("start_at"));
        LocalDateTime endAt = LocalDateTime.parse((String) q.get("end_at"));
        if (now.isBefore(startAt) || !now.isBefore(endAt)) {
            throw new IllegalStateException("Pergunta fora do período de disponibilidade");
        }

        Map<String, Object> validOption = dbCommands.selectOne(
                "SELECT 1 AS one FROM option WHERE question_id = ? AND letter = ?",
                questionId,
                selected.name()
        );
        if (validOption == null) {
            throw new IllegalArgumentException("Opção inválida para esta pergunta.");
        }

        Map<String, Object> existing = dbCommands.selectOne(
                "SELECT 1 AS one FROM answer WHERE student_id = ? AND question_id = ? LIMIT 1",
                studentId, questionId
        );
        if (existing != null) {
            throw new IllegalStateException("Já respondeu a esta pergunta.");
        }

        // insert answer
        dbCommands.executeUpdate(
                "INSERT INTO answer (student_id, question_id, chosen_option, created_at) VALUES (?, ?, ?, ?)",
                studentId, questionId, selected.name(), now.toString()
        );

        // Try to notify the owning teacher (if connected)
        try {
            Object rawTeacherId = q.get("teacher_id");
            Long teacherId = null;

            if (rawTeacherId instanceof Number n) {
                teacherId = n.longValue();
            } else if (rawTeacherId instanceof String s && !s.isBlank()) {
                teacherId = Long.parseLong(s);
            }

            if (teacherId != null) {
                TcpMessage<Integer> notify =
                        new TcpMessage<>(MessageType.ANSWER_SUBMITTED, questionId, Integer.class);
                context.sendToUser("TEACHER", teacherId, notify);

                Log.info(AnswerService.class,
                        "Real-time notification attempt sent to teacher %d (question %d).",
                        teacherId, questionId);
            }
        } catch (Exception e) {
            // Notification failures must not prevent successful submission
            Log.error(AnswerService.class,
                    "Failed to notify teacher in real time: %s", e.getMessage());
        }

        return true;
    }

    /**
     * Returns all answers for a question (teacher view).
     * Calculates {@code isCorrect} in memory.
     *
     * @param dto query parameters
     * @return list of answers
     * @throws Exception if DB access fails
     */
    @Override
    public List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception {
        if (dto == null || dto.questionId() == null || dto.questionId() <= 0
                || dto.teacherId() == null || dto.teacherId() <= 0) {
            throw new IllegalArgumentException("Dados da consulta inválidos.");
        }
        Integer questionId = dto.questionId();
        Integer teacherId = dto.teacherId();

        // check question ownership
        Map<String, Object> rec = dbCommands.selectOne(
                "SELECT correct_option FROM question WHERE id = ? AND teacher_id = ?",
                questionId, teacherId
        );
        if (rec == null) {
            throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente");
        }

        OptionLetter correct = OptionLetter.valueOf((String) rec.get("correct_option"));

        List<Answer> out = new ArrayList<>();
        for (Map<String, Object> row : dbCommands.selectList(
                "SELECT a.student_id, a.chosen_option, a.created_at, " +
                        "s.name AS student_name, s.email AS student_email, s.student_number " +
                        "FROM answer a " +
                        "JOIN student s ON s.id = a.student_id " +
                        "WHERE a.question_id = ? " +
                        "ORDER BY a.created_at",
                questionId
        )) {
            Integer studentId = ((Number) row.get("student_id")).intValue();
            OptionLetter selected = OptionLetter.valueOf((String) row.get("chosen_option"));
            LocalDateTime answeredAt = LocalDateTime.parse((String) row.get("created_at"));
            out.add(new Answer(
                    null,
                    studentId,
                    ((Number) row.get("student_number")).longValue(),
                    questionId,
                    selected,
                    answeredAt,
                    selected.equals(correct),
                    (String) row.get("student_name"),
                    (String) row.get("student_email"),
                    null
            ));
        }
        return out;
    }

    /**
     * Returns the answer history of a student.
     * Calculates {@code isCorrect} using the question's {@code correct_option}.
     *
     * @param studentId student ID
     * @return list of answers for that student
     * @throws Exception if DB access fails
     */
    @Override
    public List<Answer> getStudentHistory(Integer studentId) throws Exception {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Identificador de estudante inválido.");
        }
        List<Answer> out = new ArrayList<>();
        for (Map<String, Object> row : dbCommands.selectList(
                "SELECT a.question_id, a.chosen_option, a.created_at, " +
                        "q.correct_option, q.statement, q.end_at " +
                        "FROM answer a " +
                        "JOIN question q ON q.id = a.question_id " +
                        "WHERE a.student_id = ? " +
                        "ORDER BY a.created_at DESC",
                studentId
        )) {
            Integer questionId = ((Number) row.get("question_id")).intValue();
            OptionLetter selected = OptionLetter.valueOf((String) row.get("chosen_option"));
            LocalDateTime answeredAt = LocalDateTime.parse((String) row.get("created_at"));
            LocalDateTime endAt = LocalDateTime.parse((String) row.get("end_at"));
            boolean resultAvailable = !LocalDateTime.now().isBefore(endAt);
            Boolean correct = resultAvailable
                    ? selected.equals(OptionLetter.valueOf((String) row.get("correct_option")))
                    : null;

            out.add(new Answer(
                    null,
                    studentId,
                    null,
                    questionId,
                    selected,
                    answeredAt,
                    resultAvailable,
                    correct,
                    null,
                    null,
                    (String) row.get("statement")
            ));
        }
        return out;
    }
}
