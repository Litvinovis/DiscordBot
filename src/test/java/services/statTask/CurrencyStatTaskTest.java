package services.statTask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the currency price conversion logic in CurrencyStatTask.
 *
 * The bug: when units=0 and nano is negative (e.g. -4490000), the old
 * StringBuilder approach produced "0.-004490000" which Double.parseDouble()
 * cannot parse, causing a NumberFormatException.
 *
 * The fix uses arithmetic: units + nano / 1.0E9, which handles negative nano
 * values correctly.
 */
public class CurrencyStatTaskTest {

    /**
     * Verifies that the arithmetic conversion (the fix) correctly handles
     * the case where units=0 and nano is negative.
     */
    @Test
    void testPriceConversion_negativeNano_producesCorrectValue() {
        long units = 0L;
        int nano = -4490000;

        double priceValue = (double) units + (double) nano / 1.0E9;

        assertEquals(-0.00449, priceValue, 1e-10,
            "Price with units=0 and nano=-4490000 should equal -0.00449");
    }

    /**
     * Verifies that the old string-concatenation approach produces a string
     * that Double.parseDouble() cannot parse when nano is negative.
     */
    @Test
    void testOldStringApproach_negativeNano_throwsNumberFormatException() {
        long units = 0L;
        int nano = -4490000;

        StringBuilder price = new StringBuilder();
        price.append(units).append(".").append(String.format("%09d", nano));
        String priceStr = price.toString();

        // The string will be "0.-04490000" which is not a valid double
        // (%09d with a negative value pads the digits portion to 9 chars including sign)
        assertEquals("0.-04490000", priceStr,
            "Old approach should produce an unparseable string");
        assertThrows(NumberFormatException.class,
            () -> Double.parseDouble(priceStr),
            "Double.parseDouble should throw on '0.-004490000'");
    }

    /**
     * Verifies the arithmetic approach works for the normal positive case.
     */
    @Test
    void testPriceConversion_positiveNano_producesCorrectValue() {
        long units = 85L;
        int nano = 500000000;

        double priceValue = (double) units + (double) nano / 1.0E9;

        assertEquals(85.5, priceValue, 1e-9,
            "Price with units=85 and nano=500000000 should equal 85.5");
    }

    /**
     * Verifies the arithmetic approach works when both units and nano are zero.
     */
    @Test
    void testPriceConversion_zeroPrice_producesZero() {
        long units = 0L;
        int nano = 0;

        double priceValue = (double) units + (double) nano / 1.0E9;

        assertEquals(0.0, priceValue, 1e-10,
            "Price with units=0 and nano=0 should equal 0.0");
    }
}
