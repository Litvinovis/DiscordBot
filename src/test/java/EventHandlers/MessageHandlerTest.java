package EventHandlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageHandlerTest {

    @Test
    void constructor_createsHandler() {
        MessageHandler handler = new MessageHandler(null);
        assertNotNull(handler);
    }
}
