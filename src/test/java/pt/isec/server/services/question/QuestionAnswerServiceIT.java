package pt.isec.server.services.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.question.CreateQuestionDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.dto.question.DeleteQuestionDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.dto.question.StudentQuestionDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;
import pt.isec.server.db.DbCreate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAnswerServiceIT {
    @TempDir
    Path tempDir;

    private DbCommands db;
    private QuestionService questions;
    private AnswerService answers;

    @BeforeEach
    void setUp() throws Exception {
        Path database = tempDir.resolve("quiz.db");
        DbCreate.createIfMissing(database, "/db/schema.sql");
        db = new DbCommands("jdbc:sqlite:" + database.toAbsolutePath());
        TestContext context = new TestContext();
        questions = new QuestionService(context, db);
        answers = new AnswerService(context, db);

        db.executeUpdate(
                "INSERT INTO teacher (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
                1, "Teacher A", "teacher-a@example.test", "hash"
        );
        db.executeUpdate(
                "INSERT INTO teacher (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
                2, "Teacher B", "teacher-b@example.test", "hash"
        );
        db.executeUpdate(
                "INSERT INTO student (id, student_number, name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                1, 3_000_000_000L, "Student A", "student-a@example.test", "hash"
        );
    }

    @Test
    void joinedQuestionIsActiveNormalizedAndCannotLeakCorrectAnswer() throws Exception {
        int questionId = insertQuestion(
                "active1",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        StudentQuestionDTO joined = questions.joinQuestion(new JoinQuestionDTO("  AcTiVe1  ", 1));

        assertNotNull(joined);
        assertEquals(questionId, joined.id());
        Set<String> fields = List.of(StudentQuestionDTO.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertFalse(fields.contains("correctOption"));
        assertFalse(fields.contains("teacherId"));
        assertFalse(fields.contains("accessCode"));
    }

    @Test
    void joinRejectsFutureAndExpiredQuestions() throws Exception {
        insertQuestion("future", LocalDateTime.now().plusMinutes(5), LocalDateTime.now().plusMinutes(10));
        insertQuestion("expired", LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(1));

        assertNull(questions.joinQuestion(new JoinQuestionDTO("future", 1)));
        assertNull(questions.joinQuestion(new JoinQuestionDTO("expired", 1)));
    }

    @Test
    void answerRequiresAnOptionOwnedByTheQuestionAndRejectsDuplicates() throws Exception {
        int questionId = insertQuestion(
                "answer1",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        assertThrows(IllegalArgumentException.class, () -> answers.submitAnswer(
                new SubmitAnswerDTO(questionId, 1, OptionLetter.C)
        ));
        assertTrue(answers.submitAnswer(new SubmitAnswerDTO(questionId, 1, OptionLetter.A)));
        assertThrows(IllegalStateException.class, () -> answers.submitAnswer(
                new SubmitAnswerDTO(questionId, 1, OptionLetter.A)
        ));
    }

    @Test
    void answerRejectsFutureAndExpiredQuestionWindows() throws Exception {
        int future = insertQuestion(
                "answer2",
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(10)
        );
        int expired = insertQuestion(
                "answer3",
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusSeconds(1)
        );

        assertThrows(IllegalStateException.class, () -> answers.submitAnswer(
                new SubmitAnswerDTO(future, 1, OptionLetter.A)
        ));
        assertThrows(IllegalStateException.class, () -> answers.submitAnswer(
                new SubmitAnswerDTO(expired, 1, OptionLetter.A)
        ));
    }

    @Test
    void historyHidesCorrectnessUntilQuestionExpires() throws Exception {
        int active = insertQuestion(
                "history1",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );
        int expired = insertQuestion(
                "history2",
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusMinutes(1)
        );
        db.executeUpdate(
                "INSERT INTO answer (student_id, question_id, chosen_option, created_at) VALUES (?, ?, ?, ?)",
                1, active, "A", LocalDateTime.now().toString()
        );
        db.executeUpdate(
                "INSERT INTO answer (student_id, question_id, chosen_option, created_at) VALUES (?, ?, ?, ?)",
                1, expired, "B", LocalDateTime.now().minusMinutes(2).toString()
        );

        List<Answer> history = answers.getStudentHistory(1);
        Answer activeResult = history.stream().filter(a -> a.getQuestionId().equals(active)).findFirst().orElseThrow();
        Answer expiredResult = history.stream().filter(a -> a.getQuestionId().equals(expired)).findFirst().orElseThrow();

        assertFalse(activeResult.isResultAvailable());
        assertNull(activeResult.getCorrect());
        assertTrue(expiredResult.isResultAvailable());
        assertEquals(Boolean.FALSE, expiredResult.getCorrect());
    }

    @Test
    void deletingAnotherTeachersQuestionPreservesQuestionAndOptions() throws Exception {
        int questionId = insertQuestion(
                "delete1",
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        assertThrows(IllegalArgumentException.class, () -> questions.deleteQuestion(
                new DeleteQuestionDTO(questionId, 2)
        ));
        assertNotNull(db.selectOne("SELECT id FROM question WHERE id = ?", questionId));
        assertNotNull(db.selectOne("SELECT id FROM option WHERE question_id = ? LIMIT 1", questionId));

        assertTrue(questions.deleteQuestion(new DeleteQuestionDTO(questionId, 1)));
        assertNull(db.selectOne("SELECT id FROM question WHERE id = ?", questionId));
        assertNull(db.selectOne("SELECT id FROM option WHERE question_id = ? LIMIT 1", questionId));
    }

    @Test
    void duplicateRuleIncludesTheTimeWindow() throws Exception {
        LocalDateTime firstStart = LocalDateTime.now().plusMinutes(5).withNano(0);
        LocalDateTime firstEnd = firstStart.plusMinutes(5);
        CreateQuestionDTO first = createQuestion(" Reusable statement ", firstStart, firstEnd);
        CreateQuestionResponseDTO created = questions.createQuestion(first);
        assertNotNull(created);

        assertThrows(IllegalArgumentException.class, () -> questions.createQuestion(
                createQuestion("reusable statement", firstStart, firstEnd)
        ));
        assertNotNull(questions.createQuestion(
                createQuestion("reusable statement", firstStart.plusHours(1), firstEnd.plusHours(1))
        ));
    }

    @Test
    void schemaCompositeForeignKeyRejectsAnUnknownOption() throws Exception {
        int questionId = insertQuestion(
                "fkcheck",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        assertThrows(RuntimeException.class, () -> db.executeUpdate(
                "INSERT INTO answer (student_id, question_id, chosen_option) VALUES (?, ?, ?)",
                1, questionId, "E"
        ));
    }

    private CreateQuestionDTO createQuestion(String statement, LocalDateTime start, LocalDateTime end) {
        return new CreateQuestionDTO(
                statement,
                1,
                List.of(new Option(OptionLetter.A, "One"), new Option(OptionLetter.B, "Two")),
                OptionLetter.A,
                start,
                end
        );
    }

    private int insertQuestion(String accessCode, LocalDateTime start, LocalDateTime end) {
        db.executeUpdate(
                "INSERT INTO question (teacher_id, statement, access_code, correct_option, start_at, end_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                1, "Question " + accessCode, accessCode.toUpperCase(), "A", start.toString(), end.toString()
        );
        int id = ((Number) db.selectOne(
                "SELECT id FROM question WHERE access_code = ?",
                accessCode.toUpperCase()
        ).get("id")).intValue();
        db.executeUpdate("INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)", id, "A", "One");
        db.executeUpdate("INSERT INTO option (question_id, letter, text) VALUES (?, ?, ?)", id, "B", "Two");
        return id;
    }

    private static final class TestContext implements IQuestionAnswerContext {
        @Override public void sendToUser(String role, long userId, TcpMessage<?> msg) { }
    }
}
