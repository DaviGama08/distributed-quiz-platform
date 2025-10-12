package pt.isec.common.model.user;

import java.util.Objects;

//Definimos um User comum para os demais(estudantes e docentes)

public abstract class User {
    protected String name;
    protected String email;
    protected String passwordHash;

    public User(){}
    public User(String name, String email, String passwordHash){
        this.name = name; this.email = email; this.passwordHash = passwordHash;
    }

    //gets/sets
    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public String getEmail() {return email;}
    public void setEmail(String email) {this.email = email;}
    public String getPasswordHash() {return passwordHash;}
    public void setPasswordHash(String passwordHash) {this.passwordHash = passwordHash;}

    //equals/hashcode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(name, user.name)
                && Objects.equals(email, user.email)
                && Objects.equals(passwordHash, user.passwordHash);
    }
    //Gera um número inteiro que representa a combinação dos três atributos
    @Override
    public int hashCode() {return Objects.hash(name, email, passwordHash);}
}
