package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
            String token = discord != null ? (String) discord.get("token") : null;

            if (token != null && token.startsWith("${DISCORD_TOKEN:")) {
                int start = token.indexOf(":") + 1;
                int end = token.length() - 1;
                token = token.substring(start, end);
            }

            if (token != null && token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1);
            }
            return token;
        } catch (Exception e) {
            logger.error("Ошибка получения токена Discord из конфигурации", e);
            throw new RuntimeException("Ошибка получения токена Discord из конфигурации", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffToken() {
        try {
            Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
            String token = tinkoff != null ? (String) tinkoff.get("token") : null;

            if (token != null && token.startsWith("${TINKOFF_TOKEN:")) {
                int start = token.indexOf(":") + 1;
                int end = token.length() - 1;
                token = token.substring(start, end);
            }

            if (token != null && token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1);
            }
            return token;
        } catch (Exception e) {
            logger.error("Ошибка получения токена Tinkoff из конфигурации", e);
            throw new RuntimeException("Ошибка получения токена Tinkoff из конфигурации", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffApiMode() {
        try {
            Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
            String apiMode = tinkoff != null ? (String) tinkoff.get("api-mode") : null;

            if (apiMode != null && apiMode.startsWith("${TINKOFF_API_MODE:")) {
                int start = apiMode.indexOf(":") + 1;
                int end = apiMode.length() - 1;
                apiMode = apiMode.substring(start, end);
            }
            return apiMode != null ? apiMode : "readonly";
        } catch (Exception e) {
            logger.error("Ошибка получения режима API Tinkoff из конфигурации", e);
            return "readonly";
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
