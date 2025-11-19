// FILE: src/main/java/pt/isec/server/repositories/Db.java
package pt.isec.server.db;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Db — camada fininha para SQLite via JDBC, com nomes claros e fluxo didático.
 *
 * API:
 *  - executeUpdate(sql, args...) → INSERT/UPDATE/DELETE/DDL (retorna nº de linhas)
 *  - selectOne(sql, args...)     → SELECT (0..1 linha) como Map<String,Object>
 *  - runInTransaction(work)      → transação com Transaction (executeUpdate/selectOne/getLastInsertId)
 */
public final class Db {

    /* ========================= 0) CONFIG ========================= */

    /** URL JDBC da BD, ex.: "jdbc:sqlite:/abs/path/quiz.db". */
    private final String url;

    /** Constrói o helper apontando para o ficheiro .db. */
    public Db(String url) { this.url = url; }

    /** Expor a URL quando precisarmos abrir uma Connection direta noutro ponto. */
    public String getUrl() { return url; }

    /* ========================= 1) OPERAÇÕES UNITÁRIAS ========================= */

    /** Executa INSERT/UPDATE/DELETE/DDL usando ligação própria. */
    public int executeUpdate(String sql, Object... args) {
        //Connection é a class para fazer a ligação à base de dados
        //PreparedStatement serve para executar comandos sql
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args); //bind está a
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Executa SELECT que retorna NO MÁXIMO uma linha. */
    public Map<String, Object> selectOne(String sql, Object... args) {
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /* ========================= 2) TRANSACÕES ========================= */

    public void runInTransaction(TransactionWork work) throws Exception {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try {
                work.run(new Transaction(c));
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public interface TransactionWork { void run(Transaction tx) throws Exception; }

    /** Contexto de transação (usa a MESMA Connection). */
    public static final class Transaction {
        private final Connection connection;
        private Transaction(Connection connection) { this.connection = connection; }

        public int executeUpdate(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        public Map<String, Object> selectOne(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return mapRow(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        /** Último ROWID gerado por um INSERT executado nesta MESMA Connection (SQLite). */
        public long getLastInsertId() {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid() AS id")) {
                return rs.next() ? rs.getLong("id") : -1L;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* ========================= 3) INTERNOS ========================= */

    /** Abre Connection e aplica PRAGMAs úteis para SQLite. */
    private Connection openConnection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    /**
     * Faz o "bind" (ligação) dos valores Java aos "?" da query SQL, em ordem.
     * Vantagens: evita SQL injection, melhora desempenho (prepare/plan reuso) e tipagem correta.
     */
    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /** Converte a linha atual do ResultSet num Map<String,Object> (coluna → valor). */
    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(n);
        for (int i = 1; i <= n; i++) {
            String name = md.getColumnLabel(i);
            if (name == null || name.isBlank()) name = md.getColumnName(i);
            row.put(name, rs.getObject(i));
        }
        return row;
    }
}
