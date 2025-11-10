package pt.isec.common.model.user;

import java.io.Serializable;
import java.util.Objects;

/**
 * Docente.
 * Usa long para o identificador (id).
 *
 * Regra: id == 0 significa "ainda não persistido" (deixa a BD gerar).
 */
public final class Teacher extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id; // PK (0 = não atribuído)

    public Teacher() { }

    public Teacher(long id, String name, String email, String passwordHash) {
        super(name, email, passwordHash);
        validateIdNonNegative(id);
        this.id = id;
    }

    // Construtor sem id (deixa BD gerar)
    public Teacher(String name, String email, String passwordHash) {
        this(0L, name, email, passwordHash);
    }

    // getters/setters
    public long getId() { return id; }

    public void setId(long id) {
        validateIdNonNegative(id);
        this.id = id;
    }

    private static void validateIdNonNegative(long id) {
        if (id < 0)
            throw new IllegalArgumentException("id must be >= 0 (0 means 'not yet persisted')");
    }

    // equals/hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || (o.getClass() != getClass())) return false;
        Teacher t = (Teacher) o;
        return id == t.id &&
                Objects.equals(name, t.name) &&
                Objects.equals(email, t.email) &&
                Objects.equals(passwordHash, t.passwordHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, passwordHash, id);
    }

    @Override
    public String toString() {
        return "Teacher{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
