package pt.isec.server.services.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.server.core.UserConnectionKey;
import pt.isec.server.db.DbCommands;
import pt.isec.server.db.DbCreate;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceIT {
    @TempDir
    Path tempDir;

    private DbCommands db;
    private AuthService auth;

    @BeforeEach
    void setUp() throws Exception {
        Path database = tempDir.resolve("sessions.db");
        DbCreate.createIfMissing(database, "/db/schema.sql");
        db = new DbCommands("jdbc:sqlite:" + database.toAbsolutePath());
        auth = new AuthService(db);
        String passwordHash = PasswordHasher.hash("Password1!");
        db.executeUpdate(
                "INSERT INTO teacher (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
                1, "Teacher One", "teacher@example.test", passwordHash
        );
        db.executeUpdate(
                "INSERT INTO student (id, student_number, name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                1, 3_000_000_000L, "Student One", "student@example.test", passwordHash
        );
    }

    @Test
    void roleIsPartOfTheActiveConnectionIdentity() {
        assertNotEquals(new UserConnectionKey("TEACHER", 1), new UserConnectionKey("STUDENT", 1));
        assertEquals(new UserConnectionKey("teacher", 1), new UserConnectionKey("TEACHER", 1));
    }

    @Test
    void loginCreatesOneResumableSessionWithTwentyFourHourExpiry() throws Exception {
        AuthResponseDTO login = auth.login(new LoginRequestDTO("teacher@example.test", "Password1!"));

        AuthResponseDTO resumed = auth.resumeSession(login.sessionId());
        assertEquals(login.userId(), resumed.userId());
        assertEquals("TEACHER", resumed.userType());
        assertEquals(1L, ((Number) db.selectOne(
                "SELECT COUNT(*) AS count FROM session WHERE session_id = ?",
                login.sessionId()
        ).get("count")).longValue());
        assertNotNull(db.selectOne(
                "SELECT 1 AS one FROM session WHERE session_id = ? " +
                        "AND expires_at >= datetime(created_at, '+23 hours', '+59 minutes') " +
                        "AND revoked_at IS NULL",
                login.sessionId()
        ));
    }

    @Test
    void logoutRevokesExistingRowAndResumeFails() throws Exception {
        AuthResponseDTO login = auth.login(new LoginRequestDTO("teacher@example.test", "Password1!"));

        auth.invalidateSession(1, login.sessionId(), "TEACHER");

        assertThrows(IllegalArgumentException.class, () -> auth.resumeSession(login.sessionId()));
        assertEquals(1L, ((Number) db.selectOne(
                "SELECT COUNT(*) AS count FROM session WHERE session_id = ?",
                login.sessionId()
        ).get("count")).longValue());
        assertNotNull(db.selectOne(
                "SELECT revoked_at FROM session WHERE session_id = ? AND revoked_at IS NOT NULL",
                login.sessionId()
        ));
    }

    @Test
    void expiredSessionCannotResume() throws Exception {
        AuthResponseDTO login = auth.login(new LoginRequestDTO("student@example.test", "Password1!"));
        db.executeUpdate(
                "UPDATE session SET expires_at = datetime('now','-1 minute') WHERE session_id = ?",
                login.sessionId()
        );

        assertThrows(IllegalArgumentException.class, () -> auth.resumeSession(login.sessionId()));
    }

    @Test
    void resumeLoadsCurrentProfileInsteadOfStaleSessionData() throws Exception {
        AuthResponseDTO login = auth.login(new LoginRequestDTO("teacher@example.test", "Password1!"));
        db.executeUpdate(
                "UPDATE teacher SET name = ?, email = ? WHERE id = ?",
                "Teacher Updated", "updated@example.test", 1
        );

        AuthResponseDTO resumed = auth.resumeSession(login.sessionId());

        assertEquals("Teacher Updated", resumed.name());
        assertEquals("updated@example.test", resumed.email());
    }

    @Test
    void legacyEventSessionsAreMigratedWithoutLosingRevocationState() throws Exception {
        Path legacyDatabase = tempDir.resolve("legacy-session.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + legacyDatabase.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE config (id INTEGER PRIMARY KEY, db_version INTEGER, teacher_code_hash TEXT)");
            statement.execute("INSERT INTO config VALUES (1, 0, 'hash')");
            statement.execute("CREATE TABLE session (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT, " +
                    "user_id INTEGER, operationType TEXT, role TEXT, name TEXT, email TEXT, " +
                    "created_at TEXT, last_seen_at TEXT, expires_at TEXT)");
            statement.execute("INSERT INTO session (session_id,user_id,operationType,role,name,email,created_at,expires_at) " +
                    "VALUES ('legacy',1,'Login','TEACHER','Old','old@example.test',datetime('now','-1 minute'),datetime('now','+1 day'))");
            statement.execute("INSERT INTO session (session_id,user_id,operationType,role,name,email,created_at,expires_at) " +
                    "VALUES ('legacy',1,'Logout','TEACHER','Old','old@example.test',datetime('now'),datetime('now','+1 day'))");
        }

        DbCreate.createIfMissing(legacyDatabase, "/db/schema.sql");
        DbCommands migrated = new DbCommands("jdbc:sqlite:" + legacyDatabase.toAbsolutePath());

        assertEquals(1L, ((Number) migrated.selectOne(
                "SELECT COUNT(*) AS count FROM session WHERE session_id = 'legacy'"
        ).get("count")).longValue());
        assertNotNull(migrated.selectOne(
                "SELECT revoked_at FROM session WHERE session_id = 'legacy' AND revoked_at IS NOT NULL"
        ));
    }

}
