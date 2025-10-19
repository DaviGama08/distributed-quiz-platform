package pt.isec.server.repositories;

import pt.isec.common.model.user.Teacher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TeacherDAO implements IUserDAO<Teacher>{
    //classe para aceder a dados do docente na base de dadosxx
    private final SQLiteConnectionFactory factory;

    public TeacherDAO(SQLiteConnectionFactory factory){
        this.factory = factory;
    }

    @Override
    public long add(Teacher user) throws SQLException {
        final String sql = "INSERT INTO Teacher (name, email, password_hash) VALUES (?,?,?)";
        try(Connection c = factory.getConnection();
            PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)){
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            return 0L;
        }
    }

    @Override
    public Optional findById(String id) throws SQLException {
        return Optional.of(true);
        //return Optional.empty();
    }
    @Override
    public boolean existsByEmail(String email){
        return true;
    }
    @Override
    public List findAll() throws SQLException {
        return List.of();
    }

    @Override
    public void update(Teacher user) throws SQLException {

    }

    @Override
    public void delete(String id) throws SQLException {

    }
}
