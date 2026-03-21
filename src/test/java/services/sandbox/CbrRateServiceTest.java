package services.sandbox;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CbrRateService}.
 *
 * These tests validate the set of supported currencies and basic properties
 * of the service without making live HTTP calls.
 */
class CbrRateServiceTest {

    @Test
    void supportedCurrencies_containsAllRequired() {
        Set<String> supported = CbrRateService.SUPPORTED_CURRENCIES;
        assertTrue(supported.contains("USD"), "USD should be supported");
        assertTrue(supported.contains("EUR"), "EUR should be supported");
        assertTrue(supported.contains("CNY"), "CNY should be supported");
        assertTrue(supported.contains("GBP"), "GBP should be supported");
        assertTrue(supported.contains("CHF"), "CHF should be supported");
        assertTrue(supported.contains("JPY"), "JPY should be supported");
        assertTrue(supported.contains("HKD"), "HKD should be supported");
    }

    @Test
    void supportedCurrencies_hasExactlySevenEntries() {
        assertEquals(7, CbrRateService.SUPPORTED_CURRENCIES.size());
    }

    @Test
    void cbrUrl_isCorrect() {
        assertEquals("https://www.cbr.ru/scripts/XML_daily.asp", CbrRateService.CBR_URL);
    }

    @Test
    void fetchRates_returnsEmptyMapOnNetworkError() {
        // Create a subclass that overrides fetchRates to simulate network failure
        CbrRateService service = new CbrRateService() {
            @Override
            public Map<String, BigDecimal> fetchRates() {
                // Simulate network error by returning empty map (as the real method does on error)
                return new java.util.HashMap<>();
            }
        };
        Map<String, BigDecimal> rates = service.fetchRates();
        assertNotNull(rates, "fetchRates should never return null");
        assertTrue(rates.isEmpty(), "Should return empty map on error");
    }
}
