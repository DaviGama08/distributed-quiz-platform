package pt.isec.common.model.answer;

import pt.isec.common.model.common.OptionLetter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Answer implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer studentNumber;
    private Integer questionId;
    private OptionLetter chosenOption;
    private LocalDateTime answeredAt;

    public Answer() {}

    public Answer(Integer studentNumber, Integer questionId, OptionLetter chosenOption,
                  LocalDateTime answeredAt) {
        validate(studentNumber, questionId, chosenOption, answeredAt);
        this.studentNumber = studentNumber;
        this.questionId = questionId;
        this.chosenOption = chosenOption;
        this.answeredAt = answeredAt;
    }

    public Answer(Integer id, Integer studentNumber, Integer questionId, OptionLetter chosenOption, LocalDateTime answeredAt) {
        this(studentNumber, questionId, chosenOption, answeredAt);
        this.id = id;
    }

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public Integer getStudentNumber() {return studentNumber;}
    public void setStudentNumber(Integer studentNumber) {this.studentNumber = studentNumber;}
    public Integer getQuestionId() {return questionId;}
    public void setQuestionId(Integer questionId) {this.questionId = questionId;}
    public OptionLetter getChosenOption() {return chosenOption;}
    public void setChosenOption(OptionLetter chosenOption) {this.chosenOption = chosenOption;}
    public LocalDateTime getAnsweredAt() {return answeredAt;}
    public void setAnsweredAt(LocalDateTime answeredAt) {this.answeredAt = answeredAt;}

    //toString
    @Override
    public String toString() {
        return "Answer{id=" + id + ", studentNumber=" + studentNumber +
                ", questionId=" + questionId + ", chosenOption=" + chosenOption +
                ", answeredAt=" + answeredAt + '}';
    }

    //equals/hashCode
    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Answer answer = (Answer) o;
        return chosenOption == answer.chosenOption &&
                Objects.equals(id, answer.id) &&
                Objects.equals(studentNumber, answer.studentNumber) &&
                Objects.equals(questionId, answer.questionId) &&
                Objects.equals(answeredAt, answer.answeredAt);
    }
    @Override
    public int hashCode() {return Objects.hash(id, studentNumber, questionId, chosenOption, answeredAt);}

    private void validate(Integer studentNumber, Integer questionId,
                          OptionLetter chosenOption, LocalDateTime answeredAt) {

        if (studentNumber == null || studentNumber <= 0)
            throw new IllegalArgumentException("studentNumber must be positive");

        if (questionId == null || questionId <= 0)
            throw new IllegalArgumentException("questionId must be positive");

        if (chosenOption == null)
            throw new IllegalArgumentException("chosenOption cannot be null");

        if (answeredAt == null)
            throw new IllegalArgumentException("answeredAt cannot be null");

        if (answeredAt.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("answeredAt cannot be in the future");
    }
}
