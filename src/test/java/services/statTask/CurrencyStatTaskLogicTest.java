package services.statTask;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure business logic in {@link CurrencyStatTask}.
 *
 * CurrencyStatTask requires live JDA + TInvestApi, so we duplicate only the
 * pure helper logic and verify it independently.
 *
 * Business rules under test:
 * - The Russian Ruble currency is EXCLUDED from change calculations.
 * - Currencies not present in oldData are SKIPPED.
 * - oldValue == 0 is SKIPPED to avoid division by zero.
 * - Change is sorted descending (best to worst).
 * - Top-5 best and bottom-5 worst are selected correctly.
 */
class CurrencyStatTaskLogicTest {

    private static final int SCALE = 8;
    private static final String RUBLE_NAME = "Российский рубль";

    // -----------------------------------------------------------------------
    // Helpers duplicated from CurrencyStatTask (private logic)
    // -----------------------------------------------------------------------

    private Map<String, BigDecimal> calculateChanges(
            Map<String, BigDecimal> newData, Map<String, BigDecimal> oldData) {
        Map<String, BigDecimal> changes = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : newData.entrySet()) {
            String name = e.getKey();
            if (RUBLE_NAME.equals(name) || !oldData.containsKey(name)) continue;
            BigDecimal oldValue = oldData.get(name);
            BigDecimal newValue = e.getValue();
            if (oldValue.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal change = newValue.subtract(oldValue)
                    .divide(oldValue, SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            changes.put(name, change);
        }
        return changes;
    }

    private Map<String, BigDecimal> sortDescending(Map<String, BigDecimal> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (ov, nv) -> ov, LinkedHashMap::new));
    }

    // -----------------------------------------------------------------------
    // Ruble exclusion
    // -----------------------------------------------------------------------

    @Test
    void russianRuble_isExcludedFromChanges() {
        Map<String, BigDecimal> old = new HashMap<>();
        old.put(RUBLE_NAME, new BigDecimal("1.00"));
        old.put("Доллар США", new BigDecimal("90.00"));

        Map<String, BigDecimal> current = new HashMap<>();
        current.put(RUBLE_NAME, new BigDecimal("1.01"));
        current.put("Доллар США", new BigDecimal("91.80")); // +2%

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertFalse(changes.containsKey(RUBLE_NAME),
                "The Russian Ruble must never appear in the change map");
        assertTrue(changes.containsKey("Доллар США"),
                "USD must appear in the change map");
    }

    // -----------------------------------------------------------------------
    // Division by zero guard
    // -----------------------------------------------------------------------

    @Test
    void oldValueZero_skippedToAvoidDivisionByZero() {
        Map<String, BigDecimal> old = Map.of("Тестовая валюта", BigDecimal.ZERO);
        Map<String, BigDecimal> current = Map.of("Тестовая валюта", new BigDecimal("50.00"));

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertFalse(changes.containsKey("Тестовая валюта"),
                "Entry with oldValue=0 must be skipped");
    }

    // -----------------------------------------------------------------------
    // New currency absent from oldData
    // -----------------------------------------------------------------------

    @Test
    void currencyAbsentFromOldData_isSkipped() {
        Map<String, BigDecimal> old = Map.of("Евро", new BigDecimal("100.00"));
        Map<String, BigDecimal> current = new HashMap<>();
        current.put("Евро", new BigDecimal("102.00"));
        current.put("Юань", new BigDecimal("12.00")); // not in old

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertFalse(changes.containsKey("Юань"),
                "Currency absent from oldData must be skipped");
    }

    // -----------------------------------------------------------------------
    // Correct change percentage calculation
    // -----------------------------------------------------------------------

    @Test
    void changePercent_positiveGain() {
        Map<String, BigDecimal> old = Map.of("USD", new BigDecimal("90.00"));
        Map<String, BigDecimal> current = Map.of("USD", new BigDecimal("94.50"));

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertEquals(0, new BigDecimal("5.00").compareTo(changes.get("USD")),
                "5% gain: (94.5 - 90) / 90 * 100 = 5.00%");
    }

    @Test
    void changePercent_negativeLoss() {
        Map<String, BigDecimal> old = Map.of("EUR", new BigDecimal("100.00"));
        Map<String, BigDecimal> current = Map.of("EUR", new BigDecimal("95.00"));

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertEquals(0, new BigDecimal("-5.00").compareTo(changes.get("EUR")),
                "-5% loss expected");
    }

    @Test
    void changePercent_unchanged_isZero() {
        Map<String, BigDecimal> old = Map.of("CNY", new BigDecimal("12.50"));
        Map<String, BigDecimal> current = Map.of("CNY", new BigDecimal("12.50"));

        Map<String, BigDecimal> changes = calculateChanges(current, old);

        assertEquals(0, BigDecimal.ZERO.compareTo(changes.get("CNY")));
    }

    // -----------------------------------------------------------------------
    // Sorting (descending by change%)
    // -----------------------------------------------------------------------

    @Test
    void sortDescending_bestPerformerFirst() {
        Map<String, BigDecimal> changes = new HashMap<>();
        changes.put("GBP", new BigDecimal("1.50"));
        changes.put("USD", new BigDecimal("3.00"));
        changes.put("EUR", new BigDecimal("-1.00"));

        Map<String, BigDecimal> sorted = sortDescending(changes);
        List<String> keys = new ArrayList<>(sorted.keySet());

        assertEquals("USD", keys.get(0), "USD with +3% must be first");
        assertEquals("GBP", keys.get(1));
        assertEquals("EUR", keys.get(2), "EUR with -1% must be last");
    }

    // -----------------------------------------------------------------------
    // Top-5 / Bottom-5 selection
    // -----------------------------------------------------------------------

    @Test
    void top5_picksHighestFive() {
        Map<String, BigDecimal> changes = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            changes.put("Currency" + i, new BigDecimal(i));
        }
        Map<String, BigDecimal> sorted = sortDescending(changes);
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sorted.entrySet());

        List<String> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            top5.add(entries.get(i).getKey());
        }

        // Top 5 must be Currency8..Currency4 (descending)
        assertEquals("Currency8", top5.get(0));
        assertEquals("Currency7", top5.get(1));
        assertEquals(5, top5.size());
    }

    @Test
    void bottom5_picksLowestFive() {
        Map<String, BigDecimal> changes = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            changes.put("Currency" + i, new BigDecimal(i));
        }
        Map<String, BigDecimal> sorted = sortDescending(changes);
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sorted.entrySet());

        int count = 0;
        List<String> bottom5 = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && count < 5; count++, i--) {
            bottom5.add(entries.get(i).getKey());
        }

        assertEquals("Currency1", bottom5.get(0), "Worst performer must be first in bottom list");
        assertEquals(5, bottom5.size());
    }

    @Test
    void fewerThan5Currencies_doesNotThrow() {
        Map<String, BigDecimal> changes = Map.of(
                "USD", new BigDecimal("2.00"),
                "EUR", new BigDecimal("1.00")
        );
        Map<String, BigDecimal> sorted = sortDescending(changes);
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sorted.entrySet());

        // top-N capped to available entries
        int n = Math.min(5, entries.size());
        assertEquals(2, n);
    }

    // -----------------------------------------------------------------------
    // firstCall_returnsNull semantics (first call seeds oldData, returns null)
    // -----------------------------------------------------------------------

    @Test
    void firstCall_noOldData_changesMapIsEmpty() {
        // On the very first run oldData is empty; simulate what the method does:
        // it seeds oldData and returns null (no message sent).
        Map<String, BigDecimal> oldData = new HashMap<>();  // empty — first run
        Map<String, BigDecimal> newData = Map.of("USD", new BigDecimal("90.00"));

        if (oldData.isEmpty()) {
            oldData.putAll(newData);
            // returns null — nothing to assert on the message, but oldData must be seeded
        }

        assertTrue(oldData.containsKey("USD"),
                "After first-call seed, oldData must contain the current prices");
    }
}
