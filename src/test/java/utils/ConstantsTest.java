package utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstantsTest {

    @Test
    void logMessage_constantIsStable() {
        assertEquals("Упс {}", Constants.LOG_MESSAGE);
    }
}
