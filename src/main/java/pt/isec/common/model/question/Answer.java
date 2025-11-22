package pt.isec.common.model.question;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa uma resposta de um estudante a uma questão
 */
public final class Answer implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer studentId;
    private Integer questionId;
    private OptionLetter selectedOption;
    private LocalDateTime answeredAt;
    private boolean isCorrect; // Se a resposta está correta

    public Answer() {}

    public Answer(Integer studentId, Integer questionId, OptionLetter selectedOption,
                  LocalDateTime answeredAt) {
        validate(studentId, questionId, selectedOption, answeredAt);
        this.studentId = studentId;
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.answeredAt = answeredAt;
        this.isCorrect = false; // Será definido pelo servidor
    }

    public Answer(Integer id, Integer studentId, Integer questionId,
                  OptionLetter selectedOption, LocalDateTime answeredAt, boolean isCorrect) {
        validate(studentId, questionId, selectedOption, answeredAt);
        this.id = id;
        this.studentId = studentId;
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.answeredAt = answeredAt;
        this.isCorrect = isCorrect;
    }

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}

    public Integer getStudentId() {return studentId;}
    public void setStudentId(Integer studentId) {
        if (studentId == null || studentId <= 0)
            throw new IllegalArgumentException("studentId must be positive");
        this.studentId = studentId;
    }

    public Integer getQuestionId() {return questionId;}
    public void setQuestionId(Integer questionId) {
        if (questionId == null || questionId <= 0)
            throw new IllegalArgumentException("questionId must be positive");
        this.questionId = questionId;
    }

    public OptionLetter getSelectedOption() {return selectedOption;}
    public void setSelectedOption(OptionLetter selectedOption) {
        if (selectedOption == null)
            throw new IllegalArgumentException("selectedOption cannot be null");
        this.selectedOption = selectedOption;
    }

    public LocalDateTime getAnsweredAt() {return answeredAt;}
    public void setAnsweredAt(LocalDateTime answeredAt) {
        if (answeredAt == null)
            throw new IllegalArgumentException("answeredAt cannot be null");
        this.answeredAt = answeredAt;
    }

    public boolean isCorrect() {return isCorrect;}
    public void setCorrect(boolean correct) {this.isCorrect = correct;}

    //toString
    @Override
    public String toString() {
        return "Answer{id=" + id + ", studentId=" + studentId +
                ", questionId=" + questionId + ", selectedOption=" + selectedOption +
                ", answeredAt=" + answeredAt + ", isCorrect=" + isCorrect + '}';
    }

    //equals/hashCode
    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Answer answer = (Answer) o;
        return selectedOption == answer.selectedOption &&
                Objects.equals(id, answer.id) &&
                Objects.equals(studentId, answer.studentId) &&
                Objects.equals(questionId, answer.questionId) &&
                Objects.equals(answeredAt, answer.answeredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, studentId, questionId, selectedOption, answeredAt);
    }

    private void validate(Integer studentId, Integer questionId,
                          OptionLetter selectedOption, LocalDateTime answeredAt) {

        if (studentId == null || studentId <= 0)
            throw new IllegalArgumentException("studentId must be positive");

        if (questionId == null || questionId <= 0)
            throw new IllegalArgumentException("questionId must be positive");

        if (selectedOption == null)
            throw new IllegalArgumentException("selectedOption cannot be null");

        if (answeredAt == null)
            throw new IllegalArgumentException("answeredAt cannot be null");
    }
}
