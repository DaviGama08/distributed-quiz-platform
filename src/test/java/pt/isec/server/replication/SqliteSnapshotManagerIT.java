package pt.isec.server.replication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.isec.common.dto.cluster.DatabaseSnapshotMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSnapshotManagerIT {
    @TempDir
    Path directory;

    @Test
    void snapshotIncludesCommittedWalDataAndCanBeInstalled() throws Exception {
        Path live = createDatabase("live.db", 7L);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA wal_autocheckpoint=0");
            statement.executeUpdate("INSERT INTO payload(value) VALUES ('committed-in-wal')");

            try (SqliteSnapshotManager.SnapshotFile snapshot =
                         SqliteSnapshotManager.createConsistentSnapshot(live)) {
                SqliteSnapshotManager.validateSnapshot(snapshot.path(), snapshot.metadata());
                assertEquals(7L, snapshot.metadata().dbVersion());
                assertTrue(snapshot.metadata().size() > 0);

                Path target = directory.resolve("installed.db");
                Path received = directory.resolve("received.db");
                Files.copy(snapshot.path(), received);
                SqliteSnapshotManager.replaceAtomically(received, target);
                assertEquals(7L, SqliteSnapshotManager.inspectDatabaseVersion(target));
                try (var installed = DriverManager.getConnection("jdbc:sqlite:" + target);
                     var query = installed.createStatement().executeQuery("SELECT COUNT(*) FROM payload")) {
                    assertTrue(query.next());
                    assertEquals(1, query.getInt(1));
                }
            }
        }
    }

    @Test
    void checksumMismatchRejectsSnapshot() throws Exception {
        Path live = createDatabase("source.db", 3L);
        try (SqliteSnapshotManager.SnapshotFile snapshot =
                     SqliteSnapshotManager.createConsistentSnapshot(live)) {
            byte[] wrongHash = snapshot.metadata().sha256();
            wrongHash[0] ^= 1;
            DatabaseSnapshotMetadata tampered = new DatabaseSnapshotMetadata(
                    snapshot.metadata().size(), wrongHash, snapshot.metadata().dbVersion());
            assertThrows(Exception.class,
                    () -> SqliteSnapshotManager.validateSnapshot(snapshot.path(), tampered));
        }
    }

    @Test
    void startupSelectionUsesHighestValidVersionNotModificationTime() throws Exception {
        Path stale = createDatabase("stale.db", 2L);
        Path fresh = createDatabase("fresh.db", 9L);
        Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        Files.setLastModifiedTime(fresh, FileTime.fromMillis(System.currentTimeMillis() - 60_000));
        Files.writeString(directory.resolve("broken.db"), "not sqlite");

        assertEquals(fresh, SqliteDatabaseSelector.selectBest(directory));
    }

    private Path createDatabase(String name, long version) throws Exception {
        Path database = directory.resolve(name);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE config (id INTEGER PRIMARY KEY, db_version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO config(id, db_version) VALUES (1, " + version + ")");
            statement.execute("CREATE TABLE payload (value TEXT NOT NULL)");
        }
        return database;
    }
}
