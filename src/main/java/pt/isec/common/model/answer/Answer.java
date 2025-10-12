package pt.isec.common.model.answer;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Answer implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Integer studentNumber;
    private Integer questionId;
    private char chosenOption;
    private LocalDateTime answeredAt;

    public Answer() {}

    public Answer(Integer studentNumber, Integer questionId, char chosenOption, LocalDateTime answeredAt) {
        setStudentNumber(studentNumber);
        setQuestionId(questionId);
        setChosenOption(chosenOption);
        this.answeredAt = answeredAt;
    }

    public Answer(Integer id, Integer studentNumber, Integer questionId, char chosenOption, LocalDateTime answeredAt) {
        this(studentNumber, questionId, chosenOption, answeredAt);
        this.id = id;
    }

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public Integer getStudentNumber() {return studentNumber;}
    public void setStudentNumber(Integer studentNumber) {
        if (studentNumber != null && studentNumber <= 0) {
            throw new IllegalArgumentException("studentNumber must be a positive integer");
        }
        this.studentNumber = studentNumber;
    }
    public Integer getQuestionId() {return questionId;}
    public void setQuestionId(Integer questionId) {
        if (questionId != null && questionId <= 0) {
            throw new IllegalArgumentException("questionId must be a positive integer");
        }
        this.questionId = questionId;
    }
    public char getChosenOption() {return chosenOption;}
    public void setChosenOption(char chosenOption) {
        char c = Character.toUpperCase(chosenOption);
        if (c < 'A' || c > 'E') {
            throw new IllegalArgumentException("Invalid chosenOption: must be a letter between A and E.");
        }
        this.chosenOption = c;
    }
    public LocalDateTime getAnsweredAt() {return answeredAt;}
    public void setAnsweredAt(LocalDateTime answeredAt) {this.answeredAt = answeredAt;}

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
}
