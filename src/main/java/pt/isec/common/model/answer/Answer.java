package pt.isec.common.model.answer;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Answer {
    private Integer id;
    private Integer studentNumber;
    private Integer questionId;
    private char chosenOption;
    private LocalDateTime answeredAt;

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public Integer getStudentNumber() {return studentNumber;}
    public void setStudentNumber(Integer studentNumber) {this.studentNumber = studentNumber;}
    public Integer getQuestionId() {return questionId;}
    public void setQuestionId(Integer questionId) {this.questionId = questionId;}
    public char getChosenOption() {return chosenOption;}
    public void setChosenOption(char chosenOption) {this.chosenOption = chosenOption;}
    public LocalDateTime getAnsweredAt() {return answeredAt;}
    public void setAnsweredAt(LocalDateTime answeredAt) {this.answeredAt = answeredAt;}

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
