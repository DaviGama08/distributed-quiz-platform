package pt.isec.common.model.question;

import pt.isec.common.model.common.OptionLetter;
import pt.isec.common.model.common.QuestionState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/*
1)
* "Criar perguntas de escolha múltipla, definindo o enunciado, o --número de opções--, as
opções, a --opção correta-- e o --período de disponibilidade-- (--data/hora de início e de fim--)"
*
2)
* "Quando uma pergunta é criada, o sistema gera automaticamente um --código de acesso--
que permite, aos utilizadores com perfil de estudante, visualizar e responder à
pergunta durante o respetivo período"
3)
O professor pode: "Eliminar uma pergunta, desde que ainda não tenha qualquer resposta associada;"
Logo precisamos de um código associado a esse professor --teacherId--
* */
public final class Question {
    private Integer id;
    private QuestionState statement;
    private String accessCode;
    private OptionLetter correctOption;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer teacherId;
    private List<Option> options;

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public QuestionState getStatement() {return statement;}
    public void setStatement(QuestionState statement) {this.statement = statement;}
    public String getAccessCode() {return accessCode;}
    public void setAccessCode(String accessCode) {this.accessCode = accessCode;}
    public OptionLetter getCorrectOption() {return correctOption;}
    public void setCorrectOption(OptionLetter correctOption) {this.correctOption = correctOption;}
    public LocalDateTime getStartAt() {return startAt;}
    public void setStartAt(LocalDateTime startAt) {this.startAt = startAt;}
    public LocalDateTime getEndAt() {return endAt;}
    public void setEndAt(LocalDateTime endAt) {this.endAt = endAt;}
    public Integer getTeacherId() {return teacherId;}
    public void setTeacherId(Integer teacherId) {this.teacherId = teacherId;}
    public List<Option> getOptions() {return options;}
    public void setOptions(List<Option> options) {this.options = options;}

    //equals/hashCode
    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question q = (Question) o;
        return correctOption.equals(q.correctOption) &&
                Objects.equals(id, q.id) &&
                Objects.equals(statement, q.statement) &&
                Objects.equals(accessCode, q.accessCode) &&
                Objects.equals(startAt, q.startAt) &&
                Objects.equals(endAt, q.endAt) &&
                Objects.equals(teacherId, q.teacherId) &&
                Objects.equals(options, q.options);
    }

    @Override
    public int hashCode() {return Objects.hash(id, statement, accessCode, correctOption, startAt, endAt, teacherId, options);}
}
