// src/main/java/pt/isec/server/repositories/SchemaCreator.java
package pt.isec.server.repositories;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class SchemaCreator {
    private final SQLiteConnectionFactory factory;

    public SchemaCreator(SQLiteConnectionFactory factory) {
        this.factory = factory;
    }

    /** Executa o schema a partir de resources/schema.sql */
    public void ensureSchema() throws SQLException {
        String ddl = loadResource("/schema.sql");
        if (ddl == null || ddl.isBlank())
            throw new IllegalStateException("schema.sql não encontrado nos resources.");

        try (Connection c = factory.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                // O ficheiro pode conter vários comandos separados por ';'
                for (String stmt : ddl.split(";")) {
                    String s = stmt.trim();
                    if (s.isEmpty()) continue;
                    st.execute(s);
                }
            }
            c.commit();
        }
    }

    private String loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
