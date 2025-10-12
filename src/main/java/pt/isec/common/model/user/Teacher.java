package pt.isec.common.model.user;

import java.io.Serializable;
import java.util.Objects;

public final class Teacher extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;

    public Teacher(){}
    public Teacher(Integer id, String name, String email, String passwordHash){
        super(name, email, passwordHash); this.id = id;
    }

    // gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}

    // equals/hashCode
    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if(o == null || (o.getClass() != getClass())) return false;

        Teacher t = (Teacher)o;

        return Objects.equals(name, t.name) &&
                Objects.equals(email, t.email) &&
                Objects.equals(passwordHash, t.passwordHash) &&
                Objects.equals(id, t.id);
    }

    //Gera um número inteiro que representa a combinação dos três atributos
    @Override
    public int hashCode(){return Objects.hash(name, email, passwordHash, id);
    }

    @Override
    public String toString(){
        return "Id: " + id + "Name: " + name + ", Email: " + email;
    }
}
