package pt.isec.server.replication;

import pt.isec.common.dto.cluster.DatabaseSnapshotMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Creates, validates and atomically installs consistent SQLite snapshots. */
public final class SqliteSnapshotManager {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private SqliteSnapshotManager() {
    }

    public static SnapshotFile createConsistentSnapshot(Path liveDatabase) throws Exception {
        Path source = liveDatabase.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IOException("Database does not exist: " + source);
        }

        Path parent = source.getParent();
        Path snapshot = Files.createTempFile(parent, source.getFileName() + ".snapshot-", ".db");
        Files.delete(snapshot); // VACUUM INTO requires a target that does not exist.

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, snapshot.toString());
            statement.executeUpdate();
        } catch (Exception e) {
            Files.deleteIfExists(snapshot);
            throw e;
        }

        try {
            long version = inspectDatabaseVersion(snapshot);
            if (version < 0 || !hasValidIntegrity(snapshot)) {
                throw new IOException("Generated SQLite snapshot is invalid");
            }
            DatabaseSnapshotMetadata metadata = new DatabaseSnapshotMetadata(
                    Files.size(snapshot), sha256(snapshot), version);
            return new SnapshotFile(snapshot, metadata);
        } catch (Exception e) {
            Files.deleteIfExists(snapshot);
            throw e;
        }
    }

    public static void validateSnapshot(Path snapshot, DatabaseSnapshotMetadata expected) throws Exception {
        if (!Files.isRegularFile(snapshot) || Files.size(snapshot) != expected.size()) {
            throw new IOException("Snapshot size does not match metadata");
        }
        if (!MessageDigest.isEqual(sha256(snapshot), expected.sha256())) {
            throw new IOException("Snapshot SHA-256 does not match metadata");
        }
        if (!hasValidIntegrity(snapshot)) {
            throw new IOException("SQLite integrity_check failed");
        }
        long actualVersion = inspectDatabaseVersion(snapshot);
        if (actualVersion != expected.dbVersion()) {
            throw new IOException("Snapshot version does not match metadata");
        }
    }

    public static void replaceAtomically(Path receivedSnapshot, Path targetDatabase) throws IOException {
        Path target = targetDatabase.toAbsolutePath().normalize();
        Path received = receivedSnapshot.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target.resolveSibling(target.getFileName() + "-wal"));
        Files.deleteIfExists(target.resolveSibling(target.getFileName() + "-shm"));
        try {
            Files.move(received, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(received, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static long inspectDatabaseVersion(Path database) {
        String url = "jdbc:sqlite:" + database.toAbsolutePath().normalize();
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT db_version FROM config WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : -1L;
        } catch (SQLException e) {
            return -1L;
        }
    }

    public static boolean hasValidIntegrity(Path database) {
        String url = "jdbc:sqlite:" + database.toAbsolutePath().normalize();
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement("PRAGMA integrity_check");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && "ok".equalsIgnoreCase(resultSet.getString(1));
        } catch (SQLException e) {
            return false;
        }
    }

    public static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[HASH_BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record SnapshotFile(Path path, DatabaseSnapshotMetadata metadata) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
