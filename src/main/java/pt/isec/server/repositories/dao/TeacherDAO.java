package pt.isec.server.repositories.dao;

import pt.isec.common.model.user.Teacher;
import pt.isec.server.repositories.Db;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class TeacherDAO implements IUserDAO<Teacher> {
    private final Db db;

    public TeacherDAO(Db db) {
        this.db = db;
    }

    @Override
    public long add(Teacher t) throws SQLException {
        try {
            if (t.getId() > 0L) {
                // id já fornecido: insere com id explícito
                db.executeUpdate(
                        "INSERT INTO teacher (id, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, datetime('now'))",
                        t.getId(), t.getName(), t.getEmail(), t.getPasswordHash()
                );
                return t.getId();
            } else {
                // sem id: deixa o SQLite gerar e devolve o ROWID
                db.runInTransaction(tx -> {
                    tx.executeUpdate(
                            "INSERT INTO teacher (name, email, password_hash, created_at) VALUES (?, ?, ?, datetime('now'))",
                            t.getName(), t.getEmail(), t.getPasswordHash()
                    );
                    long id = tx.getLastInsertId();
                    // -> requeira que Teacher tenha setId(long)
                    t.setId(id);
                });
                return t.getId();
            }
        } catch (Exception e) {
            throw new SQLException("Erro ao inserir teacher", e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        Map<String,Object> r = db.selectOne(
                "SELECT 1 AS one FROM teacher WHERE email = ? LIMIT 1",
                email
        );
        return r != null;
    }

    @Override
    public Optional<Teacher> findById(long id) throws SQLException {
        Map<String,Object> r = db.selectOne(
                "SELECT id, name, email, password_hash FROM teacher WHERE id = ?",
                id
        );
        if (r == null) return Optional.empty();
        Teacher t = new Teacher(
                ((Number) r.get("id")).longValue(),
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash")
        );
        return Optional.of(t);
    }

    @Override
    public Optional<Teacher> findByEmail(String email) {
        Map<String,Object> r = db.selectOne(
                "SELECT id, name, email, password_hash FROM teacher WHERE email = ? LIMIT 1",
                email
        );
        if (r == null) return Optional.empty();
        Teacher t = new Teacher(
                ((Number) r.get("id")).longValue(),
                (String) r.get("name"),
                (String) r.get("email"),
                (String) r.get("password_hash")
        );
        return Optional.of(t);
    }

    @Override
    public List<Teacher> findAll() throws SQLException {
        // Implementação simples sem reflection: abre connection localmente
        try (var c = java.sql.DriverManager.getConnection(getUrlFromDb());
             var ps = c.prepareStatement(
                     "SELECT id, name, email, password_hash FROM teacher ORDER BY name");
             var rs = ps.executeQuery()) {

            List<Teacher> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Teacher(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                ));
            }
            return out;
        }
    }

    @Override
    public void update(Teacher t) throws SQLException {
        db.executeUpdate(
                "UPDATE teacher SET name = ?, email = ?, password_hash = ?, updated_at = ? WHERE id = ?",
                t.getName(), t.getEmail(), t.getPasswordHash(),
                LocalDateTime.now().toString(),
                t.getId()
        );
    }

    @Override
    public void delete(long id) throws SQLException {
        db.executeUpdate("DELETE FROM teacher WHERE id = ?", id);
    }

    // Pega a URL da Db sem reflection: adicione um getter público em Db (recomendado).
    private String getUrlFromDb() {
        // RECOMENDADO: crie public String getUrl() na classe Db e retorne aqui db.getUrl();
        // Se não puder, mantenha seu helper por enquanto.
        try {
            var f = Db.class.getDeclaredField("url");
            f.setAccessible(true);
            return (String) f.get(db);
        } catch (Exception e) {
            throw new RuntimeException("Adicione Db#getUrl() para evitar reflection", e);
        }
    }
}
