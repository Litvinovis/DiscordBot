package services.sandbox.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initSchema() {
        String ddl = loadDdl();
        if (ddl == null || ddl.isBlank()) {
            log.error("SchemaInitializer: schema.sql is empty or not found");
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String s : ddl.split(";")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            log.info("SchemaInitializer: schema init complete");
        } catch (Exception e) {
            log.error("SchemaInitializer: failed to init schema: {}", e.getMessage(), e);
        }
    }

    private String loadDdl() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) {
                log.error("SchemaInitializer: schema.sql not found in classpath");
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SchemaInitializer: failed to load schema.sql: {}", e.getMessage());
            return null;
        }
    }
}
