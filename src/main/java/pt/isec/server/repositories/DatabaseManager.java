package pt.isec.server.repositories;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private String dbUrl;
    private static DatabaseManager instance = null;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null)
            instance = new DatabaseManager();
        return instance;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    //Helper: Retorna o Connection
    public Connection getConnection() throws SQLException {
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL not set.");
        }

        Connection conn = DriverManager.getConnection(dbUrl);

        try (Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }

        return conn;
    }

    //Funcao para saber se a bd já foi inicializada
    public boolean isInitialized() {

        try (Connection conn = getConnection()) {

            try (Statement s = conn.createStatement()) {
                //Querys de test
                try (ResultSet rs = s.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='config'")) {
                    if (!rs.next()) return false;
                }

                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM config WHERE id=1")) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            return false;
        }
    }

    //Initcializa a bd
    public void initializeDatabase(String teacherCodeHash) throws SQLException, IOException {
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL not set.");
        }
        try (Connection conn = getConnection()) {

            conn.setAutoCommit(false);
            try {
                //Pasa o schema.sql para string
                String schemaSql;
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql")) {
                    if (is == null)
                        throw new IOException("schema.sql not found in resources/db/");
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        schemaSql = sb.toString();
                    }
                }

                //Limpa a string em Statements Individuias (Cada table numa unica string)
                String[] stmts = schemaSql.split(";(\\s*\\r?\\n|\\s*$)");
                try (Statement stmt = conn.createStatement()) {
                    for (String raw : stmts) {
                        String sql = raw.trim();
                        if (sql.isEmpty() || sql.startsWith("--"))
                            continue;
                        stmt.execute(sql);
                    }
                }
                //Ve se a tabela config ja esta configurada (se ja estava criada)
                boolean hasConfig = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM config WHERE id = 1")) {
                    if (rs.next())
                        hasConfig = true;
                }
                //Se nao existem valores, insere
                if (!hasConfig) {

                    String insertSql = "INSERT INTO config (id, db_version, teacher_code_hash) VALUES (1, 0, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, teacherCodeHash);
                        pstmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException | IOException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    //Verifica se existe alguma bd na diretoria enviada
    public boolean databaseExistsInDir(String check_dir){

        File dir = new File(check_dir);

        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        //Verifica se ha algum ficheiro acabado em .db e qual foi o ultimo a ser modificado
        File latest = null;
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".db")) {
                if (latest == null || f.lastModified() > latest.lastModified())
                    latest = f;
            }
        }

        if (latest == null) {
            return false;
        }

        this.dbUrl = "jdbc:sqlite:" + latest.getAbsolutePath();
        return true;
    }

    public Path getCurrentDbFile() {
        if (dbUrl == null || !dbUrl.startsWith("jdbc:sqlite:"))
            throw new IllegalStateException("Database URL not set or invalid.");

        return Path.of(dbUrl.substring("jdbc:sqlite:".length()));
    }

    //Helper para receber info da bd e criar as respeticvas classes
    @FunctionalInterface
    interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    //Retorna uma List
    <T> List<T> queryList(String sql, ResultSetMapper<T> mapper, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }
         catch (SQLException e) {
            return null;
        }
    }

    //Retorna multiplas colunas
    <T> T queryForObject(String sql, ResultSetMapper<T> mapper, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapper.map(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }

    }
        //Retorna valor unico
    <T> T queryForSingleValue(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Object value = rs.getObject(1);
                return (T) value;
            }

        } catch (SQLException e) {
            return null;
        }
    }

    //Helper para guardar com o tipo certo na bd
    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int idx = i + 1;
            if (p == null) {
                ps.setObject(idx, null);
            } else if (p instanceof Integer v) {
                ps.setInt(idx, v);
            } else if (p instanceof Long v) {
                ps.setLong(idx, v);
            } else if (p instanceof String v) {
                ps.setString(idx, v);
            } else {
                ps.setObject(idx, p);
            }
        }
    }

    //Helper para atualizar a versao da bd
    private void updateDbVersion() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("UPDATE config SET db_version = db_version + 1 WHERE id = 1;");

        } catch (SQLException e) {
            System.err.println("Failed to update DB version: " + e.getMessage());
        }
    }

    //Faz alterações nas tabelas
    int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);
            int res = ps.executeUpdate();

            if(res > 0){
                updateDbVersion();
            }

            return res;

        } catch (SQLException e) {
            return -1;
        }
    }

    public String getTeacherCodeHash() {
        return queryForSingleValue("SELECT teacher_code_hash FROM config WHERE id = 1;");
    }


    public boolean updateTeacherCodeHash(String newHash) {
        int rows = executeUpdate("UPDATE config SET teacher_code_hash = ? WHERE id = 1;", newHash);
        return rows > 0;
    }

    public int getDBVersion() {
        Integer v = queryForSingleValue("SELECT db_version FROM config WHERE id = 1;");
        return v != null ? v : -1;
    }

    public boolean setDbVersion(int newVersion) {
        String sql = "UPDATE config SET db_version = ? WHERE id = 1;";
        int rows = executeUpdate(sql, newVersion);
        return rows > 0;
    }

}