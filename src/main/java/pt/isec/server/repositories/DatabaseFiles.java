// FILE: src/main/java/pt/isec/server/app/DatabaseFiles.java
package pt.isec.server.app;

import java.io.InputStream;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** Cria o ficheiro .db se não existir e aplica o schema.sql do classpath. */
public final class DatabaseFiles {
    private DatabaseFiles() {}

    /**
     * @param dbPath caminho absoluto do ficheiro .db
     * @param schemaResourceOnClasspath caminho no classpath (ex.: "/db/schema.sql")
     */
    public static void createIfMissing(Path dbPath, String schemaResourceOnClasspath) throws Exception {
        Files.createDirectories(dbPath.getParent());
        if (Files.exists(dbPath)) return;

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = c.createStatement();
             InputStream is = DatabaseFiles.class.getResourceAsStream(schemaResourceOnClasspath)) {

            if (is == null) throw new IllegalStateException("Recurso não encontrado: " + schemaResourceOnClasspath);
            String sql = new String(is.readAllBytes());
            st.executeUpdate(sql);
        }
    }
}
