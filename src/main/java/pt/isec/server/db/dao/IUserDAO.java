package pt.isec.server.db.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface IUserDAO<T> {
    long add(T user) throws SQLException;

    Optional<T> findById(long id) throws SQLException;
    Optional<T> findByEmail(String email) throws SQLException;

    boolean existsByEmail(String email) throws SQLException;

    List<T> findAll() throws SQLException;
    void update(T user) throws SQLException;

    void delete(long id) throws SQLException;
}
