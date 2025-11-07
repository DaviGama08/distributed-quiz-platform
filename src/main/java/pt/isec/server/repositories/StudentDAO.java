package pt.isec.server.repositories;

import pt.isec.common.model.user.Student;
import pt.isec.common.model.user.Teacher;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class StudentDAO implements IUserDAO<Student>{
    //classe para aceder a dados do estudante na base de dados
    private final DatabaseManager db;

    public StudentDAO(){
        db = DatabaseManager.getInstance();
    }

    @Override
    public long add(Student s) throws SQLException {
        String sql = "INSERT INTO student (student_number, name, email, password_hash) VALUES (?, ?, ?, ?)";
        return db.executeUpdate(sql,
                s.getStudentNumber(),
                s.getName(),
                s.getEmail(),
                s.getPasswordHash()
        );
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer one = db.queryForSingleValue("SELECT 1 FROM student WHERE email = ? LIMIT 1",email);
        return one != null;
    }

    @Override
    public Optional<Student> findById(long id) throws SQLException {
        String sql = "SELECT name, email, password_hash FROM student WHERE student_number = ?";
        Student aux = db.queryForObject(sql, rs -> new Student(
                rs.getInt("student_number"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash")
        ), id);

        if(aux == null)
            return Optional.empty();
        return Optional.of(aux);
    }

    @Override
    public Optional<Student> findByEmail(String email){
        //Optional.ofNullable: Se não existe, retorna Optional.empty() em vez de lançar exceção ou retornar null
        return Optional.ofNullable(db.queryForObject(
                "SELECT studentNumber, name, email, password_hash FROM teacher WHERE email = ? LIMIT 1;",
                rs -> new Student(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                ),
                email
        ));
    }

    @Override
    public List<Student> findAll() throws SQLException {
        String sql = "SELECT id, name, email, password_hash FROM student ORDER BY name";
        return db.queryList(sql, rs ->  new Student(
                rs.getInt("student_number"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"))
        );
    }

    @Override
    public void update(Student s) throws SQLException {
        String sql = "UPDATE student SET (name, email, password_hash, updated_at) VALUES (?, ?, ?) WHERE student_number = ?";
        db.executeUpdate(sql,
                s.getName(),
                s.getEmail(),
                s.getPasswordHash(),
                LocalDateTime.now().toString(),
                s.getStudentNumber()
        );
    }

    @Override
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM student WHERE student_number = ?";
        db.executeUpdate(sql, id);
    }
}
