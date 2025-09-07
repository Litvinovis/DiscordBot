package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {

    @Test
    public void testConfigLoader() {
        // Проверка, что загрузчик конфигурации может загрузить конфигурацию
        assertDoesNotThrow(() -> {
            String discordToken = ConfigLoader.getDiscordToken();
            String tinkoffToken = ConfigLoader.getTinkoffToken();
            String apiMode = ConfigLoader.getTinkoffApiMode();
            
            // Эти значения не должны быть null
            assertNotNull(discordToken);
            assertNotNull(tinkoffToken);
            assertNotNull(apiMode);
        });
    }
}