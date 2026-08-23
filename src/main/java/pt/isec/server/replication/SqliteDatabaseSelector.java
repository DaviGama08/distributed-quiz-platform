package pt.isec.server.replication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Selects a startup database by validity and logical version, never by file timestamp. */
public final class SqliteDatabaseSelector {
    private SqliteDatabaseSelector() {
    }

    public static Path selectBest(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".db"))
                    .filter(Files::isRegularFile)
                    .filter(SqliteSnapshotManager::hasValidIntegrity)
                    .filter(path -> SqliteSnapshotManager.inspectDatabaseVersion(path) >= 0)
                    .max(Comparator
                            .comparingLong(SqliteSnapshotManager::inspectDatabaseVersion)
                            .thenComparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .orElse(null);
        }
    }
}
