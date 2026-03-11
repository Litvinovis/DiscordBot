package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CurrencyInfoServiceTest {

    @Test
    void constructor_createsService() {
        CurrencyInfoService service = new CurrencyInfoService(null);
        assertNotNull(service);
    }
}
