package services.statTask;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure business logic embedded in {@link SharesStatTask}.
 *
 * Because SharesStatTask requires live JDA + TInvestApi wiring, we duplicate
 * only the pure helper methods here and test them directly.  This mirrors the
 * pattern used in SandboxTradingLogicTest.
 *
 * Methods under test:
 *  - calculateChanges()    — percentage change calculation
 *  - partitionList()       — chunking helper
 *  - sortChanges()         — descending sort by value
 *  - appendTopPerformers() — best-N and worst-N display logic
 *  - checkClassCode()      — bad-code filter
 */
class SharesStatTaskLogicTest {

	private static final int SCALE = 8;
	private static final List<String> BAD_CODE = List.of("SPEQ", "SMAL", "SPBXM_OTC", "A29", "A30");

	// -----------------------------------------------------------------------
	// Helpers duplicated from SharesStatTask (private methods)
	// -----------------------------------------------------------------------

	private Map<String, BigDecimal> calculateChanges(
			Map<String, BigDecimal> currentData, Map<String, BigDecimal> oldData) {
		Map<String, BigDecimal> changes = new HashMap<>();
		for (Map.Entry<String, BigDecimal> entry : currentData.entrySet()) {
			String name = entry.getKey();
			if (!oldData.containsKey(name)) continue;
			BigDecimal oldValue = oldData.get(name);
			BigDecimal newValue = entry.getValue();
			if (oldValue.compareTo(BigDecimal.ZERO) == 0) continue;
			BigDecimal change = newValue.subtract(oldValue)
					.divide(oldValue, SCALE, RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100))
					.setScale(2, RoundingMode.HALF_UP);
			changes.put(name, change);
		}
		return changes;
	}

	private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
		List<List<T>> chunks = new ArrayList<>();
		for (int i = 0; i < list.size(); i += chunkSize) {
			int end = Math.min(list.size(), i + chunkSize);
			chunks.add(list.subList(i, end));
		}
		return chunks;
	}

	private Map<String, BigDecimal> sortChanges(Map<String, BigDecimal> changes) {
		return changes.entrySet().stream()
				.sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(ov, nv) -> ov, LinkedHashMap::new));
	}

	private boolean checkClassCode(String classCode) {
		return !BAD_CODE.contains(classCode);
	}

	// -----------------------------------------------------------------------
	// calculateChanges tests
	// -----------------------------------------------------------------------

	@Test
	void calculateChanges_positiveGain() {
		Map<String, BigDecimal> old = Map.of("SBER", new BigDecimal("300.00"));
		Map<String, BigDecimal> current = Map.of("SBER", new BigDecimal("315.00"));

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertTrue(changes.containsKey("SBER"));
		assertEquals(0, new BigDecimal("5.00").compareTo(changes.get("SBER")),
				"5% gain expected when price moves from 300 to 315");
	}

	@Test
	void calculateChanges_negativeLoss() {
		Map<String, BigDecimal> old = Map.of("GAZP", new BigDecimal("200.00"));
		Map<String, BigDecimal> current = Map.of("GAZP", new BigDecimal("180.00"));

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertEquals(0, new BigDecimal("-10.00").compareTo(changes.get("GAZP")),
				"-10% loss expected when price drops from 200 to 180");
	}

	@Test
	void calculateChanges_zeroOldValue_skipped() {
		// Avoid division by zero — entry with oldValue=0 must be skipped
		Map<String, BigDecimal> old = Map.of("ZERO", BigDecimal.ZERO);
		Map<String, BigDecimal> current = Map.of("ZERO", new BigDecimal("100.00"));

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertFalse(changes.containsKey("ZERO"),
				"Entries with oldValue=0 must be skipped to avoid division by zero");
	}

	@Test
	void calculateChanges_newTickerNotInOld_skipped() {
		Map<String, BigDecimal> old = Map.of("SBER", new BigDecimal("300.00"));
		Map<String, BigDecimal> current = new HashMap<>();
		current.put("SBER", new BigDecimal("310.00"));
		current.put("NVDA", new BigDecimal("800.00")); // not in old

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertTrue(changes.containsKey("SBER"));
		assertFalse(changes.containsKey("NVDA"),
				"Tickers absent from oldData must not appear in changes");
	}

	@Test
	void calculateChanges_unchangedPrice_isZeroPercent() {
		Map<String, BigDecimal> old = Map.of("LKOH", new BigDecimal("7500.00"));
		Map<String, BigDecimal> current = Map.of("LKOH", new BigDecimal("7500.00"));

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertEquals(0, BigDecimal.ZERO.compareTo(changes.get("LKOH")),
				"0% change when price is identical");
	}

	@Test
	void calculateChanges_multipleSymbols() {
		Map<String, BigDecimal> old = new HashMap<>();
		old.put("A", new BigDecimal("100.00"));
		old.put("B", new BigDecimal("50.00"));
		old.put("C", new BigDecimal("200.00"));

		Map<String, BigDecimal> current = new HashMap<>();
		current.put("A", new BigDecimal("110.00")); // +10%
		current.put("B", new BigDecimal("45.00"));  // -10%
		current.put("C", new BigDecimal("200.00")); //   0%

		Map<String, BigDecimal> changes = calculateChanges(current, old);

		assertEquals(3, changes.size());
		assertEquals(0, new BigDecimal("10.00").compareTo(changes.get("A")));
		assertEquals(0, new BigDecimal("-10.00").compareTo(changes.get("B")));
		assertEquals(0, BigDecimal.ZERO.compareTo(changes.get("C")));
	}

	// -----------------------------------------------------------------------
	// partitionList tests
	// -----------------------------------------------------------------------

	@Test
	void partitionList_evenDivision() {
		List<Integer> input = List.of(1, 2, 3, 4, 5, 6);
		List<List<Integer>> chunks = partitionList(input, 2);
		assertEquals(3, chunks.size());
		assertEquals(List.of(1, 2), chunks.get(0));
		assertEquals(List.of(5, 6), chunks.get(2));
	}

	@Test
	void partitionList_unevenDivision_lastChunkSmaller() {
		List<Integer> input = List.of(1, 2, 3, 4, 5);
		List<List<Integer>> chunks = partitionList(input, 2);
		assertEquals(3, chunks.size());
		assertEquals(1, chunks.get(2).size(), "Last chunk must hold the remainder");
	}

	@Test
	void partitionList_emptyList_returnsNoChunks() {
		List<List<Integer>> chunks = partitionList(List.of(), 100);
		assertTrue(chunks.isEmpty());
	}

	@Test
	void partitionList_chunkSizeLargerThanList_singleChunk() {
		List<String> input = List.of("A", "B", "C");
		List<List<String>> chunks = partitionList(input, 1000);
		assertEquals(1, chunks.size());
		assertEquals(3, chunks.get(0).size());
	}

	@Test
	void partitionList_chunkSizeOne_eachElementItsOwnChunk() {
		List<String> input = List.of("X", "Y", "Z");
		List<List<String>> chunks = partitionList(input, 1);
		assertEquals(3, chunks.size());
		for (List<String> chunk : chunks) {
			assertEquals(1, chunk.size());
		}
	}

	// -----------------------------------------------------------------------
	// sortChanges tests
	// -----------------------------------------------------------------------

	@Test
	void sortChanges_descendingOrder() {
		Map<String, BigDecimal> changes = new HashMap<>();
		changes.put("A", new BigDecimal("3.00"));
		changes.put("B", new BigDecimal("10.00"));
		changes.put("C", new BigDecimal("-5.00"));

		Map<String, BigDecimal> sorted = sortChanges(changes);
		List<String> keys = new ArrayList<>(sorted.keySet());

		assertEquals("B", keys.get(0), "Highest gainer must be first");
		assertEquals("A", keys.get(1));
		assertEquals("C", keys.get(2), "Worst performer must be last");
	}

	@Test
	void sortChanges_preservesAllEntries() {
		Map<String, BigDecimal> changes = Map.of(
				"X", new BigDecimal("1.00"),
				"Y", new BigDecimal("2.00")
		);
		Map<String, BigDecimal> sorted = sortChanges(changes);
		assertEquals(2, sorted.size());
	}

	// -----------------------------------------------------------------------
	// checkClassCode tests
	// -----------------------------------------------------------------------

	@Test
	void checkClassCode_fqbrIsAllowed() {
		assertTrue(checkClassCode("FQBR"),
				"FQBR (SPB Exchange) must pass the class-code filter");
	}

	@Test
	void checkClassCode_tqbrIsAllowed() {
		assertTrue(checkClassCode("TQBR"),
				"TQBR (MOEX main board) must pass the class-code filter");
	}

	@Test
	void checkClassCode_badCodesAreFiltered() {
		for (String code : BAD_CODE) {
			assertFalse(checkClassCode(code),
					"Bad code '" + code + "' must be rejected");
		}
	}

	@Test
	void checkClassCode_unknownCodeIsAllowed() {
		// Unknown codes should pass through (whitelist of BAD only)
		assertTrue(checkClassCode("UNKNOWN_CODE"));
	}

	// -----------------------------------------------------------------------
	// appendTopPerformers logic (tested via direct calculation)
	// -----------------------------------------------------------------------

	@Test
	void topPerformers_bestFirst_picksHighestN() {
		// Simulate what appendTopPerformers does for bestFirst=true
		Map<String, BigDecimal> sorted = sortChanges(Map.of(
				"W", new BigDecimal("15.00"),
				"X", new BigDecimal("10.00"),
				"Y", new BigDecimal("5.00"),
				"Z", new BigDecimal("-3.00")
		));
		List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sorted.entrySet());
		int n = Math.min(2, entries.size());

		List<String> top = new ArrayList<>();
		for (int i = 0; i < n; i++) top.add(entries.get(i).getKey());

		assertEquals(List.of("W", "X"), top, "Top-2 best performers must be W and X");
	}

	@Test
	void topPerformers_worstFirst_picksLowestN() {
		Map<String, BigDecimal> sorted = sortChanges(Map.of(
				"W", new BigDecimal("15.00"),
				"X", new BigDecimal("10.00"),
				"Y", new BigDecimal("-2.00"),
				"Z", new BigDecimal("-8.00")
		));
		List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sorted.entrySet());
		int count = 2;
		int startIndex = Math.max(0, entries.size() - count);

		List<String> worst = new ArrayList<>();
		for (int i = entries.size() - 1; i >= startIndex; i--) {
			worst.add(entries.get(i).getKey());
		}

		assertEquals(List.of("Z", "Y"), worst, "Worst-2 must be Z and Y");
	}

	@Test
	void topPerformers_emptyMap_producesNoEntries() {
		Map<String, BigDecimal> sorted = sortChanges(Map.of());
		assertTrue(sorted.isEmpty());
	}

	// -----------------------------------------------------------------------
	// Price conversion (BigDecimal.valueOf nano, 9) — mirrors SharesStatTask fix
	// -----------------------------------------------------------------------

	@Test
	void priceConversion_nanoScaleOf9_isCorrect() {
		// BigDecimal.valueOf(nano, 9) moves decimal 9 places — same as /1e9
		long units = 150L;
		int nano = 750_000_000; // 0.75
		BigDecimal price = BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
		assertEquals(0, new BigDecimal("150.75").compareTo(price.setScale(2, RoundingMode.HALF_UP)));
	}

	@Test
	void priceConversion_negativeNanoWithBigDecimalApproach_isCorrect() {
		// Verify the BigDecimal approach handles negative nano correctly (unlike the string bug)
		long units = 0L;
		int nano = -4_490_000;
		BigDecimal price = BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
		// -4490000 * 1e-9 = -0.00449
		assertTrue(price.compareTo(BigDecimal.ZERO) < 0,
				"Negative nano must produce a negative price component");
		assertEquals(0, new BigDecimal("-0.00449").compareTo(price.setScale(5, RoundingMode.HALF_UP)));
	}
}
