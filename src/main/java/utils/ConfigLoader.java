package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Map<String, Object> config = loadConfig();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml");
            if (inputStream == null) {
                logger.error("Не удалось найти файл application.yml");
                throw new RuntimeException("Не удалось найти файл application.yml");
            }
            return yaml.load(inputStream);
        } catch (Exception e) {
            logger.error("Ошибка загрузки конфигурации", e);
            throw new RuntimeException("Ошибка загрузки конфигурации", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getDiscordToken() {
        try {
            Map<String, Object> discord = (Map<String, Object>) config.get("discord");
            String token = (String) discord.get("token");
            // Обработка подстановки переменных окружения
            if (token != null && token.startsWith("${DISCORD_TOKEN:")) {
                // Извлечение значения по умолчанию
                int start = token.indexOf(":") + 1;
                int end = token.length() - 1; // Удаление закрывающей скобки
                token = token.substring(start, end);
            }
            // Удаление кавычек, если они есть
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
            String token = (String) tinkoff.get("token");
            // Обработка подстановки переменных окружения
            if (token != null && token.startsWith("${TINKOFF_TOKEN:")) {
                // Извлечение значения по умолчанию
                int start = token.indexOf(":") + 1;
                int end = token.length() - 1; // Удаление закрывающей скобки
                token = token.substring(start, end);
            }
            // Удаление кавычек, если они есть
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
            String apiMode = (String) tinkoff.get("api-mode");
            // Обработка подстановки переменных окружения
            if (apiMode != null && apiMode.startsWith("${TINKOFF_API_MODE:")) {
                // Извлечение значения по умолчанию
                int start = apiMode.indexOf(":") + 1;
                int end = apiMode.length() - 1; // Удаление закрывающей скобки
                apiMode = apiMode.substring(start, end);
            }
            return apiMode != null ? apiMode : "readonly"; // Резервный вариант
        } catch (Exception e) {
            logger.error("Ошибка получения режима API Tinkoff из конфигурации", e);
            return "readonly"; // Резервный вариант
        }
    }
    
    // New methods for report configuration
    @SuppressWarnings("unchecked")
    public static String getReportGuildId() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return (String) reports.get("guild-id");
        } catch (Exception e) {
            logger.error("Ошибка получения guild ID для отчетов из конфигурации", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public static String getReportChannelName() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return (String) reports.get("channel-name");
        } catch (Exception e) {
            logger.error("Ошибка получения названия канала для отчетов из конфигурации", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public static String getCurrencyReportCron() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return (String) reports.get("currency-cron");
        } catch (Exception e) {
            logger.error("Ошибка получения cron выражения для валютных отчетов из конфигурации", e);
            return "0 0 21 * * *"; // Default: daily at 21:00
        }
    }
    
    @SuppressWarnings("unchecked")
    public static String getSharesReportCron() {
        try {
            Map<String, Object> reports = (Map<String, Object>) config.get("reports");
            return (String) reports.get("shares-cron");
        } catch (Exception e) {
            logger.error("Ошибка получения cron выражения для отчетов по акциям из конфигурации", e);
            return "0 30 21 * * *"; // Default: daily at 21:30
        }
    }
}