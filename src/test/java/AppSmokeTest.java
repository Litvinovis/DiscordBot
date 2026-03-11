import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppSmokeTest {

    @Test
    void appClass_isLoadable() {
        assertNotNull(App.class);
    }
}
