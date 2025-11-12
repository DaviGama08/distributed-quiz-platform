package pt.isec.server.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/** Cria o ficheiro .db se não existir (ou se estiver sem tabelas) e aplica o schema.sql do classpath. */
public final class DbFiles {
    private DbFiles() {}

    /**
     * @param dbPath caminho absoluto do ficheiro .db
     * @param schemaResourceOnClasspath caminho no classpath (ex.: "/db/schema.sql")
     */
    public static void createIfMissing(Path dbPath, String schemaResourceOnClasspath) throws Exception {
        Files.createDirectories(dbPath.getParent());

        boolean needSchema = !Files.exists(dbPath);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            if (!needSchema) {
                // Se já existe ficheiro, verifica se a tabela 'config' existe:
                try (ResultSet rs = c.getMetaData().getTables(null, null, "config", null)) {
                    needSchema = !rs.next();
                }
            }

            if (needSchema) {
                try (InputStream is = DbFiles.class.getResourceAsStream(schemaResourceOnClasspath)) {
                    if (is == null)
                        throw new IllegalStateException("Recurso não encontrado: " + schemaResourceOnClasspath);
                    runSqlScript(c, is);
                }
            }
        }
    }

    /** Executa o script SQL respeitando blocos CREATE TRIGGER ... BEGIN ... END; */
    private static void runSqlScript(Connection c, InputStream sqlStream) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(sqlStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            boolean inTrigger = false;

            try (Statement st = c.createStatement()) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    // ignora comentários '-- ...' e linhas vazias
                    if (trimmed.startsWith("--") || trimmed.isEmpty())
                        continue;

                    // detecta início de trigger (case-insensitive)
                    String upper = trimmed.toUpperCase();
                    if (!inTrigger && upper.startsWith("CREATE TRIGGER")) {
                        inTrigger = true;
                    }

                    sb.append(line).append('\n');

                    if (inTrigger) {
                        // dentro de trigger, só executa quando encontrar END;
                        if (upper.equals("END;") || upper.endsWith("\nEND;")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) st.execute(stmt);
                            sb.setLength(0);
                            inTrigger = false;
                        }
                    } else {
                        // instruções normais: executa quando terminar com ';'
                        if (trimmed.endsWith(";")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) st.execute(stmt);
                            sb.setLength(0);
                        }
                    }
                }

                // resto pendente (sem ; final)
                String leftover = sb.toString().trim();
                if (!leftover.isBlank()) {
                    st.execute(leftover);
                }
            }
        }
    }
}
