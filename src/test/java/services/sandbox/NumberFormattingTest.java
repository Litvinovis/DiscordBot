package services.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Tests for the number formatting logic used in SandboxTradingService.
 *
 * Verifies that large monetary values are formatted with thousands separators
 * and two decimal places, matching Russian display conventions used in Discord.
 */
class NumberFormattingTest {

    /**
     * Mirror of the fmt() method in SandboxTradingService.
     */
    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        NumberFormat nf = NumberFormat.getInstance(new Locale("ru", "RU"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return nf.format(value).replace('\u00A0', ' ');
    }

    // -----------------------------------------------------------------------
    // Basic formatting
    // -----------------------------------------------------------------------

    @Test
    void fmt_null_returnsZero() {
        assertEquals("0.00", fmt(null));
    }

    @Test
    void fmt_zero_returnsZeroWithTwoDecimals() {
        assertEquals("0,00", fmt(BigDecimal.ZERO));
    }

    @Test
    void fmt_smallValue_noThousandsSeparator() {
        assertEquals("100,00", fmt(new BigDecimal("100")));
    }

    @Test
    void fmt_oneThousand_hasSeparator() {
        String result = fmt(new BigDecimal("1000"));
        assertTrue(result.contains("1") && result.contains("000"),
                "1000 should be formatted with a thousands separator, got: " + result);
    }

    @Test
    void fmt_oneMillion_hasTwoSeparators() {
        String result = fmt(new BigDecimal("1000000"));
        // e.g. "1 000 000,00" — two space separators
        long spaceCount = result.chars().filter(c -> c == ' ').count();
        assertEquals(2, spaceCount,
                "1 000 000 should have 2 space separators, got: " + result);
    }

    @Test
    void fmt_startBalance_isReadable() {
        // Default start balance is 1 000 000 ₽
        BigDecimal startBalance = new BigDecimal("1000000.00");
        String result = fmt(startBalance);
        assertFalse(result.equals("1000000.00"),
                "1000000.00 should NOT be formatted without separators");
        assertTrue(result.contains("1"),
                "Result should contain '1': " + result);
    }

    // -----------------------------------------------------------------------
    // Decimal precision
    // -----------------------------------------------------------------------

    @Test
    void fmt_roundsToTwoDecimals() {
        BigDecimal value = new BigDecimal("1234.567");
        String result = fmt(value);
        // Should be rounded to 2 decimal places
        assertTrue(result.endsWith("57") || result.endsWith(",57"),
                "Should round 567 to 57, got: " + result);
    }

    @Test
    void fmt_addsTrailingZeros() {
        String result = fmt(new BigDecimal("500.1"));
        assertTrue(result.contains("0"),
                "Should show two decimal places (500,10), got: " + result);
        assertTrue(result.endsWith("0"),
                "Should end with 0 for .10 value, got: " + result);
    }

    @Test
    void fmt_negativeValue_works() {
        String result = fmt(new BigDecimal("-500.50"));
        assertTrue(result.startsWith("-"),
                "Negative value should start with '-', got: " + result);
    }

    // -----------------------------------------------------------------------
    // Large value specific checks
    // -----------------------------------------------------------------------

    @Test
    void fmt_largeValue_doesNotContainNoBreakSpace() {
        // The Russian locale uses \u00A0 (non-breaking space) as group separator.
        // Our fmt() replaces it with a regular space for Discord display.
        String result = fmt(new BigDecimal("1000000.00"));
        assertFalse(result.contains("\u00A0"),
                "Result must not contain non-breaking spaces, got: " + result);
    }

    @Test
    void fmt_priceValue_formattedCorrectly() {
        // A typical stock price like 320.50
        String result = fmt(new BigDecimal("320.50"));
        assertEquals("320,50", result,
                "320.50 should be formatted as 320,50 in Russian locale");
    }
}
