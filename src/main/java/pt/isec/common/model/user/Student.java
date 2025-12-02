package pt.isec.common.model.user;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Student entity.
 * <p>
 * Identified by a unique {@code studentNumber}.
 */
public final class Student extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    private long studentNumber; // unique student number

    /**
     * Default constructor.
     */
    public Student() {
        super();
    }

    /**
     * Constructs a student with basic fields.
     *
     * @param name          student name
     * @param email         email address
     * @param passwordHash  hashed password
     * @param studentNumber unique student number
     */
    public Student(String name, String email, String passwordHash, long studentNumber) {
        super(name, email, passwordHash);
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    /**
     * Constructs a student with all fields.
     *
     * @param id            student id
     * @param name          student name
     * @param email         email address
     * @param passwordHash  hashed password
     * @param studentNumber unique student number
     * @param createdAt     creation time
     */
    public Student(long id, String name, String email, String passwordHash,
                   long studentNumber, LocalDateTime createdAt) {
        super(id, name, email, passwordHash, createdAt);
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    // getters/setters
    public long getStudentNumber() { return studentNumber; }

    public void setStudentNumber(Integer studentNumber) {
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    /**
     * Validates the student number.
     *
     * @param studentNumber student number
     */
    private static void validateStudentNumber(long studentNumber) {
        if (studentNumber <= 0) {
            throw new IllegalArgumentException("studentNumber must be a positive integer");
        }
    }

    @Override
    public String getUserType() {
        return "student";
    }

    // equals/hashCode
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Student student = (Student) o;
        return Objects.equals(studentNumber, student.studentNumber);
    }

    @Override
    public int hashCode(){
        return Objects.hash(super.hashCode(), studentNumber);
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", studentNumber=" + studentNumber +
                ", createdAt=" + createdAt +
                '}';
    }
}
