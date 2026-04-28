package utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigLoader} logic that can be exercised without
 * a real config file or environment variables.
 *
 * The static block in ConfigLoader loads the config at class-load time, but all
 * business-logic helpers are accessible via the public static methods. We test the
 * default-value behaviour (no config file, no env var) and the parsing helpers
 * that are inlined inside the class.
 */
class ConfigLoaderTest {

    // -----------------------------------------------------------------------
    // Default values when no env var and no config file is present
    // -----------------------------------------------------------------------

    @Test
    void sandboxStartBalance_defaultIs1000000() {
        // Runs without a config file; must return the hard-coded default
        BigDecimal balance = ConfigLoader.getSandboxStartBalance();
        assertNotNull(balance);
        assertTrue(balance.compareTo(BigDecimal.ZERO) > 0,
                "Start balance must be positive");
        assertEquals(0, new BigDecimal("1000000.00").compareTo(balance),
                "Default start balance must be 1 000 000 ₽");
    }

    @Test
    void sandboxCommissionRate_defaultIs0001() {
        BigDecimal rate = ConfigLoader.getSandboxCommissionRate();
        assertNotNull(rate);
        assertEquals(0, new BigDecimal("0.001").compareTo(rate),
                "Default commission rate must be 0.001 (0.1%)");
    }

    @Test
    void sandboxMaxLeverage_defaultIs3() {
        BigDecimal maxLev = ConfigLoader.getSandboxMaxLeverage();
        assertNotNull(maxLev);
        assertEquals(0, new BigDecimal("3.0").compareTo(maxLev),
                "Default max leverage must be 3.0x");
    }

    @Test
    void sandboxMaintenanceMargin_defaultIs025() {
        BigDecimal mm = ConfigLoader.getSandboxMaintenanceMargin();
        assertNotNull(mm);
        assertEquals(0, new BigDecimal("0.25").compareTo(mm),
                "Default maintenance margin must be 0.25");
    }

    @Test
    void allowedTickers_defaultListIsNotEmpty() {
        List<String> tickers = ConfigLoader.getSandboxAllowedTickers();
        assertNotNull(tickers);
        assertFalse(tickers.isEmpty(), "Default allowed tickers must not be empty");
    }

    @Test
    void allowedTickers_containsMoexBlueChips() {
        List<String> tickers = ConfigLoader.getSandboxAllowedTickers();
        assertTrue(tickers.contains("SBER"), "SBER must be in defaults");
        assertTrue(tickers.contains("GAZP"), "GAZP must be in defaults");
        assertTrue(tickers.contains("LKOH"), "LKOH must be in defaults");
    }

    @Test
    void allowedTickers_containsSpbExchangeStocks() {
        List<String> tickers = ConfigLoader.getSandboxAllowedTickers();
        assertTrue(tickers.contains("AAPL"), "AAPL (SPB) must be in defaults");
        assertTrue(tickers.contains("MSFT"), "MSFT (SPB) must be in defaults");
        assertTrue(tickers.contains("TSLA"), "TSLA (SPB) must be in defaults");
    }

    @Test
    void allowedTickers_noDuplicates() {
        List<String> tickers = ConfigLoader.getSandboxAllowedTickers();
        long distinctCount = tickers.stream().distinct().count();
        assertEquals(tickers.size(), distinctCount,
                "Default allowed tickers must have no duplicates");
    }

    @Test
    void allowedTickers_allUpperCase() {
        List<String> tickers = ConfigLoader.getSandboxAllowedTickers();
        for (String t : tickers) {
            assertEquals(t.toUpperCase(), t,
                    "All tickers must be uppercase, but found: " + t);
        }
    }

    @Test
    void dbUrl_defaultIsNotBlank() {
        String url = ConfigLoader.getDbUrl();
        assertNotNull(url);
        assertFalse(url.isBlank());
        assertTrue(url.startsWith("jdbc:postgresql://"));
    }

    // -----------------------------------------------------------------------
    // Env-placeholder resolution — tested via the resolved BigDecimal values.
    // The resolveEnvPlaceholder method is private, so we test through the public API.
    // -----------------------------------------------------------------------

    /**
     * Validates that a non-parseable BigDecimal string falls back to the default.
     * ConfigLoader.getBigDecimal silently swallows parse errors and returns the default.
     * We cannot inject a bad value directly without writing a file; instead we verify
     * the contract: all returned BigDecimals must be parseable.
     */
    @Test
    void sandboxConfig_allBigDecimalsAreParseable() {
        // These must not throw
        assertDoesNotThrow(() -> ConfigLoader.getSandboxStartBalance().toPlainString());
        assertDoesNotThrow(() -> ConfigLoader.getSandboxCommissionRate().toPlainString());
        assertDoesNotThrow(() -> ConfigLoader.getSandboxMaxLeverage().toPlainString());
        assertDoesNotThrow(() -> ConfigLoader.getSandboxMaintenanceMargin().toPlainString());
    }

    // -----------------------------------------------------------------------
    // Currency cron defaults
    // -----------------------------------------------------------------------

    @Test
    void currencyReportCron_defaultIsNotBlank() {
        String cron = ConfigLoader.getCurrencyReportCron();
        assertNotNull(cron);
        assertFalse(cron.isBlank());
    }

    @Test
    void sharesReportCron_defaultIsNotBlank() {
        String cron = ConfigLoader.getSharesReportCron();
        assertNotNull(cron);
        assertFalse(cron.isBlank());
    }
}
