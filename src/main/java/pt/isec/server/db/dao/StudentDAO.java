package pt.isec.server.db.dao;

import pt.isec.common.model.user.Student;
import pt.isec.server.db.Db;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class StudentDAO implements IUserDAO<Student> {
    private final Db db;

    public StudentDAO(Db db) {
        this.db = db;
    }

    @Override
    public long add(Student s) throws SQLException {
        try {
            if (s.getStudentNumber() > 0) {
                // PK fornecida
                db.executeUpdate(
                        "INSERT INTO student (student_number, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, datetime('now'))",
                        s.getStudentNumber(), s.getName(), s.getEmail(), s.getPasswordHash()
                );
                return s.getStudentNumber();
            } else {
                // Caso raro: se student_number for autogerado (não comum). Usa ROWID.
                db.runInTransaction(tx -> {
                    tx.executeUpdate(
                            "INSERT INTO student (name, email, password_hash, created_at) VALUES (?, ?, ?, datetime('now'))",
                            s.getName(), s.getEmail(), s.getPasswordHash()
                    );
                    long id = tx.getLastInsertId();
                    s.setStudentNumber(id);
                });
                return s.getStudentNumber();
            }
        } catch (Exception e) {
            throw new SQLException("Erro ao inserir student", e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        Map<String,Object> r = db.selectOne(
                "SELECT 1 AS one FROM student WHERE email = ? LIMIT 1",
                email
        );
        return r != null;
    }

    @Override
    public Optional<Student> findById(long id) throws SQLException {
        Map<String,Object> r = db.selectOne(
                "SELECT student_number, name, email, password_hash FROM student WHERE student_number = ?",
                id
        );
        if (r == null) return Optional.empty();
        Student s = new Student(
                ((Number)r.get("student_number")).longValue(),
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash")
        );
        return Optional.of(s);
    }

    @Override
    public Optional<Student> findByEmail(String email) {
        Map<String,Object> r = db.selectOne(
                "SELECT student_number, name, email, password_hash FROM student WHERE email = ? LIMIT 1",
                email
        );
        if (r == null) return Optional.empty();
        Student s = new Student(
                ((Number)r.get("student_number")).longValue(),
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash")
        );
        return Optional.of(s);
    }

    @Override
    public List<Student> findAll() throws SQLException {
        try (var c = java.sql.DriverManager.getConnection(extractUrlFromDb());
             var ps = c.prepareStatement("SELECT student_number, name, email, password_hash FROM student ORDER BY name");
             var rs = ps.executeQuery()) {

            List<Student> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Student(
                        rs.getLong("student_number"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                ));
            }
            return out;
        }
    }

    @Override
    public void update(Student s) throws SQLException {
        db.executeUpdate(
                "UPDATE student SET name = ?, email = ?, password_hash = ?, updated_at = ? WHERE student_number = ?",
                s.getName(), s.getEmail(), s.getPasswordHash(),
                LocalDateTime.now().toString(),
                s.getStudentNumber()
        );
    }

    @Override
    public void delete(long id) throws SQLException {
        db.executeUpdate("DELETE FROM student WHERE student_number = ?", id);
    }

    private String extractUrlFromDb() {
        try {
            var f = Db.class.getDeclaredField("url");
            f.setAccessible(true);
            return (String) f.get(db);
        } catch (Exception e) {
            throw new RuntimeException("Não consegui obter a URL da Db; adicione um getter público", e);
        }
    }
}
