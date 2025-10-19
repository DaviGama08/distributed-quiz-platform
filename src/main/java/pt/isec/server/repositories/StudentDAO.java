package pt.isec.server.repositories;

import pt.isec.common.model.user.Student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class StudentDAO implements IUserDAO<Student>{
    //classe para aceder a dados do estudante na base de dados
    private final SQLiteConnectionFactory factory;

    public StudentDAO(SQLiteConnectionFactory factory){
        this.factory = factory;
    }
    @Override
    public long add(Student user) throws SQLException {
        return 0;
    }

    @Override
    public Optional findById(String id) throws SQLException {
        return Optional.empty();
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
    public void update(Student user) throws SQLException {

    }


    @Override
    public void delete(String id) throws SQLException {

    }

}
