package pt.isec.server.threads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.db.DbCommands;
import pt.isec.server.replication.SqliteSnapshotManager;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSnapshotTransferIT {
    @TempDir
    Path directory;

    @Test
    void transfersAndInstallsValidatedSnapshotOverLoopbackTcp() throws Exception {
        Path primaryDatabase = createDatabase(directory.resolve("primary.db"), 11L);
        Path backupDatabase = directory.resolve("backup.db");
        TestServerContext primary = new TestServerContext(primaryDatabase, true);
        TestServerContext backup = new TestServerContext(backupDatabase, false);
        ClusterHeartbeatThread primaryProtocol = new ClusterHeartbeatThread(primary);
        ClusterHeartbeatThread backupProtocol = new ClusterHeartbeatThread(backup);

        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket listener = new ServerSocket(0, 1, loopback);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var serving = executor.submit(() -> {
                primaryProtocol.handleDbCopySession(listener.accept());
                return null;
            });
            backupProtocol.requestDbCopyFromPrimary(
                    loopback.getHostAddress(), listener.getLocalPort(), 11L);
            serving.get(5, TimeUnit.SECONDS);
        }

        assertEquals(11L, SqliteSnapshotManager.inspectDatabaseVersion(backupDatabase));
        assertTrue(SqliteSnapshotManager.hasValidIntegrity(backupDatabase));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + backupDatabase);
             var result = connection.createStatement().executeQuery("SELECT value FROM payload")) {
            assertTrue(result.next());
            assertEquals("replicated", result.getString(1));
        }
    }

    private static Path createDatabase(Path database, long version) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE config (id INTEGER PRIMARY KEY, db_version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO config(id, db_version) VALUES (1, " + version + ")");
            statement.execute("CREATE TABLE payload (value TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO payload(value) VALUES ('replicated')");
        }
        return database;
    }

    private static final class TestServerContext implements IServerThreadContext {
        private final Path database;
        private final boolean primary;

        private TestServerContext(Path database, boolean primary) {
            this.database = database;
            this.primary = primary;
        }

        @Override public String id() { return primary ? "primary" : "backup"; }
        @Override public String serverTcpIp() { return "127.0.0.1"; }
        @Override public int serverTcpPort() { return 0; }
        @Override public int dbCopyPort() { return 0; }
        @Override public String directoryHost() { return "127.0.0.1"; }
        @Override public int directoryPort() { return 0; }
        @Override public String multicastGroup() { return "230.30.30.30"; }
        @Override public int multicastPort() { return 3030; }
        @Override public NetworkInterface multicastInterface() { return null; }
        @Override public boolean isRunning() { return true; }
        @Override public void shutdownServer() { }
        @Override public boolean isPrimary() { return primary; }
        @Override public void setPrimary(String ip, int port) { }
        @Override public long dbVersion() { return SqliteSnapshotManager.inspectDatabaseVersion(database); }
        @Override public void setDbVersion(long version) { }
        @Override public Path dbPath() { return database; }
        @Override public void initDatabaseLayerIfNeeded() { }
        @Override public DbCommands getDb() { return null; }
        @Override public boolean tryLockCopy() { return true; }
        @Override public void unlockCopy() { }
        @Override public IAuthService getAuthService() { return null; }
        @Override public IQuestionService getQuestionService() { return null; }
        @Override public IAnswerService getAnswerService() { return null; }
        @Override public void registerClientConnection(String role, long userId, NetworkTcpConnection connection) { }
        @Override public void unregisterClientConnection(String role, long userId, NetworkTcpConnection connection) { }
    }
}
