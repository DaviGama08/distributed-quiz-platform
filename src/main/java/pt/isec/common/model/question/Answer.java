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
    private Integer studentNumber; // Added
    private Integer questionId;
    private OptionLetter selectedOption;
    private LocalDateTime answeredAt;
    private boolean isCorrect; // Se a resposta está correta
    private String studentName;
    private String studentEmail;
    private String questionStatement;

    public Answer() {}

    public Answer(Integer id,
                  Integer studentId,
                  Integer studentNumber, // Added
                  Integer questionId,
                  OptionLetter selectedOption,
                  LocalDateTime answeredAt,
                  boolean isCorrect,
                  String studentName,
                  String studentEmail,
                  String questionStatement) {
        this.id = id;
        this.studentId = studentId;
        this.studentNumber = studentNumber; // Added
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.answeredAt = answeredAt;
        this.isCorrect = isCorrect;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.questionStatement = questionStatement;
    }

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}

    public Integer getStudentId() {return studentId;}
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }

    public Integer getStudentNumber() { return studentNumber; } // Added
    public void setStudentNumber(Integer studentNumber) { this.studentNumber = studentNumber; } // Added

    public String getQuestionStatement() {
        return questionStatement;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public Integer getQuestionId() {return questionId;}
    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public OptionLetter getSelectedOption() {return selectedOption;}
    public void setSelectedOption(OptionLetter selectedOption) {
        this.selectedOption = selectedOption;
    }

    public LocalDateTime getAnsweredAt() {return answeredAt;}
    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public boolean isCorrect() {return isCorrect;}
    public void setCorrect(boolean correct) {this.isCorrect = correct;}

    @Override
    public String toString() {
        return "Answer{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", studentNumber=" + studentNumber +
                ", questionId=" + questionId +
                ", selectedOption=" + selectedOption +
                ", answeredAt=" + answeredAt +
                ", isCorrect=" + isCorrect +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Answer answer = (Answer) o;
        return isCorrect == answer.isCorrect && Objects.equals(id, answer.id) && Objects.equals(studentId, answer.studentId) && Objects.equals(studentNumber, answer.studentNumber) && Objects.equals(questionId, answer.questionId) && selectedOption == answer.selectedOption && Objects.equals(answeredAt, answer.answeredAt) && Objects.equals(studentName, answer.studentName) && Objects.equals(studentEmail, answer.studentEmail) && Objects.equals(questionStatement, answer.questionStatement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, studentId, studentNumber, questionId, selectedOption, answeredAt, isCorrect, studentName, studentEmail, questionStatement);
    }
}
