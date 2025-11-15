package pt.isec.server.model.question;

import pt.isec.server.model.common.OptionLetter;
import pt.isec.server.model.common.QuestionState;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
public final class Question implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private QuestionState state;
    private String statement;
    private String accessCode;
    private OptionLetter correctOption;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer teacherId;
    private List<Option> options;

    public Question() {}

    public Question(String statement, Integer teacherId, List<Option> options,
                    LocalDateTime startAt, LocalDateTime endAt,
                    OptionLetter correctOption, String accessCode) {
        validate(statement, teacherId, options, startAt, endAt, correctOption, accessCode);
        this.id = null;
        this.statement = statement;
        this.teacherId = teacherId;
        this.options = List.copyOf(options); // evita modificação externa
        this.startAt = startAt;
        this.endAt = endAt;
        this.correctOption = correctOption;
        this.accessCode = accessCode;
        this.state = computeState(startAt, endAt, LocalDateTime.now());
    }

    public Question(Integer id, String statement, Integer teacherId, List<Option> options,
                    LocalDateTime startAt, LocalDateTime endAt,
                    OptionLetter correctOption, String accessCode) {
        validate(statement, teacherId, options, startAt, endAt, correctOption, accessCode);
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be > 0 if provided");
        this.id = id;
        this.statement = statement;
        this.teacherId = teacherId;
        this.options = List.copyOf(options);
        this.startAt = startAt;
        this.endAt = endAt;
        this.correctOption = correctOption;
        this.accessCode = accessCode;
        this.state = computeState(startAt, endAt, LocalDateTime.now());
    }

    //gets/sets
    public void setAccessCode(String accessCode) {
        if (accessCode != null && accessCode.isBlank())
            throw new IllegalArgumentException("accessCode cannot be blank if provided");
        this.accessCode = accessCode;
    }

    public void setStartAt(LocalDateTime startAt) {
        if (startAt == null) throw new IllegalArgumentException("startAt cannot be null");
        if (this.endAt != null && !this.endAt.isAfter(startAt))
            throw new IllegalArgumentException("endAt must be after startAt");
        this.startAt = startAt;
        refreshState();
    }

    public void setEndAt(LocalDateTime endAt) {
        if (endAt == null) throw new IllegalArgumentException("endAt cannot be null");
        if (this.startAt != null && !endAt.isAfter(this.startAt))
            throw new IllegalArgumentException("endAt must be after startAt");
        this.endAt = endAt;
        refreshState();
    }

    public void setOptions(List<Option> options) {
        // reusa parte da validação relevante
        if (options == null || options.size() < 2)
            throw new IllegalArgumentException("there must be at least two options");
        Set<OptionLetter> seen = new HashSet<>();
        for (Option o : options) {
            if (o == null || o.getLetter() == null)
                throw new IllegalArgumentException("each option must have a non-null letter");
            if (!seen.add(o.getLetter()))
                throw new IllegalArgumentException("duplicate option letter: " + o.getLetter());
        }
        if (this.correctOption != null &&
                options.stream().noneMatch(o -> o.getLetter() == this.correctOption))
            throw new IllegalArgumentException("current correctOption is not present in new options");

        this.options = List.copyOf(options);
    }

    public void setCorrectOption(OptionLetter correctOption) {
        if (correctOption == null) throw new IllegalArgumentException("correctOption cannot be null");
        if (this.options != null &&
                this.options.stream().noneMatch(o -> o.getLetter() == correctOption))
            throw new IllegalArgumentException("correctOption must exist in the options");
        this.correctOption = correctOption;
    }

    public void setStatement(String statement) {
        if (statement == null || statement.isBlank())
            throw new IllegalArgumentException("statement cannot be null or blank");
        this.statement = statement;
    }

    public void setTeacherId(Integer teacherId) {
        if (teacherId == null || teacherId <= 0)
            throw new IllegalArgumentException("teacherId must be a positive integer");
        this.teacherId = teacherId;
    }
    public Integer getId() {return id;}
    public QuestionState getState() {return state;}
    public String getStatement() {return statement;}
    public String getAccessCode() {return accessCode;}
    public OptionLetter getCorrectOption() {return correctOption;}
    public LocalDateTime getStartAt() {return startAt;}
    public LocalDateTime getEndAt() {return endAt;}
    public Integer getTeacherId() {return teacherId;}
    public List<Option> getOptions() {return options;}

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Verifica se a questão está ativa no momento atual
     */
    public boolean isActive() {
        refreshState();
        return state == QuestionState.ACTIVE;
    }

    /**
     * Verifica se a questão já expirou
     */
    public boolean isExpired() {
        refreshState();
        return state == QuestionState.EXPIRED;
    }

    /**
     * Verifica se a questão é futura
     */
    public boolean isFuture() {
        refreshState();
        return state == QuestionState.FUTURE;
    }

    /**
     * Verifica se uma resposta está correta
     */
    public boolean isCorrectAnswer(OptionLetter answer) {
        return correctOption.equals(answer);
    }

    private static QuestionState computeState(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        if (now.isBefore(startAt)) return QuestionState.FUTURE;
        if (now.isAfter(endAt))    return QuestionState.EXPIRED;
        return QuestionState.ACTIVE;
    }

    public void refreshState() {
        this.state = computeState(this.startAt, this.endAt, LocalDateTime.now());
    }

    private static void validate(String statement, Integer teacherId, List<Option> options,
                                 LocalDateTime startAt, LocalDateTime endAt,
                                 OptionLetter correctOption, String accessCode) {

        if (statement == null || statement.isBlank())
            throw new IllegalArgumentException("statement (question text) cannot be null or blank");

        if (teacherId == null || teacherId <= 0)
            throw new IllegalArgumentException("teacherId must be a positive integer");

        if (options == null || options.size() < 2)
            throw new IllegalArgumentException("there must be at least two options");

        // letras únicas (coerente com UNIQUE (pergunta_id, letra) na BD)
        Set<OptionLetter> seen = new HashSet<>();
        for (Option o : options) {
            if (o == null || o.getLetter() == null)
                throw new IllegalArgumentException("each option must have a non-null letter");
            if (!seen.add(o.getLetter()))
                throw new IllegalArgumentException("duplicate option letter: " + o.getLetter());
        }

        if (correctOption == null)
            throw new IllegalArgumentException("correctOption cannot be null");

        boolean containsCorrect = options.stream().anyMatch(o -> o.getLetter() == correctOption);
        if (!containsCorrect)
            throw new IllegalArgumentException("correctOption must exist in the provided options list");

        if (startAt == null || endAt == null)
            throw new IllegalArgumentException("startAt and endAt cannot be null");

        if (!endAt.isAfter(startAt))
            throw new IllegalArgumentException("endAt must be after startAt");

        if (accessCode != null && accessCode.isBlank())
            throw new IllegalArgumentException("accessCode cannot be blank if provided");
    }

    //toString
    @Override
    public String toString() {
        return "Question{id=" + id + ", statement=" + statement + ", accessCode='" + accessCode + '\'' +
                ", correctOption=" + correctOption + ", startAt=" + startAt + ", endAt=" + endAt +
                ", teacherId=" + teacherId + ", options=" + (options == null ? "[]" : options.size() + " itens") + '}';
    }

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
    public int hashCode() {
        return Objects.hash(id, statement, accessCode, correctOption, startAt, endAt, teacherId, options);
    }
}
