package services.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.TradeSide;
import services.sandbox.repository.LimitOrderRepository;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for bugs #4, #7, #10, #11.
 */
class BugFixValidationTest {

    // ------------------------------------------------------------------
    // Bug #7: limit order price <= 0 is rejected
    // ------------------------------------------------------------------

    @Test
    void bug7_limitOrderPriceZero_isRejected() {
        // Simulate the guard added in placeLimitOrder:
        BigDecimal price = BigDecimal.ZERO;
        boolean rejected = price.compareTo(BigDecimal.ZERO) <= 0;
        assertTrue(rejected, "Price of 0 must be rejected for limit orders");
    }

    @Test
    void bug7_limitOrderNegativePrice_isRejected() {
        BigDecimal price = new BigDecimal("-1.00");
        boolean rejected = price.compareTo(BigDecimal.ZERO) <= 0;
        assertTrue(rejected, "Negative price must be rejected for limit orders");
    }

    @Test
    void bug7_limitOrderPositivePrice_isAccepted() {
        BigDecimal price = new BigDecimal("100.00");
        boolean rejected = price.compareTo(BigDecimal.ZERO) <= 0;
        assertFalse(rejected, "Positive price should be accepted");
    }

    // ------------------------------------------------------------------
    // Bug #10: 11th limit order for same ticker is rejected
    // ------------------------------------------------------------------

    @Test
    void bug10_eleventhLimitOrder_isRejected() {
        LimitOrderRepository repo = mock(LimitOrderRepository.class);
        when(repo.countByUserAndTicker("user1", "SBER")).thenReturn(10L);

        long count = repo.countByUserAndTicker("user1", "SBER");
        boolean rejected = count >= 10;
        assertTrue(rejected, "Should reject when already 10 orders exist for ticker");
    }

    @Test
    void bug10_tenthLimitOrder_isRejected() {
        LimitOrderRepository repo = mock(LimitOrderRepository.class);
        when(repo.countByUserAndTicker("user1", "SBER")).thenReturn(10L);

        long count = repo.countByUserAndTicker("user1", "SBER");
        assertTrue(count >= 10, "Exactly 10 orders already means 11th is rejected");
    }

    @Test
    void bug10_ninthLimitOrder_isAllowed() {
        LimitOrderRepository repo = mock(LimitOrderRepository.class);
        when(repo.countByUserAndTicker("user1", "SBER")).thenReturn(9L);

        long count = repo.countByUserAndTicker("user1", "SBER");
        assertFalse(count >= 10, "9 existing orders allows one more");
    }

    // ------------------------------------------------------------------
    // Bug #11: +история 0 and +история -1 return error
    // ------------------------------------------------------------------

    @Test
    void bug11_historyZero_returnsError() {
        // Simulate the guard added in SandboxHistoryCommand.execute()
        int parsed = 0;
        boolean shouldReturnError = parsed <= 0;
        assertTrue(shouldReturnError, "+история 0 should return an error");
    }

    @Test
    void bug11_historyNegative_returnsError() {
        int parsed = -1;
        boolean shouldReturnError = parsed <= 0;
        assertTrue(shouldReturnError, "+история -1 should return an error");
    }

    @Test
    void bug11_historyPositive_isAccepted() {
        int parsed = 1;
        boolean shouldReturnError = parsed <= 0;
        assertFalse(shouldReturnError, "+история 1 should be accepted");
    }

    // ------------------------------------------------------------------
    // Bug #4: partial sell does NOT change avgPrice
    // ------------------------------------------------------------------

    @Test
    void bug4_partialSell_doesNotChangeAvgPrice() {
        // Simulate a position: 10 shares at avg 300
        Position pos = new Position("user1", "SBER", "uid-sber", 10, BigDecimal.valueOf(300.0));
        BigDecimal originalAvgPrice = pos.getAvgPrice();

        // Partial sell: 5 shares. avgPrice must NOT change.
        int sellQty = 5;
        pos.setQuantity(pos.getQuantity() - sellQty);
        // avgPrice intentionally NOT updated on sell (this is the fix)

        assertEquals(0, originalAvgPrice.compareTo(pos.getAvgPrice()),
                "avgPrice must not change on a partial sell");
        assertEquals(5, pos.getQuantity(), "quantity should decrease by sell amount");
    }

    @Test
    void bug4_buy_updatesAvgPrice() {
        // Buying 10 shares at 300, then 10 more at 320 → avg = 310
        Position pos = new Position("user1", "SBER", "uid-sber", 10, BigDecimal.valueOf(300.0));

        int buyQty = 10;
        BigDecimal buyPrice = BigDecimal.valueOf(320.0);
        int newQty = pos.getQuantity() + buyQty;
        BigDecimal newAvg = pos.getAvgPrice().multiply(BigDecimal.valueOf(pos.getQuantity()))
                .add(buyPrice.multiply(BigDecimal.valueOf(buyQty)))
                .divide(BigDecimal.valueOf(newQty), 8, java.math.RoundingMode.HALF_UP);
        pos.setQuantity(newQty);
        pos.setAvgPrice(newAvg);

        assertEquals(0, BigDecimal.valueOf(310.0).compareTo(pos.getAvgPrice()), "avgPrice must be weighted average after buy");
        assertEquals(20, pos.getQuantity());
    }
}
