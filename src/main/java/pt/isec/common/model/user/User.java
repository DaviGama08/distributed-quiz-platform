package pt.isec.common.model.user;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Base para utilizadores (Docente/Estudante).
 * Contém validação simples e getters/setters.
 */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    protected long id;
    protected String name;
    protected String email;
    protected String passwordHash;
    protected LocalDateTime createdAt;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public User(String name, String email, String passwordHash){
        validate(name, email, passwordHash);
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now();
    }

    public User(long id, String name, String email, String passwordHash, LocalDateTime createdAt){
        validate(name, email, passwordHash);
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // getters/setters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(long id) { this.id = id; }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name cannot be null or blank");
        this.name = name;
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("email cannot be null or blank");
        if (!email.contains("@") || !email.contains("."))
            throw new IllegalArgumentException("email must be a valid address");
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("passwordHash cannot be null or blank");
        this.passwordHash = passwordHash;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    protected static void validate(String name, String email, String passwordHash) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name cannot be null or blank");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("email cannot be null or blank");
        if (!email.contains("@") || !email.contains("."))
            throw new IllegalArgumentException("email must be a valid address");
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("passwordHash cannot be null or blank");
    }

    /**
     * Retorna o tipo de utilizador (student ou teacher)
     */
    public abstract String getUserType();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) &&
                Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

