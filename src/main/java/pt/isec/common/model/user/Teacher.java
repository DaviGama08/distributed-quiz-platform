package pt.isec.common.model.user;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Docente/Professor.
 * Identificado por id gerado pela base de dados.
 */
public final class Teacher extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    public Teacher() {
        super();
    }

    public Teacher(String name, String email, String passwordHash) {
        super(name, email, passwordHash);
    }

    public Teacher(long id, String name, String email, String passwordHash, LocalDateTime createdAt) {
        super(id, name, email, passwordHash, createdAt);
    }

    @Override
    public String getUserType() {
        return "teacher";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "Teacher{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

