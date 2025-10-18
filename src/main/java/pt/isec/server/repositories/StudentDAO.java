package pt.isec.server.repositories;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class StudentDAO implements IUserDAO{
    //classe para aceder a dados do estudante na base de dados

    @Override
    public long add(Object user) throws SQLException {
        return 0;
    }

    @Override
    public Optional findById(String id) throws SQLException {
        return Optional.empty();
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
