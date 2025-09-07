package utils;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class ConfigLoader {
    private static final Map<String, Object> config = loadConfig();

    private static Map<String, Object> loadConfig() {
        Yaml yaml = new Yaml();
        InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml");
        return yaml.load(inputStream);
    }

    @SuppressWarnings("unchecked")
    public static String getDiscordToken() {
        Map<String, Object> discord = (Map<String, Object>) config.get("discord");
        return (String) discord.get("token");
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffToken() {
        Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
        return (String) tinkoff.get("token");
    }

    @SuppressWarnings("unchecked")
    public static String getTinkoffApiMode() {
        Map<String, Object> tinkoff = (Map<String, Object>) config.get("tinkoff");
        return (String) tinkoff.get("api-mode");
    }
}