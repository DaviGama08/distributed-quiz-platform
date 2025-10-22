package pt.isec.server.repositories;

import java.io.*;
import java.sql.*;

public class DatabaseManager {

    private String dbUrl;
    private static DatabaseManager instance = null;

    private DatabaseManager() { }

    public static DatabaseManager getInstance() {
        if (instance == null)
            instance = new DatabaseManager();
        return instance;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public Connection getConnection() throws SQLException {
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL not set.");
        }
        Connection conn = DriverManager.getConnection(dbUrl);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        return conn;
    }

    public boolean isInitialized() {
        if(dbUrl == null)
            return false;

        try(Connection conn = DriverManager.getConnection(dbUrl)){

            try (Statement s = conn.createStatement()) {
                ResultSet rs;
                rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='config';");
                if(!rs.next()){
                    return false;
                }
                rs = s.executeQuery("SELECT COUNT(*) FROM config WHERE id=1;");
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count > 0;
                }
            }

        }  catch (SQLException e) {
            return false;
        }
        return false;
    }

    public void initializeDatabase(String teacherCodeHash) throws SQLException, IOException {
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL not set.");
        }
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON;");
            }
            conn.setAutoCommit(false);
            try {
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
                String[] stmts = schemaSql.split(";(\\s*\\r?\\n|\\s*$)");
                try (Statement stmt = conn.createStatement()) {
                    for (String raw : stmts) {
                        String sql = raw.trim();
                        if (sql.isEmpty() || sql.startsWith("--"))
                            continue;
                        stmt.execute(sql);
                    }
                }
                boolean hasConfig = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM config WHERE id = 1")) {
                    if (rs.next())
                        hasConfig = true;
                }
                if (!hasConfig) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("INSERT INTO config (id, db_version, teacher_code_hash) " +
                                "VALUES (1, 0, '" + teacherCodeHash + "')");
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

    public String getTeacherCodeHash() {

        if (dbUrl == null)
            return null;

        try(Connection conn = DriverManager.getConnection(dbUrl)){

            try (Statement s = conn.createStatement()) {
                ResultSet rs;
                rs = s.executeQuery("SELECT teacher_code_hash FROM config WHERE id = 1;");
                if(rs.next()){
                    return rs.getString(1);
                }

                return null;
            }

        }  catch (SQLException e) {
           return null;
        }
    }

    public boolean updateTeacherCodeHash(String newHash){

        if (dbUrl == null)
            return false;

        String sql = "UPDATE config SET teacher_code_hash = ? WHERE id = 1;";

        try(Connection conn = DriverManager.getConnection(dbUrl) ;
            PreparedStatement stmt = conn.prepareStatement(sql)){

            stmt.setString(1, newHash);
            int rowsUpdated = stmt.executeUpdate();

            return rowsUpdated > 0;

        }  catch (SQLException e) {
            return false;
        }
    }

    public int getDBVersion(){
        if(dbUrl == null)
            return -1;

        try(Connection conn = DriverManager.getConnection(dbUrl)){

            try (Statement s = conn.createStatement()) {
                ResultSet rs;
                rs = s.executeQuery("SELECT db_version FROM config WHERE id = 1;");
                if(rs.next()){
                    return rs.getInt(1);
                }

                return -1;
            }

        }  catch (SQLException e) {
            return -1;
        }
    }

    public boolean setDbVersion(int new_version){

        if (dbUrl == null)
            return false;

        String sql = "UPDATE config SET db_version = ? WHERE id = 1;";

        try(Connection conn = DriverManager.getConnection(dbUrl) ;
            PreparedStatement stmt = conn.prepareStatement(sql)){

            stmt.setString(1, String.valueOf(new_version));
            int rowsUpdated = stmt.executeUpdate();

            return rowsUpdated > 0;

        }  catch (SQLException e) {
            return false;
        }
    }
}
