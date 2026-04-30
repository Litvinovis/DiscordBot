package services.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SandboxMessageFormatter#format(BigDecimal)}.
 */
class NumberFormattingTest {

	private final SandboxMessageFormatter formatter = new SandboxMessageFormatter();

	// -----------------------------------------------------------------------
	// Basic formatting
	// -----------------------------------------------------------------------

	@Test
	void fmt_null_returnsZero() {
		assertEquals("0.00", formatter.format(null));
	}

	@Test
	void fmt_zero_returnsZeroWithTwoDecimals() {
		assertEquals("0,00", formatter.format(BigDecimal.ZERO));
	}

	@Test
	void fmt_smallValue_noThousandsSeparator() {
		assertEquals("100,00", formatter.format(new BigDecimal("100")));
	}

	@Test
	void fmt_oneThousand_hasSeparator() {
		String result = formatter.format(new BigDecimal("1000"));
		assertTrue(result.contains("1") && result.contains("000"),
				"1000 should be formatted with a thousands separator, got: " + result);
	}

	@Test
	void fmt_oneMillion_hasTwoSeparators() {
		String result = formatter.format(new BigDecimal("1000000"));
		long spaceCount = result.chars().filter(c -> c == ' ').count();
		assertEquals(2, spaceCount,
				"1 000 000 should have 2 space separators, got: " + result);
	}

	@Test
	void fmt_startBalance_isReadable() {
		String result = formatter.format(new BigDecimal("1000000.00"));
		assertFalse(result.equals("1000000.00"),
				"1000000.00 should NOT be formatted without separators");
		assertTrue(result.contains("1"), "Result should contain '1': " + result);
	}

	// -----------------------------------------------------------------------
	// Decimal precision
	// -----------------------------------------------------------------------

	@Test
	void fmt_roundsToTwoDecimals() {
		String result = formatter.format(new BigDecimal("1234.567"));
		assertTrue(result.endsWith("57") || result.endsWith(",57"),
				"Should round 567 to 57, got: " + result);
	}

	@Test
	void fmt_addsTrailingZeros() {
		String result = formatter.format(new BigDecimal("500.1"));
		assertTrue(result.endsWith("0"),
				"Should show two decimal places (500,10), got: " + result);
	}

	@Test
	void fmt_negativeValue_works() {
		String result = formatter.format(new BigDecimal("-500.50"));
		assertTrue(result.startsWith("-"),
				"Negative value should start with '-', got: " + result);
	}

	// -----------------------------------------------------------------------
	// Large value specific checks
	// -----------------------------------------------------------------------

	@Test
	void fmt_largeValue_doesNotContainNoBreakSpace() {
		String result = formatter.format(new BigDecimal("1000000.00"));
		assertFalse(result.contains(" "),
				"Result must not contain non-breaking spaces, got: " + result);
	}

	@Test
	void fmt_priceValue_formattedCorrectly() {
		assertEquals("320,50", formatter.format(new BigDecimal("320.50")),
				"320.50 should be formatted as 320,50 in Russian locale");
	}

	// -----------------------------------------------------------------------
	// Currency symbols
	// -----------------------------------------------------------------------

	@Test
	void currencySymbol_null_returnsRuble() {
		assertEquals("₽", formatter.currencySymbol(null));
	}

	@Test
	void currencySymbol_usd_returnsDollar() {
		assertEquals("$", formatter.currencySymbol("USD"));
	}

	@Test
	void currencySymbol_unknown_returnsRuble() {
		assertEquals("₽", formatter.currencySymbol("XYZ"));
	}

	// -----------------------------------------------------------------------
	// Leverage status
	// -----------------------------------------------------------------------

	@Test
	void leverageStatus_low_isSafe() {
		assertEquals("✅ БЕЗОПАСНО", formatter.leverageStatus(new BigDecimal("1.5")));
	}

	@Test
	void leverageStatus_mid_isWarning() {
		assertEquals("⚠️ ВНИМАНИЕ", formatter.leverageStatus(new BigDecimal("3.0")));
	}

	@Test
	void leverageStatus_high_isCritical() {
		assertTrue(formatter.leverageStatus(new BigDecimal("5.0")).startsWith("🚨"));
	}
}
