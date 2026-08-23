package pt.isec.server.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class that creates the SQLite {@code .db} file if it does not exist
 * (or if it has no tables) and applies the {@code schema.sql} script from the classpath.
 */
public final class DbCreate {
    private DbCreate() {}

    /**
     * Ensures that the database file exists and that the schema is applied.
     * <p>
     * If the file is missing or does not contain the {@code config} table,
     * the given SQL script is executed.
     *
     * @param dbPath                   absolute path to the {@code .db} file
     * @param schemaResourceOnClasspath classpath resource path (e.g. {@code "/db/schema.sql"})
     * @throws Exception if database or I/O errors occur
     */
    public static void createIfMissing(Path dbPath, String schemaResourceOnClasspath) throws Exception {
        Files.createDirectories(dbPath.getParent());

        boolean needSchema = !Files.exists(dbPath);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            if (!needSchema) {
                // If the file exists, check if table 'config' exists
                try (ResultSet rs = c.getMetaData().getTables(null, null, "config", null)) {
                    needSchema = !rs.next();
                }
            }

            if (needSchema) {
                try (InputStream is = DbCreate.class.getResourceAsStream(schemaResourceOnClasspath)) {
                    if (is == null) {
                        throw new IllegalStateException("Resource not found: " + schemaResourceOnClasspath);
                    }
                    runSqlScript(c, is);
                }
            }

            migrateLegacySessionTable(c);
        }
    }

    /** Preserves legacy session state while upgrading the old event-log table in place. */
    private static void migrateLegacySessionTable(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(session)")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }

        if (columns.isEmpty() || columns.contains("revoked_at")) {
            return;
        }

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS session_migrated");
            statement.execute("""
                    CREATE TABLE session_migrated (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL UNIQUE,
                        user_id INTEGER NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('TEACHER','STUDENT')),
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        last_seen_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        expires_at TEXT NOT NULL,
                        revoked_at TEXT
                    )
                    """);
            statement.execute("""
                    INSERT INTO session_migrated
                        (session_id, user_id, role, created_at, last_seen_at, expires_at, revoked_at)
                    SELECT latest.session_id,
                           latest.user_id,
                           latest.role,
                           COALESCE(first_seen.created_at, datetime('now')),
                           COALESCE(latest.last_seen_at, latest.created_at, datetime('now')),
                           COALESCE(latest.expires_at, datetime('now','+1 day')),
                           CASE WHEN latest.operationType = 'Logout'
                                THEN COALESCE(latest.created_at, datetime('now'))
                                ELSE NULL END
                    FROM session latest
                    JOIN (
                        SELECT session_id, MAX(id) AS latest_id, MIN(created_at) AS created_at
                        FROM session
                        GROUP BY session_id
                    ) first_seen ON first_seen.latest_id = latest.id
                    """);
            statement.execute("DROP TABLE session");
            statement.execute("ALTER TABLE session_migrated RENAME TO session");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_session_user_id ON session(user_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_session_session_id ON session(session_id)");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Executes the SQL script, handling {@code CREATE TRIGGER ... BEGIN ... END;} blocks correctly.
     *
     * @param c          open database connection
     * @param sqlStream  input stream with SQL text
     * @throws Exception if reading or executing SQL fails
     */
    private static void runSqlScript(Connection c, InputStream sqlStream) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(sqlStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            boolean inTrigger = false;

            try (Statement st = c.createStatement()) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    // ignore '-- ...' comments and empty lines
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                        continue;
                    }

                    // detect trigger start (case-insensitive)
                    String upper = trimmed.toUpperCase();
                    if (!inTrigger && upper.startsWith("CREATE TRIGGER")) {
                        inTrigger = true;
                    }

                    sb.append(line).append('\n');

                    if (inTrigger) {
                        // inside trigger: only execute when END; is found
                        if (upper.equals("END;") || upper.endsWith("\nEND;")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) {
                                st.execute(stmt);
                            }
                            sb.setLength(0);
                            inTrigger = false;
                        }
                    } else {
                        // normal statements: execute when ending with ';'
                        if (trimmed.endsWith(";")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) {
                                st.execute(stmt);
                            }
                            sb.setLength(0);
                        }
                    }
                }

                // leftover without final ';'
                String leftover = sb.toString().trim();
                if (!leftover.isBlank()) {
                    st.execute(leftover);
                }
            }
        }
    }
}
