package services.sandbox.ignite;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Инициализирует DDL-схему Apache Ignite 3.x при старте приложения.
 *
 * <p>Загружает файл {@code ignite3-schema.sql} из classpath и выполняет
 * каждый SQL-стейтмент отдельно. Ошибки отдельных стейтментов логируются,
 * но не прерывают инициализацию остальных таблиц (IF NOT EXISTS).
 */
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final IgniteClient igniteClient;

    /**
     * Создаёт инициализатор схемы.
     *
     * @param igniteClient подключённый клиент Apache Ignite 3
     */
    public SchemaInitializer(IgniteClient igniteClient) {
        this.igniteClient = igniteClient;
    }

    /**
     * Загружает DDL из classpath и исполняет каждый стейтмент.
     */
    public void initSchema() {
        String ddl = loadDdl();
        if (ddl == null || ddl.isBlank()) {
            log.error("SchemaInitializer: ignite3-schema.sql is empty or not found");
            return;
        }

        String[] statements = ddl.split(";");
        int ok = 0;
        int failed = 0;
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            try {
                igniteClient.sql().execute(null, trimmed);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("SchemaInitializer: failed to execute statement [{}]: {}",
                        trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed,
                        e.getMessage());
            }
        }
        log.info("SchemaInitializer: schema init complete. ok={} failed={}", ok, failed);
    }

    private String loadDdl() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("ignite3-schema.sql")) {
            if (is == null) {
                log.error("SchemaInitializer: ignite3-schema.sql not found in classpath");
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SchemaInitializer: failed to load ignite3-schema.sql: {}", e.getMessage());
            return null;
        }
    }
}
