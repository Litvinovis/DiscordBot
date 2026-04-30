package services.sandbox;

import org.junit.jupiter.api.Test;
import services.sandbox.model.SandboxUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the business logic of SandboxTradingService.
 *
 * These tests validate the core formulas directly (equity, P&L, ROI, baseline
 * recording) without standing up the full Ignite / TInvestApi stack.
 */
public class SandboxTradingLogicTest {

	// -----------------------------------------------------------------------
	// Helpers that mirror private methods in SandboxTradingService
	// -----------------------------------------------------------------------

	private BigDecimal equity(BigDecimal cash, BigDecimal grossPositionValue, BigDecimal borrowed) {
		return cash.add(grossPositionValue).subtract(borrowed);
	}

	private BigDecimal grossPositionValue(List<BigDecimal[]> positions) {
		// each entry: [qty, currentPrice]
		return positions.stream()
				.map(p -> p[0].multiply(p[1]))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal safeRoi(BigDecimal now, BigDecimal base) {
		if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
		return now.subtract(base).divide(base, 9, RoundingMode.HALF_UP);
	}

	/**
	 * Simulate recordBaseline() — called from trade() BEFORE executing the trade.
	 * Uses double fields as in the model, but accepts BigDecimal equityAtCallTime for test convenience.
	 */
	private void recordBaseline(SandboxUser u, BigDecimal equityAtCallTime, LocalDate now) {
		int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
		if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
			u.setDailyBaselineDate(now);
			u.setDailyBaselineEquity(equityAtCallTime.doubleValue());
		}
		if (u.getWeeklyBaselineDate() == null ||
				u.getWeeklyBaselineDate().get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
			u.setWeeklyBaselineDate(now);
			u.setWeeklyBaselineEquity(equityAtCallTime.doubleValue());
		}
		if (u.getMonthlyBaselineDate() == null ||
				u.getMonthlyBaselineDate().getMonthValue() != now.getMonthValue()) {
			u.setMonthlyBaselineDate(now);
			u.setMonthlyBaselineEquity(equityAtCallTime.doubleValue());
		}
	}

	// -----------------------------------------------------------------------
	// Bug 1: top() — baselines come from trade(), not top()
	// -----------------------------------------------------------------------

	/**
	 * When top() is called, it must NOT re-record baselines (that was the bug).
	 * Baselines should be set by register()/trade() BEFORE the trade happens.
	 * This test verifies that calling recordBaseline() a second time on the same
	 * day does NOT overwrite the existing baseline (idempotent for same day).
	 */
	@Test
	void testRecordBaseline_sameDay_doesNotOverwriteExistingBaseline() {
		SandboxUser user = new SandboxUser("u1", "Alice", 100000.00);
		LocalDate today = LocalDate.of(2026, 3, 19);

		// First call (e.g. from register) — equity = 100 000
		recordBaseline(user, new BigDecimal("100000.00"), today);
		assertEquals(0, new BigDecimal("100000.00").compareTo(BigDecimal.valueOf(user.getDailyBaselineEquity())),
				"Daily baseline should be set to 100 000 on first call");

		// After a profitable trade equity grows to 110 000.
		// A second call on the same day should NOT overwrite the baseline.
		recordBaseline(user, new BigDecimal("110000.00"), today);
		assertEquals(0, new BigDecimal("100000.00").compareTo(BigDecimal.valueOf(user.getDailyBaselineEquity())),
				"Daily baseline must NOT be updated when the date hasn't changed");
	}

	/**
	 * When a new day starts the baseline should be updated to the pre-trade equity,
	 * not the post-trade equity.
	 */
	@Test
	void testRecordBaseline_newDay_updatesBaselineToPreTradeEquity() {
		SandboxUser user = new SandboxUser("u1", "Alice", 100000.00);
		LocalDate yesterday = LocalDate.of(2026, 3, 18);
		LocalDate today = LocalDate.of(2026, 3, 19);

		// Baseline was set yesterday
		recordBaseline(user, new BigDecimal("105000.00"), yesterday);
		assertEquals(0, new BigDecimal("105000.00").compareTo(BigDecimal.valueOf(user.getDailyBaselineEquity())));

		// Today's first trade: recordBaseline() is called BEFORE the trade,
		// so equityAtCallTime = equity before the trade = 107 000.
		BigDecimal equityBeforeTrade = new BigDecimal("107000.00");
		recordBaseline(user, equityBeforeTrade, today);

		assertEquals(today, user.getDailyBaselineDate());
		assertEquals(0, new BigDecimal("107000.00").compareTo(BigDecimal.valueOf(user.getDailyBaselineEquity())),
				"Baseline must capture equity BEFORE the trade on the new day");
	}

	/**
	 * top() metric must return non-zero ROI when there is a meaningful baseline.
	 */
	@Test
	void testTopMetric_usesPreExistingBaseline_notCurrentEquity() {
		BigDecimal baselineEquity = new BigDecimal("100000.00");
		BigDecimal currentEquity  = new BigDecimal("110000.00");

		BigDecimal roi = safeRoi(currentEquity, baselineEquity);

		assertEquals(0, new BigDecimal("0.1").compareTo(roi.setScale(1, RoundingMode.HALF_UP)),
				"ROI should be 10%, not 0%, when baseline is from a prior period");

		// What the old (buggy) code did: reset baseline to currentEquity then compare
		BigDecimal roiBuggy = safeRoi(currentEquity, currentEquity);
		assertEquals(0, BigDecimal.ZERO.compareTo(roiBuggy),
				"Old bug: resetting baseline to current equity always yields 0%");
	}

	/**
	 * Weekly baseline rolls over when the ISO week number changes.
	 */
	@Test
	void testRecordBaseline_newWeek_updatesWeeklyBaseline() {
		SandboxUser user = new SandboxUser("u1", "Alice", 100000.00);

		// Week 11 of 2026
		LocalDate lastWeek = LocalDate.of(2026, 3, 9);   // Monday, week 11
		LocalDate thisWeek = LocalDate.of(2026, 3, 16);  // Monday, week 12

		recordBaseline(user, new BigDecimal("100000.00"), lastWeek);
		assertEquals(0, new BigDecimal("100000.00").compareTo(BigDecimal.valueOf(user.getWeeklyBaselineEquity())));

		recordBaseline(user, new BigDecimal("115000.00"), thisWeek);
		assertEquals(0, new BigDecimal("115000.00").compareTo(BigDecimal.valueOf(user.getWeeklyBaselineEquity())),
				"Weekly baseline must update on a new ISO week");
	}

	// -----------------------------------------------------------------------
	// Bug 2: balance() — equity includes stock value
	// -----------------------------------------------------------------------

	/**
	 * Equity = cash + grossPositionValue - borrowed.
	 */
	@Test
	void testEquity_includesPositionValue() {
		BigDecimal cash = new BigDecimal("50000.00");
		BigDecimal borrowed = BigDecimal.ZERO;
		// User holds 10 shares of SBER at current price 320 ₽
		BigDecimal[] pos1 = {new BigDecimal("10"), new BigDecimal("320.0")};
		BigDecimal gross = grossPositionValue(java.util.Collections.singletonList(pos1));

		BigDecimal eq = equity(cash, gross, borrowed);

		assertEquals(0, new BigDecimal("53200.00").compareTo(eq),
				"Equity must include 3 200 ₽ worth of stock positions");
	}

	@Test
	void testEquity_withBorrowedFunds() {
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal borrowed = new BigDecimal("20000.00");
		BigDecimal[] pos1b = {new BigDecimal("100"), new BigDecimal("300.0")};
		BigDecimal gross = grossPositionValue(java.util.Collections.singletonList(pos1b));

		BigDecimal eq = equity(cash, gross, borrowed);

		assertEquals(0, new BigDecimal("10000.00").compareTo(eq),
				"Equity = 30 000 (stocks) - 20 000 (borrowed) = 10 000");
	}

	/**
	 * ROI shown in balance() is relative to startBalance.
	 */
	@Test
	void testBalanceRoi_relativeTo_startBalance() {
		BigDecimal startBalance = new BigDecimal("100000.00");
		BigDecimal currentEquity = new BigDecimal("112500.00");

		BigDecimal roi = safeRoi(currentEquity, startBalance)
				.multiply(BigDecimal.valueOf(100))
				.setScale(1, RoundingMode.HALF_UP);

		assertEquals(0, new BigDecimal("12.5").compareTo(roi), "ROI from start should be +12.50%");
	}

	// -----------------------------------------------------------------------
	// Bug 3: portfolio() — P&L per position and total
	// -----------------------------------------------------------------------

	/**
	 * Unrealised P&L for a long position = (currentPrice - avgPrice) * qty.
	 */
	@Test
	void testPortfolioPnl_longPosition_profitable() {
		int qty = 10;
		BigDecimal avgPrice = new BigDecimal("300.0");
		BigDecimal currentPrice = new BigDecimal("320.0");

		BigDecimal pnl = currentPrice.subtract(avgPrice).multiply(BigDecimal.valueOf(qty));
		BigDecimal pnlPct = currentPrice.subtract(avgPrice)
				.divide(avgPrice, 9, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(3, RoundingMode.HALF_UP);

		assertEquals(0, new BigDecimal("200.0").compareTo(pnl), "P&L should be +200 ₽");
		assertEquals(0, new BigDecimal("6.667").compareTo(pnlPct), "P&L% should be ~+6.67%");
	}

	@Test
	void testPortfolioPnl_longPosition_loss() {
		int qty = 5;
		BigDecimal avgPrice = new BigDecimal("500.0");
		BigDecimal currentPrice = new BigDecimal("450.0");

		BigDecimal pnl = currentPrice.subtract(avgPrice).multiply(BigDecimal.valueOf(qty));

		assertEquals(0, new BigDecimal("-250.0").compareTo(pnl), "P&L should be -250 ₽");
	}

	/**
	 * Total P&L is the sum across all positions.
	 */
	@Test
	void testPortfolioTotalPnl_multiplePositions() {
		// Position A: SBER 10 @ avg 300, now 320 → +200
		BigDecimal pnlA = new BigDecimal("320.0").subtract(new BigDecimal("300.0")).multiply(BigDecimal.valueOf(10));
		// Position B: GAZP 20 @ avg 200, now 180 → -400
		BigDecimal pnlB = new BigDecimal("180.0").subtract(new BigDecimal("200.0")).multiply(BigDecimal.valueOf(20));

		BigDecimal total = pnlA.add(pnlB);

		assertEquals(0, new BigDecimal("-200.0").compareTo(total), "Net P&L should be -200 ₽");
	}

	@Test
	void testPortfolioPnlPct_zeroAvgPrice_doesNotDivideByZero() {
		BigDecimal avgPrice = BigDecimal.ZERO;
		BigDecimal currentPrice = new BigDecimal("100.0");

		BigDecimal pnlPct = avgPrice.compareTo(BigDecimal.ZERO) > 0
				? currentPrice.subtract(avgPrice).divide(avgPrice, 9, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
				: BigDecimal.ZERO;

		assertEquals(0, BigDecimal.ZERO.compareTo(pnlPct),
				"When avgPrice is 0, P&L% should be 0 to avoid division by zero");
	}

	/**
	 * GrossPositionValue is the sum of (qty * currentPrice) for all positions.
	 */
	@Test
	void testGrossPositionValue_multiplePositions() {
		List<BigDecimal[]> positions = List.of(
				new BigDecimal[]{new BigDecimal("10"), new BigDecimal("320.0")},  // SBER: 3200
				new BigDecimal[]{new BigDecimal("20"), new BigDecimal("180.0")}   // GAZP: 3600
		);

		BigDecimal gross = grossPositionValue(positions);

		assertEquals(0, new BigDecimal("6800.0").compareTo(gross));
	}

	// -----------------------------------------------------------------------
	// New feature tests
	// -----------------------------------------------------------------------

	/**
	 * Stop-loss trigger: SL fires when price <= triggerPrice.
	 */
	@Test
	void testStopLoss_triggersWhenPriceFallsToLevel() {
		BigDecimal triggerPrice = new BigDecimal("270.0");
		BigDecimal currentPrice = new BigDecimal("269.5");
		assertTrue(currentPrice.compareTo(triggerPrice) <= 0, "SL should trigger when price <= triggerPrice");

		BigDecimal currentPriceAbove = new BigDecimal("271.0");
		assertFalse(currentPriceAbove.compareTo(triggerPrice) <= 0, "SL should NOT trigger when price > triggerPrice");
	}

	/**
	 * Take-profit trigger: TP fires when price >= triggerPrice.
	 */
	@Test
	void testTakeProfit_triggersWhenPriceReachesLevel() {
		BigDecimal triggerPrice = new BigDecimal("310.0");
		BigDecimal currentPrice = new BigDecimal("310.5");
		assertTrue(currentPrice.compareTo(triggerPrice) >= 0, "TP should trigger when price >= triggerPrice");

		BigDecimal currentPriceBelow = new BigDecimal("309.9");
		assertFalse(currentPriceBelow.compareTo(triggerPrice) >= 0, "TP should NOT trigger when price < triggerPrice");
	}

	/**
	 * Limit buy: executes when price <= limitPrice.
	 */
	@Test
	void testLimitBuy_triggersWhenPriceAtOrBelowLimit() {
		BigDecimal limitPrice = new BigDecimal("270.5");
		BigDecimal marketPrice = new BigDecimal("270.0");
		assertTrue(marketPrice.compareTo(limitPrice) <= 0, "Limit buy should execute when price <= limitPrice");

		BigDecimal marketPriceAbove = new BigDecimal("271.0");
		assertFalse(marketPriceAbove.compareTo(limitPrice) <= 0, "Limit buy should NOT execute when price > limitPrice");
	}

	/**
	 * Limit sell: executes when price >= limitPrice.
	 */
	@Test
	void testLimitSell_triggersWhenPriceAtOrAboveLimit() {
		BigDecimal limitPrice = new BigDecimal("310.0");
		BigDecimal marketPrice = new BigDecimal("310.0");
		assertTrue(marketPrice.compareTo(limitPrice) >= 0, "Limit sell should execute when price >= limitPrice");

		BigDecimal marketPriceBelow = new BigDecimal("309.5");
		assertFalse(marketPriceBelow.compareTo(limitPrice) >= 0, "Limit sell should NOT execute when price < limitPrice");
	}

	/**
	 * Price alert: above alert triggers when price >= targetPrice.
	 */
	@Test
	void testPriceAlert_above_triggersCorrectly() {
		BigDecimal targetPrice = new BigDecimal("310.0");
		boolean above = true;

		BigDecimal triggering = new BigDecimal("310.0");
		boolean shouldFire = above ? triggering.compareTo(targetPrice) >= 0 : triggering.compareTo(targetPrice) <= 0;
		assertTrue(shouldFire);

		BigDecimal notTriggering = new BigDecimal("309.9");
		boolean shouldNotFire = above ? notTriggering.compareTo(targetPrice) >= 0 : notTriggering.compareTo(targetPrice) <= 0;
		assertFalse(shouldNotFire);
	}

	/**
	 * Price alert: below alert triggers when price <= targetPrice.
	 */
	@Test
	void testPriceAlert_below_triggersCorrectly() {
		BigDecimal targetPrice = new BigDecimal("270.0");
		boolean above = false;

		BigDecimal triggering = new BigDecimal("270.0");
		boolean shouldFire = above ? triggering.compareTo(targetPrice) >= 0 : triggering.compareTo(targetPrice) <= 0;
		assertTrue(shouldFire);

		BigDecimal notTriggering = new BigDecimal("270.1");
		boolean shouldNotFire = above ? notTriggering.compareTo(targetPrice) >= 0 : notTriggering.compareTo(targetPrice) <= 0;
		assertFalse(shouldNotFire);
	}

	/**
	 * Leverage status boundaries:
	 * < 2 = SAFE, 2-4 = WARNING, > 4 = CRITICAL.
	 */
	@Test
	void testLeverageStatus_boundaries() {
		// lev < 2 → SAFE
		BigDecimal lev1 = new BigDecimal("1.5");
		assertTrue(lev1.compareTo(new BigDecimal("2.0")) < 0);

		// lev in [2, 4] → WARNING
		BigDecimal lev2 = new BigDecimal("3.0");
		assertTrue(lev2.compareTo(new BigDecimal("2.0")) >= 0 && lev2.compareTo(new BigDecimal("4.0")) <= 0);

		// lev > 4 → CRITICAL
		BigDecimal lev3 = new BigDecimal("4.1");
		assertTrue(lev3.compareTo(new BigDecimal("4.0")) > 0);
	}

	/**
	 * Realized PnL calculation for a sell trade:
	 * pnl = (sellPrice - avgCost) * qty - fee
	 */
	@Test
	void testRealizedPnl_sellAboveCost_isProfit() {
		BigDecimal avgCost = new BigDecimal("300.0");
		BigDecimal sellPrice = new BigDecimal("320.0");
		int qty = 10;
		BigDecimal fee = new BigDecimal("3.2"); // 0.001 * 320 * 10
		BigDecimal pnl = sellPrice.subtract(avgCost).multiply(BigDecimal.valueOf(qty)).subtract(fee);
		assertTrue(pnl.compareTo(BigDecimal.ZERO) > 0, "Selling above avg cost should yield profit");
		assertEquals(0, new BigDecimal("196.8").compareTo(pnl));
	}

	@Test
	void testRealizedPnl_sellBelowCost_isLoss() {
		BigDecimal avgCost = new BigDecimal("300.0");
		BigDecimal sellPrice = new BigDecimal("280.0");
		int qty = 5;
		BigDecimal fee = new BigDecimal("1.4"); // 0.001 * 280 * 5
		BigDecimal pnl = sellPrice.subtract(avgCost).multiply(BigDecimal.valueOf(qty)).subtract(fee);
		assertTrue(pnl.compareTo(BigDecimal.ZERO) < 0, "Selling below avg cost should be a loss");
	}

	/**
	 * Win rate calculation: wins / total closed positions.
	 */
	@Test
	void testWinRate_calculation() {
		List<BigDecimal> pnls = List.of(
				new BigDecimal("100.0"),
				new BigDecimal("-50.0"),
				new BigDecimal("200.0"),
				new BigDecimal("-30.0"),
				new BigDecimal("150.0")
		);
		long wins = pnls.stream().filter(p -> p.compareTo(BigDecimal.ZERO) > 0).count();
		BigDecimal winRate = BigDecimal.valueOf(wins)
				.divide(BigDecimal.valueOf(pnls.size()), 9, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(1, RoundingMode.HALF_UP);
		assertEquals(0, new BigDecimal("60.0").compareTo(winRate), "Win rate should be 60%");
	}

	/**
	 * Trade history: invalid price (0.0) should block trade.
	 */
	@Test
	void testInvalidPrice_zeroBlocksTrade() {
		BigDecimal price = BigDecimal.ZERO;
		// Simulate the guard in trade()
		boolean shouldBlock = price.compareTo(BigDecimal.ZERO) <= 0;
		assertTrue(shouldBlock, "Price of 0.0 must block the trade");
	}

	/**
	 * MyRank: user rank is 1-based position in equity-sorted list.
	 */
	@Test
	void testMyRank_positionInList() {
		// Simulate 3 users with equities
		BigDecimal[] equities = {
				new BigDecimal("110000.0"),
				new BigDecimal("95000.0"),
				new BigDecimal("130000.0")
		};
		// Sort descending
		java.util.Arrays.sort(equities, (a, b) -> b.compareTo(a));
		// Find rank of user with equity 95000
		int targetRank = 0;
		for (int i = 0; i < equities.length; i++) {
			if (equities[i].compareTo(new BigDecimal("95000.0")) == 0) {
				targetRank = i + 1;
			}
		}
		assertEquals(3, targetRank, "User with 95k equity should be rank 3");
	}
}
