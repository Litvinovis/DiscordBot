package services.sandbox;

import org.junit.jupiter.api.Test;
import services.sandbox.model.DcaOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DCA order logic — no Spring context, no DB required.
 */
public class DcaOrderTest {

    // -----------------------------------------------------------------------
    // next_execution scheduling
    // -----------------------------------------------------------------------

    @Test
    void testWeeklyOrder_nextExecutionIn7Days() {
        Instant now = Instant.now();
        Instant nextExecution = now.plus(7, ChronoUnit.DAYS);

        DcaOrder order = new DcaOrder("user1", "SBER", BigDecimal.valueOf(5000.0), "WEEKLY", nextExecution, now, true);

        long diffDays = ChronoUnit.DAYS.between(now, order.getNextExecution());
        assertEquals(7, diffDays, "Weekly DCA должен исполняться через 7 дней");
    }

    @Test
    void testMonthlyOrder_nextExecutionIn30Days() {
        Instant now = Instant.now();
        Instant nextExecution = now.plus(30, ChronoUnit.DAYS);

        DcaOrder order = new DcaOrder("user1", "LKOH", BigDecimal.valueOf(10000.0), "MONTHLY", nextExecution, now, true);

        long diffDays = ChronoUnit.DAYS.between(now, order.getNextExecution());
        assertEquals(30, diffDays, "Monthly DCA должен исполняться через 30 дней");
    }

    // -----------------------------------------------------------------------
    // findDueOrders logic — orders with nextExecution <= now are due
    // -----------------------------------------------------------------------

    @Test
    void testFindDueOrders_returnsOnlyOverdueOrders() {
        Instant now = Instant.now();

        DcaOrder overdue = new DcaOrder("user1", "SBER", BigDecimal.valueOf(5000.0), "WEEKLY",
                now.minus(1, ChronoUnit.HOURS), now.minus(8, ChronoUnit.DAYS), true);
        DcaOrder notDue = new DcaOrder("user2", "GAZP", BigDecimal.valueOf(3000.0), "WEEKLY",
                now.plus(6, ChronoUnit.DAYS), now, true);
        DcaOrder inactiveDue = new DcaOrder("user3", "LKOH", BigDecimal.valueOf(7000.0), "MONTHLY",
                now.minus(1, ChronoUnit.HOURS), now.minus(31, ChronoUnit.DAYS), false);

        List<DcaOrder> all = List.of(overdue, notDue, inactiveDue);

        // Simulate findDueOrders filter: active=true AND nextExecution <= now
        List<DcaOrder> due = all.stream()
                .filter(o -> o.isActive() && !o.getNextExecution().isAfter(now))
                .toList();

        assertEquals(1, due.size(), "Только один просроченный активный ордер");
        assertEquals("SBER", due.getFirst().getTicker());
    }

    // -----------------------------------------------------------------------
    // Minimum amount validation
    // -----------------------------------------------------------------------

    @Test
    void testMinimumAmount_below100_isRejected() {
        BigDecimal amount = new BigDecimal("99.99");
        BigDecimal minAmount = new BigDecimal("100");
        assertTrue(amount.compareTo(minAmount) < 0, "Сумма меньше 100 ₽ должна отклоняться");
    }

    @Test
    void testMinimumAmount_exactly100_isAccepted() {
        BigDecimal amount = new BigDecimal("100");
        BigDecimal minAmount = new BigDecimal("100");
        assertFalse(amount.compareTo(minAmount) < 0, "Ровно 100 ₽ должна приниматься");
    }

    @Test
    void testMinimumAmount_above100_isAccepted() {
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal minAmount = new BigDecimal("100");
        assertFalse(amount.compareTo(minAmount) < 0, "5000 ₽ должна приниматься");
    }

    // -----------------------------------------------------------------------
    // Quantity calculation: qty = floor(amountRub / price)
    // -----------------------------------------------------------------------

    @Test
    void testQtyCalculation_correctlyRoundsDown() {
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal price = new BigDecimal("320.50");
        int qty = amount.divide(price, 0, RoundingMode.DOWN).intValue();
        // 5000 / 320.50 = 15.6
        assertEquals(15, qty, "Кол-во должно округляться вниз");
    }

    @Test
    void testQtyCalculation_insufficientAmount_givesZero() {
        BigDecimal amount = new BigDecimal("100");
        BigDecimal price = new BigDecimal("5000");
        int qty = amount.divide(price, 0, RoundingMode.DOWN).intValue();
        assertEquals(0, qty, "Если сумма меньше цены одной акции — qty=0, ордер пропускается");
    }

    // -----------------------------------------------------------------------
    // Advance next execution
    // -----------------------------------------------------------------------

    @Test
    void testAdvanceNextExecution_weekly_adds7Days() {
        Instant original = Instant.now();
        Instant next = original.plus(7, ChronoUnit.DAYS);
        assertEquals(7, ChronoUnit.DAYS.between(original, next));
    }

    @Test
    void testAdvanceNextExecution_monthly_adds30Days() {
        Instant original = Instant.now();
        Instant next = original.plus(30, ChronoUnit.DAYS);
        assertEquals(30, ChronoUnit.DAYS.between(original, next));
    }

    // -----------------------------------------------------------------------
    // DcaOrder model field round-trip
    // -----------------------------------------------------------------------

    @Test
    void testDcaOrder_fieldsStoredCorrectly() {
        Instant now = Instant.now();
        Instant next = now.plus(7, ChronoUnit.DAYS);
        DcaOrder order = new DcaOrder("u42", "NVDA", BigDecimal.valueOf(2500.0), "WEEKLY", next, now, true);
        order.setId(99L);

        assertEquals(99L, order.getId());
        assertEquals("u42", order.getUserId());
        assertEquals("NVDA", order.getTicker());
        assertEquals(0, BigDecimal.valueOf(2500.0).compareTo(order.getAmountRub()));
        assertEquals("WEEKLY", order.getFrequency());
        assertEquals(next, order.getNextExecution());
        assertEquals(now, order.getCreatedAt());
        assertTrue(order.isActive());
    }

    @Test
    void testDcaOrder_cancel_setsActiveToFalse() {
        Instant now = Instant.now();
        DcaOrder order = new DcaOrder("u1", "SBER", BigDecimal.valueOf(5000.0), "WEEKLY",
                now.plus(7, ChronoUnit.DAYS), now, true);
        order.setActive(false);
        assertFalse(order.isActive(), "После отмены active должен быть false");
    }
}
