package pt.isec.server.db.dao;

import pt.isec.server.model.user.Student;
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
                        "INSERT INTO student (student_number, name, email, password_hash, created_at) " +
                                "VALUES (?, ?, ?, ?, datetime('now'))",
                        s.getStudentNumber(), s.getName(), s.getEmail(), s.getPasswordHash()
                );
                return s.getStudentNumber();
            } else {
                final long[] newNr = { -1L };
                db.runInTransaction(tx -> {
                    tx.executeUpdate(
                            "INSERT INTO student (name, email, password_hash, created_at) " +
                                    "VALUES (?, ?, ?, datetime('now'))",
                            s.getName(), s.getEmail(), s.getPasswordHash()
                    );
                    newNr[0] = tx.getLastInsertId();    // rowid == student_number
                    s.setStudentNumber((int) newNr[0]); // setter aceita Integer
                });
                return newNr[0];
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
                "SELECT student_number, name, email, password_hash, created_at FROM student WHERE student_number = ?",
                id
        );
        if (r == null) return Optional.empty();

        long studentNumber = ((Number) r.get("student_number")).longValue();
        LocalDateTime createdAt = parseTs((String) r.get("created_at"));

        // usamos student_number também como 'id' do User
        Student s = new Student(
                studentNumber,                        // id (User)
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash"),
                studentNumber,                        // studentNumber
                createdAt
        );
        return Optional.of(s);
    }

    @Override
    public Optional<Student> findByEmail(String email) throws SQLException {
        Map<String,Object> r = db.selectOne(
                "SELECT student_number, name, email, password_hash, created_at FROM student WHERE email = ? LIMIT 1",
                email
        );
        if (r == null) return Optional.empty();

        long studentNumber = ((Number) r.get("student_number")).longValue();
        LocalDateTime createdAt = parseTs((String) r.get("created_at"));

        Student s = new Student(
                studentNumber,                        // id (User)
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash"),
                studentNumber,                        // studentNumber
                createdAt
        );
        return Optional.of(s);
    }

    @Override
    public List<Student> findAll() throws SQLException {
        try (var c = java.sql.DriverManager.getConnection(extractUrlFromDb());
             @SuppressWarnings({"SqlResolve","SqlNoDataSourceInspection"})
             var ps = c.prepareStatement(
                     "SELECT student_number, name, email, password_hash, created_at FROM student ORDER BY name");
             var rs = ps.executeQuery()) {

            List<Student> out = new ArrayList<>();
            while (rs.next()) {
                long studentNumber = rs.getLong("student_number");
                LocalDateTime createdAt = parseTs(rs.getString("created_at"));

                out.add(new Student(
                        studentNumber,                  // id (User)
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        studentNumber,                  // studentNumber
                        createdAt
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

    private static LocalDateTime parseTs(String s) {
        if (s == null || s.isBlank()) return null;
        var F = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try { return LocalDateTime.parse(s.replace('T',' '), F); }
        catch (Exception ignore) { return LocalDateTime.parse(s); }
    }
}
