/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.yaml.snakeyaml.Yaml
 */
package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

public final class ConfigLoader {
    private static final Map<String, Object> CONFIG = ConfigLoader.loadConfig();

    private ConfigLoader() {
    }

    public static String getDiscordToken() {
        String botToken = System.getenv("DISCORD_BOT_TOKEN");
        if (botToken != null && !botToken.isBlank()) {
            return botToken.trim();
        }
        return ConfigLoader.getString("discord.token", "DISCORD_TOKEN", "");
    }

    public static String getTinkoffToken() {
        return ConfigLoader.getString("tinkoff.token", "TINKOFF_TOKEN", "");
    }

    public static String getTinkoffApiMode() {
        return ConfigLoader.getString("tinkoff.api-mode", "TINKOFF_API_MODE", "prod");
    }

    public static List<String> getAllowedChannelIds() {
        String fromEnv = System.getenv("DISCORD_ALLOWED_CHANNEL_IDS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return ConfigLoader.parseCsv(fromEnv);
        }
        Object value = ConfigLoader.getNestedValue(CONFIG, "discord.allowed-channel-ids");
        if (value instanceof List) {
            List list = (List)value;
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of("1157258712138907700");
    }

    public static String getReportGuildId() {
        return ConfigLoader.getString("reports.guild-id", "DISCORD_REPORT_GUILD_ID", "");
    }

    public static String getReportChannelName() {
        return ConfigLoader.getString("reports.channel-name", "DISCORD_REPORT_CHANNEL_NAME", "");
    }

    public static String getCurrencyReportCron() {
        return ConfigLoader.getString("reports.currency-cron", "CURRENCY_REPORT_CRON", "0 0 10 * * *");
    }

    public static String getSharesReportCron() {
        return ConfigLoader.getString("reports.shares-cron", "SHARES_REPORT_CRON", "0 5 10 * * *");
    }

    public static double getSandboxStartBalance() {
        return ConfigLoader.getDouble("sandbox.start-balance", "SANDBOX_START_BALANCE", 1000000.0);
    }

    public static double getSandboxCommissionRate() {
        return ConfigLoader.getDouble("sandbox.commission-rate", "SANDBOX_COMMISSION_RATE", 0.001);
    }

    public static double getSandboxMaxLeverage() {
        return ConfigLoader.getDouble("sandbox.max-leverage", "SANDBOX_MAX_LEVERAGE", 3.0);
    }

    public static double getSandboxMaintenanceMargin() {
        return ConfigLoader.getDouble("sandbox.maintenance-margin", "SANDBOX_MAINTENANCE_MARGIN", 0.25);
    }

    public static List<String> getSandboxAllowedTickers() {
        Object value = ConfigLoader.getNestedValue(CONFIG, "sandbox.allowed-tickers");
        if (value instanceof List) {
            List list = (List)value;
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String fromEnv = System.getenv("SANDBOX_ALLOWED_TICKERS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return ConfigLoader.parseCsv(fromEnv).stream().map(String::toUpperCase).toList();
        }
        return List.of("SBER", "GAZP", "LKOH", "ROSN", "NVTK", "YDEX", "TATN", "PLZL", "MGNT", "MTSS", "SNGS", "ALRS", "CHMF", "NLMK", "VTBR");
    }

    public static String getIgniteLocalAddress() {
        return ConfigLoader.getString("ignite.local-address", "IGNITE_LOCAL_ADDRESS", "127.0.0.1");
    }

    public static List<String> getIgniteDiscoveryAddresses() {
        String fromEnv = System.getenv("IGNITE_DISCOVERY_ADDRESSES");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return ConfigLoader.parseCsv(fromEnv);
        }
        Object value = ConfigLoader.getNestedValue(CONFIG, "ignite.discovery-addresses");
        if (value instanceof List) {
            List list = (List)value;
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of("127.0.0.1:47500..47509");
    }

    public static String getIgniteWorkDir() {
        return ConfigLoader.getString("ignite.work-dir", "IGNITE_WORK_DIR", "/tmp/ignite-stonks-client");
    }

    private static String getString(String yamlPath, String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        Object value = ConfigLoader.getNestedValue(CONFIG, yamlPath);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return (text = ConfigLoader.resolveEnvPlaceholder(text)).isEmpty() ? defaultValue : text;
    }

    private static double getDouble(String yamlPath, String envKey, double defaultValue) {
        String text = ConfigLoader.getString(yamlPath, envKey, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(text);
        }
        catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String resolveEnvPlaceholder(String value) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colon = inner.indexOf(58);
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

    private static Object getNestedValue(Map<String, Object> source, String path) {
        String[] parts = path.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            Map<String, Object> map = current;
            current = map.get(part);
            if (current != null) continue;
            return null;
        }
        return current;
    }

    private static List<String> parseCsv(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static Map<String, Object> loadConfig() {
        Yaml yaml = new Yaml();
        List<Path> candidateFiles = List.of(Path.of("application.yml", new String[0]), Path.of("config", "application.yml"), Path.of("src", "main", "resources", "application.yml"));
        for (Path file : candidateFiles) {
            if (!Files.exists(file, new LinkOption[0])) continue;
            try {
                InputStream in222 = Files.newInputStream(file, new OpenOption[0]);
                try {
                    Map map4;
                    Object loaded2 = yaml.load(in222);
                    if (!(loaded2 instanceof Map)) continue;
                    Map map3 = map4 = (Map)loaded2;
                    return map3;
                }
                finally {
                    if (in222 == null) continue;
                    in222.close();
                }
            }
            catch (IOException in222) {}
        }
        try (InputStream classpath = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml");){
            Map map2;
            if (classpath == null) return Collections.emptyMap();
            Object loaded = yaml.load(classpath);
            if (!(loaded instanceof Map)) return Collections.emptyMap();
            Map map = map2 = (Map)loaded;
            return map;
        }
        catch (IOException iOException) {
            // empty catch block
        }
        return Collections.emptyMap();
    }
}

