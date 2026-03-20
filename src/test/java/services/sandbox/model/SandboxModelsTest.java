package services.sandbox.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the domain model classes in the sandbox package.
 *
 * Goals:
 * - Verify that all six models initialise their schemaVersion to 0 by default
 *   (the migration system depends on this invariant).
 * - Verify that CURRENT_SCHEMA_VERSION constants are at least 1
 *   (if still 0 the migration would never run).
 * - Verify constructor-level field assignment for models that carry business data.
 * - Edge cases: boundary values, serialization-friendly no-arg constructors.
 */
class SandboxModelsTest {

    // =======================================================================
    // Schema version invariants — shared across all six models
    // =======================================================================

    @Test
    void sandboxUser_defaultSchemaVersionIsZero() {
        SandboxUser user = new SandboxUser();
        assertEquals(0, user.getSchemaVersion(),
                "SandboxUser default schemaVersion must be 0 so migration can upgrade it");
    }

    @Test
    void position_defaultSchemaVersionIsZero() {
        Position pos = new Position();
        assertEquals(0, pos.getSchemaVersion());
    }

    @Test
    void tradeRecord_defaultSchemaVersionIsZero() {
        TradeRecord tr = new TradeRecord();
        assertEquals(0, tr.getSchemaVersion());
    }

    @Test
    void limitOrder_defaultSchemaVersionIsZero() {
        LimitOrder lo = new LimitOrder();
        assertEquals(0, lo.getSchemaVersion());
    }

    @Test
    void stopOrder_defaultSchemaVersionIsZero() {
        StopOrder so = new StopOrder();
        assertEquals(0, so.getSchemaVersion());
    }

    @Test
    void priceAlert_defaultSchemaVersionIsZero() {
        PriceAlert pa = new PriceAlert();
        assertEquals(0, pa.getSchemaVersion());
    }

    @Test
    void allModels_currentSchemaVersionIsAtLeastOne() {
        assertTrue(SandboxUser.CURRENT_SCHEMA_VERSION >= 1);
        assertTrue(Position.CURRENT_SCHEMA_VERSION >= 1);
        assertTrue(TradeRecord.CURRENT_SCHEMA_VERSION >= 1);
        assertTrue(LimitOrder.CURRENT_SCHEMA_VERSION >= 1);
        assertTrue(StopOrder.CURRENT_SCHEMA_VERSION >= 1);
        assertTrue(PriceAlert.CURRENT_SCHEMA_VERSION >= 1);
    }

    // =======================================================================
    // SandboxUser business-logic fields
    // =======================================================================

    @Test
    void sandboxUser_constructorSetsFieldsCorrectly() {
        SandboxUser user = new SandboxUser("u42", "Alice", 100_000.0);
        assertEquals("u42", user.getUserId());
        assertEquals("Alice", user.getUserName());
        assertEquals(100_000.0, user.getCash(), 1e-9);
        assertEquals(0.0, user.getBorrowed(), 1e-9,
                "Borrowed must start at 0 when using the 3-arg constructor");
        assertEquals(0.0, user.getTotalFees(), 1e-9,
                "Total fees must start at 0 when using the 3-arg constructor");
    }

    @Test
    void sandboxUser_cashCanBeNegativeAfterSetter() {
        // The service uses setCash() to reflect borrowed-state; the model must accept negative
        SandboxUser user = new SandboxUser("u1", "Bob", 500.0);
        user.setCash(-100.0);
        assertEquals(-100.0, user.getCash(), 1e-9);
    }

    @Test
    void sandboxUser_baselineDatesNullByDefault() {
        SandboxUser user = new SandboxUser("u1", "Carol", 1_000.0);
        assertNull(user.getDailyBaselineDate(),   "No daily baseline until first trade");
        assertNull(user.getWeeklyBaselineDate(),  "No weekly baseline until first trade");
        assertNull(user.getMonthlyBaselineDate(), "No monthly baseline until first trade");
    }

    @Test
    void sandboxUser_baselineSettersWorkCorrectly() {
        SandboxUser user = new SandboxUser("u1", "Dave", 50_000.0);
        LocalDate today = LocalDate.of(2026, 3, 20);
        user.setDailyBaselineDate(today);
        user.setDailyBaselineEquity(50_000.0);
        user.setWeeklyBaselineDate(today);
        user.setWeeklyBaselineEquity(48_000.0);
        user.setMonthlyBaselineDate(today);
        user.setMonthlyBaselineEquity(45_000.0);

        assertEquals(today, user.getDailyBaselineDate());
        assertEquals(50_000.0, user.getDailyBaselineEquity(), 1e-9);
        assertEquals(48_000.0, user.getWeeklyBaselineEquity(), 1e-9);
        assertEquals(45_000.0, user.getMonthlyBaselineEquity(), 1e-9);
    }

    // =======================================================================
    // Position
    // =======================================================================

    @Test
    void position_constructorSetsFieldsCorrectly() {
        Position pos = new Position("u1", "SBER", "uid-sber", 10, 305.50);
        assertEquals("u1", pos.getUserId());
        assertEquals("SBER", pos.getTicker());
        assertEquals("uid-sber", pos.getInstrumentId());
        assertEquals(10, pos.getQuantity());
        assertEquals(305.50, pos.getAvgPrice(), 1e-9);
    }

    @Test
    void position_quantityCanBeZeroAfterSell() {
        Position pos = new Position("u1", "GAZP", "uid-gazp", 5, 200.0);
        pos.setQuantity(0);
        assertEquals(0, pos.getQuantity());
    }

    // =======================================================================
    // TradeRecord
    // =======================================================================

    @Test
    void tradeRecord_constructorSetsAllFields() {
        Instant now = Instant.now();
        TradeRecord tr = new TradeRecord("id1", "u1", "LKOH", "BUY", 3, 7500.0, 7.5, now);
        assertEquals("id1", tr.getId());
        assertEquals("u1", tr.getUserId());
        assertEquals("LKOH", tr.getTicker());
        assertEquals("BUY", tr.getSide());
        assertEquals(3, tr.getQty());
        assertEquals(7500.0, tr.getPrice(), 1e-9);
        assertEquals(7.5, tr.getFee(), 1e-9);
        assertEquals(now, tr.getTimestamp());
    }

    @Test
    void tradeRecord_feeMinimumEnforcement_conceptual() {
        // The service enforces fee >= 1.0; a fee < 1 in the model itself should still be storable
        TradeRecord tr = new TradeRecord("id2", "u2", "SBER", "SELL", 1, 300.0, 0.3, Instant.now());
        assertEquals(0.3, tr.getFee(), 1e-9,
                "Model stores whatever fee the service puts in — minimum enforcement is the service's job");
    }

    // =======================================================================
    // LimitOrder
    // =======================================================================

    @Test
    void limitOrder_constructorSetsAllFields() {
        Instant now = Instant.now();
        LimitOrder lo = new LimitOrder("lo1", "u1", "Alice", "SBER", "BUY", 5, 295.0, now);
        assertEquals("lo1", lo.getId());
        assertEquals("u1", lo.getUserId());
        assertEquals("Alice", lo.getUserName());
        assertEquals("SBER", lo.getTicker());
        assertEquals("BUY", lo.getSide());
        assertEquals(5, lo.getQty());
        assertEquals(295.0, lo.getLimitPrice(), 1e-9);
        assertEquals(now, lo.getCreatedAt());
    }

    @Test
    void limitOrder_sellSideStoredCorrectly() {
        LimitOrder lo = new LimitOrder("lo2", "u2", "Bob", "GAZP", "SELL", 10, 185.0, Instant.now());
        assertEquals("SELL", lo.getSide());
        assertEquals(185.0, lo.getLimitPrice(), 1e-9);
    }

    // =======================================================================
    // StopOrder
    // =======================================================================

    @Test
    void stopOrder_constructorSetsAllFields_SL() {
        Instant now = Instant.now();
        StopOrder so = new StopOrder("so1", "u1", "SBER", "SL", 270.0, now);
        assertEquals("so1", so.getId());
        assertEquals("u1", so.getUserId());
        assertEquals("SBER", so.getTicker());
        assertEquals("SL", so.getType());
        assertEquals(270.0, so.getTriggerPrice(), 1e-9);
        assertEquals(now, so.getCreatedAt());
    }

    @Test
    void stopOrder_constructorSetsAllFields_TP() {
        StopOrder so = new StopOrder("so2", "u1", "NVDA", "TP", 850.0, Instant.now());
        assertEquals("TP", so.getType());
        assertEquals(850.0, so.getTriggerPrice(), 1e-9);
    }

    // =======================================================================
    // PriceAlert
    // =======================================================================

    @Test
    void priceAlert_constructorSetsAllFields_above() {
        Instant now = Instant.now();
        PriceAlert pa = new PriceAlert("pa1", "u1", "AAPL", 200.0, true, now);
        assertEquals("pa1", pa.getId());
        assertEquals("u1", pa.getUserId());
        assertEquals("AAPL", pa.getTicker());
        assertEquals(200.0, pa.getTargetPrice(), 1e-9);
        assertTrue(pa.isAbove(), "above=true means notify when price >= target");
        assertEquals(now, pa.getCreatedAt());
    }

    @Test
    void priceAlert_belowVariant() {
        PriceAlert pa = new PriceAlert("pa2", "u2", "TSLA", 150.0, false, Instant.now());
        assertFalse(pa.isAbove(), "above=false means notify when price <= target");
    }

    // =======================================================================
    // Schema version setters — used by migration
    // =======================================================================

    @Test
    void schemaVersionSetters_upgradeCorrectly() {
        SandboxUser user = new SandboxUser("u1", "E", 1000.0);
        assertEquals(0, user.getSchemaVersion());
        user.setSchemaVersion(SandboxUser.CURRENT_SCHEMA_VERSION);
        assertEquals(SandboxUser.CURRENT_SCHEMA_VERSION, user.getSchemaVersion());

        Position pos = new Position("u1", "X", "uid", 1, 100.0);
        pos.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);
        assertEquals(Position.CURRENT_SCHEMA_VERSION, pos.getSchemaVersion());

        TradeRecord tr = new TradeRecord("t1", "u1", "X", "BUY", 1, 100.0, 1.0, Instant.now());
        tr.setSchemaVersion(TradeRecord.CURRENT_SCHEMA_VERSION);
        assertEquals(TradeRecord.CURRENT_SCHEMA_VERSION, tr.getSchemaVersion());

        LimitOrder lo = new LimitOrder("lo1", "u1", "A", "X", "BUY", 1, 100.0, Instant.now());
        lo.setSchemaVersion(LimitOrder.CURRENT_SCHEMA_VERSION);
        assertEquals(LimitOrder.CURRENT_SCHEMA_VERSION, lo.getSchemaVersion());

        StopOrder so = new StopOrder("so1", "u1", "X", "SL", 90.0, Instant.now());
        so.setSchemaVersion(StopOrder.CURRENT_SCHEMA_VERSION);
        assertEquals(StopOrder.CURRENT_SCHEMA_VERSION, so.getSchemaVersion());

        PriceAlert pa = new PriceAlert("pa1", "u1", "X", 100.0, true, Instant.now());
        pa.setSchemaVersion(PriceAlert.CURRENT_SCHEMA_VERSION);
        assertEquals(PriceAlert.CURRENT_SCHEMA_VERSION, pa.getSchemaVersion());
    }
}
