package pt.isec.server.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbCommandsIT {
    @TempDir
    Path tempDir;

    private Path database;
    private DbCommands db;

    @BeforeEach
    void setUp() throws Exception {
        database = tempDir.resolve("transactions.db");
        DbCreate.createIfMissing(database, "/db/schema.sql");
        db = new DbCommands("jdbc:sqlite:" + database.toAbsolutePath());
    }

    @Test
    void executeUpdateCommitsMutationAndVersionTogether() {
        long before = db.getDbVersion();

        db.executeUpdate(
                "INSERT INTO teacher (name, email, password_hash) VALUES (?, ?, ?)",
                "Teacher", "teacher@example.test", "hash"
        );

        assertEquals(before + 1, db.getDbVersion());
        assertEquals("Teacher", db.selectOne(
                "SELECT name FROM teacher WHERE email = ?", "teacher@example.test"
        ).get("name"));
    }

    @Test
    void transactionAdvancesVersionOnlyWhenDirty() throws Exception {
        long before = db.getDbVersion();
        db.runInTransaction(tx -> tx.selectOne("SELECT id FROM config WHERE id = 1"));
        assertEquals(before, db.getDbVersion());

        db.runInTransaction(tx -> tx.executeUpdate(
                "UPDATE teacher SET name = ? WHERE id = ?", "Nobody", -1
        ));
        assertEquals(before, db.getDbVersion());

        db.runInTransaction(tx -> {
            tx.executeUpdate(
                    "INSERT INTO teacher (name, email, password_hash) VALUES (?, ?, ?)",
                    "One", "one@example.test", "hash"
            );
            tx.executeUpdate(
                    "INSERT INTO teacher (name, email, password_hash) VALUES (?, ?, ?)",
                    "Two", "two@example.test", "hash"
            );
        });
        assertEquals(before + 1, db.getDbVersion());
    }

    @Test
    void transactionRollsBackBusinessDataAndVersionOnFailure() {
        long before = db.getDbVersion();

        assertThrows(Exception.class, () -> db.runInTransaction(tx -> {
            tx.executeUpdate(
                    "INSERT INTO teacher (name, email, password_hash) VALUES (?, ?, ?)",
                    "Rollback", "rollback@example.test", "hash"
            );
            tx.executeUpdate("INSERT INTO table_that_does_not_exist VALUES (1)");
        }));

        assertEquals(before, db.getDbVersion());
        assertNull(db.selectOne("SELECT id FROM teacher WHERE email = ?", "rollback@example.test"));
    }

    @Test
    void missingVersionRowRollsBackSingleMutation() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM config WHERE id = 1");
        }

        assertThrows(RuntimeException.class, () -> db.executeUpdate(
                "INSERT INTO teacher (name, email, password_hash) VALUES (?, ?, ?)",
                "Atomic", "atomic@example.test", "hash"
        ));
        assertNull(db.selectOne("SELECT id FROM teacher WHERE email = ?", "atomic@example.test"));
    }
}
