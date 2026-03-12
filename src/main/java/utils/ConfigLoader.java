package utils;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class ConfigLoader {
    private static final Map<String, Object> CONFIG = loadConfig();

    private ConfigLoader() {
    }

    public static String getDiscordToken() {
        String botToken = System.getenv("DISCORD_BOT_TOKEN");
        if (botToken != null && !botToken.isBlank()) {
            return botToken.trim();
        }
        return getString("discord.token", "DISCORD_TOKEN", "");
    }

    public static String getTinkoffToken() {
        return getString("tinkoff.token", "TINKOFF_TOKEN", "");
    }

    public static String getTinkoffApiMode() {
        return getString("tinkoff.api-mode", "TINKOFF_API_MODE", "prod");
    }

    public static List<String> getAllowedChannelIds() {
        String fromEnv = System.getenv("DISCORD_ALLOWED_CHANNEL_IDS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parseCsv(fromEnv);
        }

        Object value = getNestedValue(CONFIG, "discord.allowed-channel-ids");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        return List.of("1157258712138907700");
    }

    public static String getReportGuildId() {
        return getString("discord.report.guild-id", "DISCORD_REPORT_GUILD_ID", "");
    }

    public static String getReportChannelName() {
        return getString("discord.report.channel-name", "DISCORD_REPORT_CHANNEL_NAME", "");
    }

    public static String getCurrencyReportCron() {
        return getString("reports.currency.cron", "CURRENCY_REPORT_CRON", "0 0 10 * * *");
    }

    public static String getSharesReportCron() {
        return getString("reports.shares.cron", "SHARES_REPORT_CRON", "0 5 10 * * *");
    }

    private static String getString(String yamlPath, String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        Object value = getNestedValue(CONFIG, yamlPath);
        if (value == null) {
            return defaultValue;
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }

        text = resolveEnvPlaceholder(text);
        return text.isEmpty() ? defaultValue : text;
    }

    private static String resolveEnvPlaceholder(String value) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colon = inner.indexOf(':');
            String envKey = colon >= 0 ? inner.substring(0, colon) : inner;
            String fallback = colon >= 0 ? inner.substring(colon + 1) : "";

            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                return envValue.trim();
            }
            return fallback.trim();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object getNestedValue(Map<String, Object> source, String path) {
        String[] parts = path.split("\\.");
        Object current = source;

        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private static List<String> parseCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadConfig() {
        Yaml yaml = new Yaml();

        List<Path> candidateFiles = List.of(
                Path.of("application.yml"),
                Path.of("config", "application.yml"),
                Path.of("src", "main", "resources", "application.yml")
        );

        for (Path file : candidateFiles) {
            if (!Files.exists(file)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                Object loaded = yaml.load(in);
                if (loaded instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            } catch (IOException ignored) {
                // fallback to next source
            }
        }

        try (InputStream classpath = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (classpath != null) {
                Object loaded = yaml.load(classpath);
                if (loaded instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            }
        } catch (IOException ignored) {
            // ignored
        }

        return Collections.emptyMap();
    }
}
