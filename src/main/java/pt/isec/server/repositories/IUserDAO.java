package pt.isec.server.repositories;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface IUserDAO<T> {
    long add(T user) throws SQLException;
    Optional<T> findById(String id) throws SQLException;
    boolean existsByEmail(String email);
    List<T> findAll() throws SQLException;
    void update(T user) throws SQLException;
    void delete(String id) throws SQLException;
}
