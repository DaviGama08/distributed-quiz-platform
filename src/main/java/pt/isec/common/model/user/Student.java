package pt.isec.common.model.user;

import java.io.Serializable;
import java.util.Objects;

/**
 * Estudante.
 * Usa long para o identificador (studentNumber).
 */
public final class Student extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    private long studentNumber; // PK

    public Student() { }

    public Student(long studentNumber, String name, String email, String passwordHash) {
        super(name, email, passwordHash);
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    // getters/setters
    public long getStudentNumber() { return studentNumber; }

    public void setStudentNumber(long studentNumber) {
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    private static void validateStudentNumber(long studentNumber) {
        if (studentNumber <= 0)
            throw new IllegalArgumentException("studentNumber must be a positive integer");
    }

    // equals/hashCode
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        Student s = (Student) o;
        return studentNumber == s.studentNumber &&
                Objects.equals(name, s.name) &&
                Objects.equals(email, s.email) &&
                Objects.equals(passwordHash, s.passwordHash);
    }

    @Override
    public int hashCode(){
        return Objects.hash(name, email, passwordHash, studentNumber);
    }

    @Override
    public String toString() {
        return "Student{" +
                "studentNumber=" + studentNumber +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
