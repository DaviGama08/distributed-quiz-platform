package pt.isec.server.repositories;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface IUserDAO<T> {
    long add(T user) throws SQLException;
    Optional<T> findById(long id) throws SQLException;
    List<T> findAll() throws SQLException;
    void update(T user) throws SQLException;
    void delete(long id) throws SQLException;
}
