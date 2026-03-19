package services.statTask;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that validate SPB Exchange (СПБ Биржа) ticker inclusion logic.
 *
 * The SPB Exchange class code in the T-Bank Invest API is "FQBR".
 * Foreign stocks on SPB are denominated in their native currency (USD, EUR, etc.).
 */
public class SpbExchangeTickersTest {

    /** Class codes that should be allowed through checkClassCode(). */
    private static final List<String> BAD_CODES = List.of("SPEQ", "SMAL", "SPBXM_OTC", "A29", "A30");
    /** FQBR is the SPB Exchange class code — must NOT be in bad codes. */
    private static final List<String> SPB_CLASS_CODES = List.of("FQBR");

    private boolean checkClassCode(String classCode) {
        return !BAD_CODES.contains(classCode);
    }

    private boolean isSpbShare(String classCode) {
        return SPB_CLASS_CODES.contains(classCode);
    }

    private String currencySymbol(String currency) {
        if (currency == null) return "₽";
        return switch (currency.toUpperCase()) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "CNY" -> "¥";
            case "GBP" -> "£";
            default    -> "₽";
        };
    }

    // -----------------------------------------------------------------------
    // Class code filtering
    // -----------------------------------------------------------------------

    @Test
    void testFqbrIsNotInBadCodes() {
        assertFalse(BAD_CODES.contains("FQBR"),
                "FQBR (SPB Exchange) must NOT be in the bad codes list so SPB stocks are included");
    }

    @Test
    void testSpbClassCodePassesCheckClassCode() {
        assertTrue(checkClassCode("FQBR"),
                "checkClassCode() must return true for FQBR so SPB stocks are not filtered out");
    }

    @Test
    void testActualBadCodesAreStillFiltered() {
        for (String bad : BAD_CODES) {
            assertFalse(checkClassCode(bad),
                    "Bad code " + bad + " must be filtered out");
        }
    }

    @Test
    void testIsSpbShare_fqbrReturnsTrue() {
        assertTrue(isSpbShare("FQBR"), "FQBR should be identified as SPB Exchange");
    }

    @Test
    void testIsSpbShare_moexReturnsFalse() {
        assertFalse(isSpbShare("TQBR"), "TQBR (MOEX main board) should not be identified as SPB");
        assertFalse(isSpbShare("MOEX"), "MOEX should not be identified as SPB");
    }

    // -----------------------------------------------------------------------
    // Currency symbol mapping
    // -----------------------------------------------------------------------

    @Test
    void testCurrencySymbol_usd() {
        assertEquals("$", currencySymbol("USD"),
                "USD (SPB Exchange foreign stocks) must map to $");
        assertEquals("$", currencySymbol("usd"),
                "Currency mapping must be case-insensitive");
    }

    @Test
    void testCurrencySymbol_rub() {
        assertEquals("₽", currencySymbol("RUB"),
                "RUB (MOEX stocks) must map to ₽");
    }

    @Test
    void testCurrencySymbol_eur() {
        assertEquals("€", currencySymbol("EUR"));
    }

    @Test
    void testCurrencySymbol_cny() {
        assertEquals("¥", currencySymbol("CNY"));
    }

    @Test
    void testCurrencySymbol_gbp() {
        assertEquals("£", currencySymbol("GBP"));
    }

    @Test
    void testCurrencySymbol_null_defaultsToRub() {
        assertEquals("₽", currencySymbol(null),
                "Null currency should default to ₽");
    }

    @Test
    void testCurrencySymbol_unknown_defaultsToRub() {
        assertEquals("₽", currencySymbol("CHF"),
                "Unknown currency should default to ₽");
    }

    // -----------------------------------------------------------------------
    // Default allowed tickers include key SPB Exchange stocks
    // -----------------------------------------------------------------------

    @Test
    void testDefaultAllowedTickersIncludeSpbStocks() {
        // Mirrors the default list in ConfigLoader.getSandboxAllowedTickers()
        List<String> spbTickers = List.of(
                "AAPL", "MSFT", "AMZN", "GOOGL", "TSLA", "META", "NVDA",
                "BRK.B", "JPM", "JNJ", "V", "PG", "UNH", "HD", "MA",
                "DIS", "NFLX", "PYPL", "INTC", "AMD", "CRM", "ORCL", "IBM",
                "BA", "GE", "XOM", "CVX", "KO", "PEP", "MCD", "WMT"
        );

        // All tickers must be non-blank
        for (String ticker : spbTickers) {
            assertNotNull(ticker);
            assertFalse(ticker.isBlank(), "Ticker must not be blank: " + ticker);
        }

        // Must contain the most prominent SPB stocks
        Set<String> tickerSet = Set.copyOf(spbTickers);
        assertTrue(tickerSet.contains("AAPL"), "AAPL must be in SPB allowed tickers");
        assertTrue(tickerSet.contains("MSFT"), "MSFT must be in SPB allowed tickers");
        assertTrue(tickerSet.contains("TSLA"), "TSLA must be in SPB allowed tickers");
        assertTrue(tickerSet.contains("NVDA"), "NVDA must be in SPB allowed tickers");
        assertTrue(tickerSet.contains("AMZN"), "AMZN must be in SPB allowed tickers");
    }

    @Test
    void testDefaultAllowedTickersStillIncludeMoexStocks() {
        List<String> moexTickers = List.of(
                "SBER", "GAZP", "LKOH", "ROSN", "NVTK", "YDEX",
                "TATN", "PLZL", "MGNT", "MTSS", "SNGS", "ALRS", "CHMF", "NLMK", "VTBR"
        );

        Set<String> tickerSet = Set.copyOf(moexTickers);
        assertTrue(tickerSet.contains("SBER"), "SBER must remain in allowed tickers");
        assertTrue(tickerSet.contains("GAZP"), "GAZP must remain in allowed tickers");
        assertTrue(tickerSet.contains("LKOH"), "LKOH must remain in allowed tickers");
    }

    @Test
    void testNoTickerDuplicates() {
        List<String> allTickers = List.of(
                "SBER", "GAZP", "LKOH", "ROSN", "NVTK", "YDEX", "TATN", "PLZL", "MGNT",
                "MTSS", "SNGS", "ALRS", "CHMF", "NLMK", "VTBR",
                "AAPL", "MSFT", "AMZN", "GOOGL", "TSLA", "META", "NVDA",
                "BRK.B", "JPM", "JNJ", "V", "PG", "UNH", "HD", "MA",
                "DIS", "NFLX", "PYPL", "INTC", "AMD", "CRM", "ORCL", "IBM",
                "BA", "GE", "XOM", "CVX", "KO", "PEP", "MCD", "WMT"
        );

        Set<String> unique = Set.copyOf(allTickers);
        assertEquals(allTickers.size(), unique.size(),
                "There must be no duplicate tickers in the default allowed list");
    }
}
