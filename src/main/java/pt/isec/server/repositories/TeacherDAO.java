package pt.isec.server.repositories;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TeacherDAO implements IUserDAO{
    //classe para aceder a dados do docente na base de dadosxx

    @Override
    public long add(Object user) throws SQLException {
        return 0;
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
    public void update(Object user) throws SQLException {

    }

    @Override
    public void delete(String id) throws SQLException {

    }
}
