package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Map<String, Object> config = loadConfig();

    private static Map<String, Object> loadConfig() {
        Yaml yaml = new Yaml();

        // 1) Внешний конфиг (приоритет): -Dapp.config=/opt/DiscordBot/config/application.yml
        String externalPath = Optional.ofNullable(System.getProperty("app.config"))
                .orElseGet(() -> System.getenv("APP_CONFIG"));

        try (InputStream is = openStream(externalPath)) {
            if (is == null) {
                logger.error("Не удалось найти файл application.yml (ни по app.config/APP_CONFIG, ни в classpath)");
                throw new RuntimeException("Не удалось найти файл application.yml");
            }
            return yaml.load(is);
        } catch (Exception e) {
            logger.error("Ошибка загрузки конфигурации", e);
            throw new RuntimeException("Ошибка загрузки конфигурации", e);
        }
    }

    private static InputStream openStream(String externalPath) throws Exception {
        if (externalPath != null && !externalPath.isBlank()) {
            Path p = Path.of(externalPath);
            if (!Files.exists(p)) {
                logger.error("Файл конфигурации не найден по пути: {}", externalPath);
                return null;
            }
            return Files.newInputStream(p);
        }

        // 2) Fallback: application.yml внутри jar (classpath)
        return ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml");
    }

    @SuppressWarnings("unchecked")
    public static String getDiscordToken() {
        try {
            Map<String, Object> discord = (Map<String, Object>) config.get("discord");
            String raw = discord != null ? (String) discord.get("token") : null;
            return resolveValue(raw, "DISCORD_BOT_TOKEN", "DISCORD_TOKEN");
        } catch (Exception e) {
            logger.error("Ошибка получения токена Discord из конфигурации", e);
            throw new RuntimeException("Ошибка получения токена Discord из конфигурации", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffToken() {
        try {
            Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
            String raw = tinkoff != null ? (String) tinkoff.get("token") : null;
            return resolveValue(raw, "TINKOFF_TOKEN");
        } catch (Exception e) {
            logger.error("Ошибка получения токена Tinkoff из конфигурации", e);
            throw new RuntimeException("Ошибка получения токена Tinkoff из конфигурации", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffApiMode() {
        try {
            Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
            String raw = tinkoff != null ? (String) tinkoff.get("api-mode") : null;
            String resolved = resolveValue(raw, "TINKOFF_API_MODE");
            return (resolved == null || resolved.isBlank()) ? "readonly" : resolved;
        } catch (Exception e) {
            logger.error("Ошибка получения режима API Tinkoff из конфигурации", e);
            return "readonly";
        }
    }

    static String resolveValue(String raw, String... envNames) {
        if (raw == null) {
            raw = "";
        }
        String value = raw.trim();

        // ${ENV:default} or ${ENV}
        if (value.startsWith("${") && value.endsWith("}")) {
            String body = value.substring(2, value.length() - 1);
            String[] parts = body.split(":", 2);
            String primaryEnv = parts[0].trim();
            String def = parts.length > 1 ? parts[1].trim() : "";

            String fromPrimary = System.getenv(primaryEnv);
            if (fromPrimary != null && !fromPrimary.isBlank()) {
                return fromPrimary.trim();
            }

            for (String envName : envNames) {
                String v = System.getenv(envName);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
            return def;
        }

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getAllowedChannelIds() {
        try {
            Map<String, Object> discord = (Map<String, Object>) config.get("discord");
            Object raw = discord != null ? discord.get("allowed-channel-ids") : null;
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) result.add(s);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Ошибка получения списка разрешенных channel IDs", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static String getReportGuildId() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return reports != null ? (String) reports.get("guild-id") : null;
        } catch (Exception e) {
            logger.error("Ошибка получения guild ID для отчетов из конфигурации", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static String getReportChannelName() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return reports != null ? (String) reports.get("channel-name") : null;
        } catch (Exception e) {
            logger.error("Ошибка получения названия канала для отчетов из конфигурации", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static String getCurrencyReportCron() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            String cron = reports != null ? (String) reports.get("currency-cron") : null;
            return cron != null ? cron : "0 0 21 * * *";
        } catch (Exception e) {
            logger.error("Ошибка получения cron выражения для валютных отчетов из конфигурации", e);
            return "0 0 21 * * *";
        }
    }

    @SuppressWarnings("unchecked")
    public static String getSharesReportCron() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            String cron = reports != null ? (String) reports.get("shares-cron") : null;
            return cron != null ? cron : "0 30 21 * * *";
        } catch (Exception e) {
            logger.error("Ошибка получения cron выражения для отчетов по акциям из конфигурации", e);
            return "0 30 21 * * *";
        }
    }
}
