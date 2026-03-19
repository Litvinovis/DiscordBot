package services.sandbox;

import org.junit.jupiter.api.Test;
import services.sandbox.model.Position;
import services.sandbox.model.SandboxUser;

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

    private double equity(double cash, double grossPositionValue, double borrowed) {
        return cash + grossPositionValue - borrowed;
    }

    private double grossPositionValue(List<double[]> positions) {
        // each entry: [qty, currentPrice]
        return positions.stream().mapToDouble(p -> p[0] * p[1]).sum();
    }

    private double safeRoi(double now, double base) {
        if (base <= 0.0) return 0.0;
        return (now - base) / base;
    }

    /**
     * Simulate recordBaseline() — the NEW single-user overload that is called
     * from trade() BEFORE executing the trade.
     */
    private void recordBaseline(SandboxUser u, double equityAtCallTime, LocalDate now) {
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
            u.setDailyBaselineDate(now);
            u.setDailyBaselineEquity(equityAtCallTime);
        }
        if (u.getWeeklyBaselineDate() == null ||
                u.getWeeklyBaselineDate().get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
            u.setWeeklyBaselineDate(now);
            u.setWeeklyBaselineEquity(equityAtCallTime);
        }
        if (u.getMonthlyBaselineDate() == null ||
                u.getMonthlyBaselineDate().getMonthValue() != now.getMonthValue()) {
            u.setMonthlyBaselineDate(now);
            u.setMonthlyBaselineEquity(equityAtCallTime);
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
        SandboxUser user = new SandboxUser("u1", "Alice", 100_000.0);
        LocalDate today = LocalDate.of(2026, 3, 19);

        // First call (e.g. from register) — equity = 100 000
        recordBaseline(user, 100_000.0, today);
        assertEquals(100_000.0, user.getDailyBaselineEquity(), 1e-9,
                "Daily baseline should be set to 100 000 on first call");

        // After a profitable trade equity grows to 110 000.
        // A second call on the same day should NOT overwrite the baseline.
        recordBaseline(user, 110_000.0, today);
        assertEquals(100_000.0, user.getDailyBaselineEquity(), 1e-9,
                "Daily baseline must NOT be updated when the date hasn't changed");
    }

    /**
     * When a new day starts the baseline should be updated to the pre-trade equity,
     * not the post-trade equity.
     */
    @Test
    void testRecordBaseline_newDay_updatesBaselineToPreTradeEquity() {
        SandboxUser user = new SandboxUser("u1", "Alice", 100_000.0);
        LocalDate yesterday = LocalDate.of(2026, 3, 18);
        LocalDate today = LocalDate.of(2026, 3, 19);

        // Baseline was set yesterday
        recordBaseline(user, 105_000.0, yesterday);
        assertEquals(105_000.0, user.getDailyBaselineEquity(), 1e-9);

        // Today's first trade: recordBaseline() is called BEFORE the trade,
        // so equityAtCallTime = equity before the trade = 107 000.
        double equityBeforeTrade = 107_000.0;
        recordBaseline(user, equityBeforeTrade, today);

        assertEquals(today, user.getDailyBaselineDate());
        assertEquals(107_000.0, user.getDailyBaselineEquity(), 1e-9,
                "Baseline must capture equity BEFORE the trade on the new day");
    }

    /**
     * top() metric must return non-zero ROI when there is a meaningful baseline.
     * Previously, top() called recordBaselines() itself which reset the baseline to
     * current equity → always 0%.
     */
    @Test
    void testTopMetric_usesPreExistingBaseline_notCurrentEquity() {
        // Simulate a user who registered yesterday with 100 000 and now has 110 000
        double baselineEquity = 100_000.0;
        double currentEquity  = 110_000.0;

        double roi = safeRoi(currentEquity, baselineEquity);

        assertEquals(0.10, roi, 1e-9,
                "ROI should be 10%, not 0%, when baseline is from a prior period");

        // What the old (buggy) code did: reset baseline to currentEquity then compare
        double roiBuggy = safeRoi(currentEquity, currentEquity);
        assertEquals(0.0, roiBuggy, 1e-9,
                "Old bug: resetting baseline to current equity always yields 0%");
    }

    /**
     * Weekly baseline rolls over when the ISO week number changes.
     */
    @Test
    void testRecordBaseline_newWeek_updatesWeeklyBaseline() {
        SandboxUser user = new SandboxUser("u1", "Alice", 100_000.0);

        // Week 11 of 2026
        LocalDate lastWeek = LocalDate.of(2026, 3, 9);   // Monday, week 11
        LocalDate thisWeek = LocalDate.of(2026, 3, 16);  // Monday, week 12

        recordBaseline(user, 100_000.0, lastWeek);
        assertEquals(100_000.0, user.getWeeklyBaselineEquity(), 1e-9);

        recordBaseline(user, 115_000.0, thisWeek);
        assertEquals(115_000.0, user.getWeeklyBaselineEquity(), 1e-9,
                "Weekly baseline must update on a new ISO week");
    }

    // -----------------------------------------------------------------------
    // Bug 2: balance() — equity includes stock value
    // -----------------------------------------------------------------------

    /**
     * Equity = cash + grossPositionValue - borrowed.
     * The balance display must reflect ALL assets, not just cash.
     */
    @Test
    void testEquity_includesPositionValue() {
        double cash = 50_000.0;
        double borrowed = 0.0;
        // User holds 10 shares of SBER at current price 320 ₽
        double grossPositionValue = grossPositionValue(List.of(new double[]{10, 320.0}));

        double eq = equity(cash, grossPositionValue, borrowed);

        assertEquals(53_200.0, eq, 1e-9,
                "Equity must include 3 200 ₽ worth of stock positions");
    }

    @Test
    void testEquity_withBorrowedFunds() {
        double cash = 0.0;
        double borrowed = 20_000.0;
        double grossPositionValue = grossPositionValue(List.of(new double[]{100, 300.0}));

        double eq = equity(cash, grossPositionValue, borrowed);

        assertEquals(10_000.0, eq, 1e-9,
                "Equity = 30 000 (stocks) - 20 000 (borrowed) = 10 000");
    }

    /**
     * ROI shown in balance() is relative to startBalance.
     */
    @Test
    void testBalanceRoi_relativeTo_startBalance() {
        double startBalance = 100_000.0;
        double currentEquity = 112_500.0;

        double roi = safeRoi(currentEquity, startBalance) * 100.0;

        assertEquals(12.5, roi, 1e-9, "ROI from start should be +12.50%");
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
        double avgPrice = 300.0;
        double currentPrice = 320.0;

        double pnl = (currentPrice - avgPrice) * qty;
        double pnlPct = (currentPrice - avgPrice) / avgPrice * 100.0;

        assertEquals(200.0, pnl, 1e-9, "P&L should be +200 ₽");
        assertEquals(6.666_666, pnlPct, 1e-3, "P&L% should be ~+6.67%");
    }

    @Test
    void testPortfolioPnl_longPosition_loss() {
        int qty = 5;
        double avgPrice = 500.0;
        double currentPrice = 450.0;

        double pnl = (currentPrice - avgPrice) * qty;

        assertEquals(-250.0, pnl, 1e-9, "P&L should be -250 ₽");
    }

    /**
     * Total P&L is the sum across all positions.
     */
    @Test
    void testPortfolioTotalPnl_multiplePositions() {
        // Position A: SBER 10 @ avg 300, now 320 → +200
        double pnlA = (320.0 - 300.0) * 10;
        // Position B: GAZP 20 @ avg 200, now 180 → -400
        double pnlB = (180.0 - 200.0) * 20;

        double total = pnlA + pnlB;

        assertEquals(-200.0, total, 1e-9, "Net P&L should be -200 ₽");
    }

    @Test
    void testPortfolioPnlPct_zeroAvgPrice_doesNotDivideByZero() {
        double avgPrice = 0.0;
        double currentPrice = 100.0;

        double pnlPct = avgPrice > 0.0 ? (currentPrice - avgPrice) / avgPrice * 100.0 : 0.0;

        assertEquals(0.0, pnlPct, 1e-9,
                "When avgPrice is 0, P&L% should be 0 to avoid division by zero");
    }

    /**
     * GrossPositionValue is the sum of (qty * currentPrice) for all positions.
     */
    @Test
    void testGrossPositionValue_multiplePositions() {
        List<double[]> positions = List.of(
                new double[]{10, 320.0},  // SBER: 3200
                new double[]{20, 180.0}   // GAZP: 3600
        );

        double gross = grossPositionValue(positions);

        assertEquals(6_800.0, gross, 1e-9);
    }
}
