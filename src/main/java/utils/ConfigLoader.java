package utils;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Утилитарный класс для загрузки конфигурации приложения.
 *
 * <p>Параметры считываются в следующем приоритете:
 * <ol>
 *   <li>переменные окружения;</li>
 *   <li>файл {@code application.yml} (поиск в нескольких стандартных путях);</li>
 *   <li>значения по умолчанию.</li>
 * </ol>
 * Поддерживает плейсхолдеры вида {@code ${ENV_KEY:default}} в YAML-значениях.
 */
public final class ConfigLoader {
    private static final Map<String, Object> CONFIG = loadConfig();

    private ConfigLoader() {
    }

    /**
     * Возвращает токен Discord-бота.
     * Сначала проверяется переменная окружения {@code DISCORD_BOT_TOKEN}.
     *
     * @return токен Discord или пустую строку, если не задан
     */
    public static String getDiscordToken() {
        String botToken = System.getenv("DISCORD_BOT_TOKEN");
        if (botToken != null && !botToken.isBlank()) {
            return botToken.trim();
        }
        return getString("discord.token", "DISCORD_TOKEN", "");
    }

    /**
     * Возвращает токен T-Invest API.
     *
     * @return токен Tinkoff/T-Bank или пустую строку, если не задан
     */
    public static String getTinkoffToken() {
        return getString("tinkoff.token", "TINKOFF_TOKEN", "");
    }

    /**
     * Возвращает режим API T-Invest: {@code "sandbox"} или {@code "prod"}.
     *
     * @return режим API (по умолчанию {@code "prod"})
     */
    public static String getTinkoffApiMode() {
        return getString("tinkoff.api-mode", "TINKOFF_API_MODE", "prod");
    }

    /**
     * Возвращает список идентификаторов Discord-каналов, в которых принимаются команды.
     *
     * @return неизменяемый список ID каналов
     */
    public static List<String> getAllowedChannelIds() {
        String fromEnv = System.getenv("DISCORD_ALLOWED_CHANNEL_IDS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parseCsv(fromEnv);
        }

        Object value = getNestedValue(CONFIG, "discord.allowed-channel-ids");
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return List.of("1157258712138907700");
    }

    /**
     * Возвращает ID Discord-сервера для отправки статистических отчётов.
     *
     * @return ID сервера или пустую строку, если не задан
     */
    public static String getReportGuildId() {
        return getString("reports.guild-id", "DISCORD_REPORT_GUILD_ID", "");
    }

    /**
     * Возвращает имя Discord-канала для отправки статистических отчётов.
     *
     * @return имя канала или пустую строку, если не задано
     */
    public static String getReportChannelName() {
        return getString("reports.channel-name", "DISCORD_REPORT_CHANNEL_NAME", "");
    }

    /**
     * Возвращает cron-выражение расписания для отчёта по валютам.
     *
     * @return cron-строка из 6 полей (по умолчанию {@code "0 0 10 * * *"})
     */
    public static String getCurrencyReportCron() {
        return getString("reports.currency.cron", "CURRENCY_REPORT_CRON", "0 0 10 * * *");
    }

    /**
     * Возвращает cron-выражение расписания для отчёта по акциям.
     *
     * @return cron-строка из 6 полей (по умолчанию {@code "0 5 10 * * *"})
     */
    public static String getSharesReportCron() {
        return getString("reports.shares.cron", "SHARES_REPORT_CRON", "0 5 10 * * *");
    }

    /**
     * Возвращает стартовый баланс нового участника песочницы в рублях.
     *
     * @return стартовый баланс (по умолчанию 1 000 000 ₽)
     */
    public static BigDecimal getSandboxStartBalance() {
        return getBigDecimal("sandbox.start-balance", "SANDBOX_START_BALANCE", new BigDecimal("1000000.00"));
    }

    /**
     * Возвращает ставку комиссии за сделку (доля от оборота).
     *
     * @return ставка комиссии (по умолчанию 0.001 = 0.1%)
     */
    public static BigDecimal getSandboxCommissionRate() {
        return getBigDecimal("sandbox.commission-rate", "SANDBOX_COMMISSION_RATE", new BigDecimal("0.001"));
    }

    /**
     * Возвращает максимально допустимое кредитное плечо в песочнице.
     *
     * @return максимальное плечо (по умолчанию 3.0x)
     */
    public static BigDecimal getSandboxMaxLeverage() {
        return getBigDecimal("sandbox.max-leverage", "SANDBOX_MAX_LEVERAGE", new BigDecimal("3.0"));
    }

    /**
     * Возвращает порог поддерживающей маржи (maintenance margin).
     *
     * @return порог маржи (по умолчанию 0.25 = 25%)
     */
    public static BigDecimal getSandboxMaintenanceMargin() {
        return getBigDecimal("sandbox.maintenance-margin", "SANDBOX_MAINTENANCE_MARGIN", new BigDecimal("0.25"));
    }

    /**
     * Возвращает список тикеров, доступных для торговли в песочнице.
     *
     * @return список тикеров в верхнем регистре
     */
    public static List<String> getSandboxAllowedTickers() {
        String fromEnv = System.getenv("SANDBOX_ALLOWED_TICKERS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parseCsv(fromEnv).stream()
                    .map(String::toUpperCase)
                    .toList();
        }
        Object value = getNestedValue(CONFIG, "sandbox.allowed-tickers");
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of(
                // === MOEX 1st echelon (blue-chips) ===
                "SBER", "SBERP", "GAZP", "LKOH", "ROSN", "NVTK", "YDEX", "TATN", "TATNP",
                "PLZL", "MGNT", "MTSS", "SNGS", "SNGSP", "ALRS", "CHMF", "NLMK", "VTBR",
                "GMKN", "RUAL", "MAGN", "TCSG", "FIVE", "OZON", "VKCO", "AFLT", "HYDR",
                "IRAO", "RTKM", "RTKMP", "FEES", "PHOR", "AKRN", "GLTR",
                // === MOEX 2nd echelon ===
                "BSPB", "SVCB", "MVID", "FIXP", "PIKK", "POSI", "ASTR", "HEAD", "SOFL",
                "TRMK", "RASP", "TGKB", "TGKD", "MSNG", "UPRO", "OGKB", "LSNG", "LSNGP",
                "SGZH", "AGRO", "SPBE", "CIAN", "GEMC", "MDMG", "RNFT", "BANEP", "BANE",
                "KMAZ", "UWGN", "NKNC", "NKNCP", "KZOS", "KZOSP", "SELG", "PMSB", "PMSBP",
                "MFGP", "GCHE", "GRNT", "NSVZ", "ZVEZ", "DIOD",
                // === MOEX 3rd echelon & emerging ===
                "LENT", "KART", "KLSB", "IRKT", "DSKI", "RKKE", "ELMT", "BRZL",
                "TGKN", "MISB", "MISBP", "MGTSP", "CHGZ", "KUBE", "AMEZ",
                // === SPB Exchange — foreign stocks (USD) ===
                "AAPL", "MSFT", "AMZN", "GOOGL", "GOOG", "TSLA", "META", "NVDA",
                "BRK.B", "JPM", "JNJ", "V", "PG", "UNH", "HD", "MA",
                "DIS", "NFLX", "PYPL", "INTC", "AMD", "CRM", "ORCL", "IBM",
                "BA", "GE", "XOM", "CVX", "KO", "PEP", "MCD", "WMT",
                "BABA", "JD", "NKE", "SBUX", "UBER", "LYFT", "SNAP", "TWTR",
                "SPOT", "SQ", "ROKU", "ZM", "SHOP", "ABNB", "COIN", "HOOD",
                "F", "GM", "T", "VZ", "CSCO", "QCOM", "TXN", "MU",
                "LRCX", "KLAC", "AMAT", "ASML", "TSM", "AVGO", "MRVL"
        );
    }

    /**
     * Возвращает адрес Ignite 3 thin client (host:port).
     *
     * @return адрес (по умолчанию {@code "127.0.0.1:10300"})
     */
    public static String getIgnite3Address() {
        return getString("ignite3.address", "IGNITE3_ADDRESS", "127.0.0.1:10300");
    }

    /**
     * Возвращает локальный адрес для привязки Ignite-клиента (legacy, не используется в Ignite 3).
     *
     * @return адрес (по умолчанию {@code "127.0.0.1"})
     */
    public static String getIgniteLocalAddress() {
        return getString("ignite.local-address", "IGNITE_LOCAL_ADDRESS", "127.0.0.1");
    }

    /**
     * Возвращает список адресов для TCP-discovery Ignite-кластера (legacy, не используется в Ignite 3).
     *
     * @return список адресов в формате {@code host:port} или {@code host:portRange}
     */
    public static List<String> getIgniteDiscoveryAddresses() {
        String fromEnv = System.getenv("IGNITE_DISCOVERY_ADDRESSES");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parseCsv(fromEnv);
        }
        Object value = getNestedValue(CONFIG, "ignite.discovery-addresses");
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of("127.0.0.1:47500..47509");
    }

    /**
     * Возвращает рабочую директорию для Ignite-клиента (legacy, не используется в Ignite 3).
     *
     * @return путь к директории (по умолчанию {@code "/tmp/ignite-stonks-client"})
     */
    public static String getIgniteWorkDir() {
        return getString("ignite.work-dir", "IGNITE_WORK_DIR", "/tmp/ignite-stonks-client");
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

    private static BigDecimal getBigDecimal(String yamlPath, String envKey, BigDecimal defaultValue) {
        String text = getString(yamlPath, envKey, defaultValue.toPlainString());
        try {
            return new BigDecimal(text);
        } catch (Exception _) {
            return defaultValue;
        }
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
            } catch (IOException _) {
            }
        }

        try (InputStream classpath = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (classpath != null) {
                Object loaded = yaml.load(classpath);
                if (loaded instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            }
        } catch (IOException _) {
        }

        return Collections.emptyMap();
    }
}
