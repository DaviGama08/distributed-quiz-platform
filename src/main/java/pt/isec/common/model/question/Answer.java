package pt.isec.common.model.question;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a student's answer to a question.
 * <p>
 * This class is designed to be used both on the server and client side
 * as a simple DTO for transporting answer data.
 */
public final class Answer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ======================= FIELDS ======================= */

    private Integer id;
    private Integer studentId;
    private Long studentNumber;
    private Integer questionId;
    private OptionLetter selectedOption;
    private LocalDateTime answeredAt;
    private boolean resultAvailable;
    private Boolean correct;
    private String studentName;
    private String studentEmail;
    private String questionStatement;

    /* ======================= CONSTRUCTORS ======================= */

    /**
     * Default constructor (intended for serialization frameworks).
     */
    public Answer() {}

    /**
     * Constructs a full answer object.
     *
     * @param id                answer identifier
     * @param studentId         student database identifier
     * @param studentNumber     student number (external identifier)
     * @param questionId        question identifier
     * @param selectedOption    chosen option letter
     * @param answeredAt        date/time when the answer was given
     * @param isCorrect         {@code true} if the answer is correct
     * @param studentName       student full name
     * @param studentEmail      student email address
     * @param questionStatement question statement text
     */
    public Answer(Integer id,
                  Integer studentId,
                  Long studentNumber,
                  Integer questionId,
                  OptionLetter selectedOption,
                  LocalDateTime answeredAt,
                  boolean isCorrect,
                  String studentName,
                  String studentEmail,
                  String questionStatement) {

        this(id, studentId, studentNumber, questionId, selectedOption, answeredAt,
                true, isCorrect, studentName, studentEmail, questionStatement);
    }

    /**
     * Constructs an answer whose correctness may still be unavailable.
     */
    public Answer(Integer id,
                  Integer studentId,
                  Long studentNumber,
                  Integer questionId,
                  OptionLetter selectedOption,
                  LocalDateTime answeredAt,
                  boolean resultAvailable,
                  Boolean correct,
                  String studentName,
                  String studentEmail,
                  String questionStatement) {
        if (resultAvailable && correct == null) {
            throw new IllegalArgumentException("Available result must have a correctness value");
        }
        if (!resultAvailable && correct != null) {
            throw new IllegalArgumentException("Unavailable result cannot expose correctness");
        }
        this.id = id;
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.answeredAt = answeredAt;
        this.resultAvailable = resultAvailable;
        this.correct = correct;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.questionStatement = questionStatement;
    }

    /* ======================= GETTERS ======================= */

    /**
     * @return answer identifier
     */
    public Integer getId() {
        return id;
    }

    /**
     * @return student database identifier
     */
    @SuppressWarnings("unused") // kept for future use / frameworks
    public Integer getStudentId() {
        return studentId;
    }

    /**
     * @return student number (external identifier)
     */
    public Long getStudentNumber() {
        return studentNumber;
    }

    /**
     * @return question identifier
     */
    public Integer getQuestionId() {
        return questionId;
    }

    /**
     * @return selected option letter
     */
    public OptionLetter getSelectedOption() {
        return selectedOption;
    }

    /**
     * @return date/time when the answer was given
     */
    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    /**
     * @return {@code true} if the answer is correct
     */
    public boolean isCorrect() {
        return Boolean.TRUE.equals(correct);
    }

    /**
     * Indicates whether the correctness result may be shown to the student.
     */
    public boolean isResultAvailable() {
        return resultAvailable;
    }

    /**
     * Returns correctness, or {@code null} while the question is still active/future.
     */
    public Boolean getCorrect() {
        return correct;
    }

    /**
     * @return student full name
     */
    public String getStudentName() {
        return studentName;
    }

    /**
     * @return student email address
     */
    public String getStudentEmail() {
        return studentEmail;
    }

    /**
     * @return question statement text
     */
    public String getQuestionStatement() {
        return questionStatement;
    }

    /* ======================= SETTERS ======================= */

    /**
     * Sets the answer identifier.
     *
     * @param id new identifier
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Sets the student identifier.
     *
     * @param studentId student database identifier
     */
    @SuppressWarnings("unused") // kept for symmetry and possible framework binding
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }

    /**
     * Sets the student number.
     *
     * @param studentNumber student number (external identifier)
     */
    @SuppressWarnings("unused") // may be used by mappers / frameworks
    public void setStudentNumber(Long studentNumber) {
        this.studentNumber = studentNumber;
    }

    /**
     * Sets the question identifier.
     *
     * @param questionId question identifier
     */
    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    /**
     * Sets the selected option.
     *
     * @param selectedOption chosen option letter
     */
    @SuppressWarnings("unused") // currently unused, kept for completeness
    public void setSelectedOption(OptionLetter selectedOption) {
        this.selectedOption = selectedOption;
    }

    /**
     * Sets the answer date/time.
     *
     * @param answeredAt date/time when the answer was given
     */
    @SuppressWarnings("unused") // currently unused, kept for completeness
    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    /**
     * Sets whether the answer is correct.
     *
     * @param correct {@code true} if the answer is correct
     */
    public void setCorrect(boolean correct) {
        this.resultAvailable = true;
        this.correct = correct;
    }

    /* ======================= OVERRIDDEN METHODS ======================= */

    @Override
    public String toString() {
        return "Answer{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", studentNumber=" + studentNumber +
                ", questionId=" + questionId +
                ", selectedOption=" + selectedOption +
                ", answeredAt=" + answeredAt +
                ", resultAvailable=" + resultAvailable +
                ", correct=" + correct +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Answer answer)) return false;
        return resultAvailable == answer.resultAvailable &&
                Objects.equals(id, answer.id) &&
                Objects.equals(studentId, answer.studentId) &&
                Objects.equals(studentNumber, answer.studentNumber) &&
                Objects.equals(questionId, answer.questionId) &&
                selectedOption == answer.selectedOption &&
                Objects.equals(answeredAt, answer.answeredAt) &&
                Objects.equals(correct, answer.correct) &&
                Objects.equals(studentName, answer.studentName) &&
                Objects.equals(studentEmail, answer.studentEmail) &&
                Objects.equals(questionStatement, answer.questionStatement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, studentId, studentNumber, questionId,
                selectedOption, answeredAt, resultAvailable, correct,
                studentName, studentEmail, questionStatement
        );
    }
}
