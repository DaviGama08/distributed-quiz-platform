package pt.isec.server.repositories;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteConnectionFactory {
    private final String dbUrl;

    public SQLiteConnectionFactory(String dbPath){
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(dbUrl);
        try(Statement st = c.createStatement()){
            st.execute("PRAGMA foreign_keys = ON;"); // ?
            st.execute("PRAGMA journal_mode = WAL;"); // ?
            st.execute("PRAGMA busy_timeout = 5000;"); // ?
        }
        return c;
    }

}
