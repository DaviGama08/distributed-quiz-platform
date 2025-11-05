package pt.isec.server.repositories;

import pt.isec.common.model.user.Teacher;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class TeacherDAO implements IUserDAO<Teacher> {
    //classe para aceder a dados do docente na base de dadosxx
    private final DatabaseManager db;

    public TeacherDAO(){
        db = DatabaseManager.getInstance();
    }

    @Override
    public long add(Teacher t) throws SQLException {
        String sql = "INSERT INTO teacher (id, name, email, password_hash) VALUES (?, ?, ?, ?)";
        return db.executeUpdate(sql,
                t.getId(),
                t.getName(),
                t.getEmail(),
                t.getPasswordHash()
        );
    }

    @Override
    public Optional<Teacher> findById(long id) throws SQLException {
        String sql = "SELECT id, name, email, password_hash FROM teacher WHERE id = ?";
        Teacher aux = db.queryForObject(sql, rs -> new Teacher(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash")
        ), id);

        if(aux == null)
            return Optional.empty();
        return Optional.of(aux);
    }

    @Override
    public List<Teacher> findAll() throws SQLException {
        String sql = "SELECT id, name, email, password_hash FROM teacher ORDER BY name";
        return db.queryList(sql, rs ->  new Teacher(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"))
        );
    }

    @Override
    public void update(Teacher t) throws SQLException {
        String sql = "UPDATE teacher SET (name, email, password_hash, updated_at) VALUES (?, ?, ?) WHERE id = ?";
        db.executeUpdate(sql,
                t.getName(),
                t.getEmail(),
                t.getPasswordHash(),
                LocalDateTime.now().toString(),
                t.getId()
        );
    }

    @Override
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM teacher WHERE id = ?";
        db.executeUpdate(sql, id);
    }
}
