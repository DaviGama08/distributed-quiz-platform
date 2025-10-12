package pt.isec.common.model.user;

import java.io.Serializable;
import java.util.Objects;

public final class Student extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer studentNumber;
    public Student(){}
    public Student(Integer studentNumber, String name, String email, String passwordHash){
        super(name, email, passwordHash); this.studentNumber = studentNumber;
    }

    //gets/sets
    public Integer getStudentNumber() {return studentNumber;}
    public void setStudentNumber(Integer studentNumber) {this.studentNumber = studentNumber;}

    //equals/hashcode
    @Override
    public boolean equals(Object o){
        if(o == this) return true;
        if(o == null || o.getClass() != getClass()) return false;

        Student s = (Student)o;

        return Objects.equals(s.name, name) &&
                Objects.equals(s.email, email) &&
                Objects.equals(s.passwordHash, passwordHash) &&
                Objects.equals(s.studentNumber, studentNumber);
    }

    @Override
    public int hashCode(){
        return Objects.hash(name, email, passwordHash, studentNumber);
    }

    @Override
    public String toString(){
        return "Student Number: " + studentNumber + "\nName: " + name + "\nEmail: " + email + "\n";
    }
}
