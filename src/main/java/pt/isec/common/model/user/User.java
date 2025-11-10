package pt.isec.common.model.user;

import java.util.Objects;

/**
 * Base para utilizadores (Docente/Estudante).
 * Contém validação simples e getters/setters.
 */
public abstract class User {
    protected String name;
    protected String email;
    protected String passwordHash;

    public User() { }

    public User(String name, String email, String passwordHash){
        validate(name, email, passwordHash);
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // getters/setters
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }

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

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", passwordHash='" + (passwordHash == null ? "null" : "***") + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(name, user.name)
                && Objects.equals(email, user.email)
                && Objects.equals(passwordHash, user.passwordHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, passwordHash);
    }
}
