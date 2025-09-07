package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {

    @Test
    public void testConfigLoader() {
        // Test that the config loader can load the configuration
        assertDoesNotThrow(() -> {
            String discordToken = ConfigLoader.getDiscordToken();
            String tinkoffToken = ConfigLoader.getTinkoffToken();
            String apiMode = ConfigLoader.getTinkoffApiMode();
            
            // These should not be null
            assertNotNull(discordToken);
            assertNotNull(tinkoffToken);
            assertNotNull(apiMode);
        });
    }
}