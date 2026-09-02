package services.sandbox;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.SandboxUserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for the replenish() business logic.
 *
 * Rather than instantiating the full SandboxTradingService (which requires TInvestApi,
 * JDA, and other heavyweight beans), this class replicates the replenish() rules
 * directly — the same approach used by SandboxTradingLogicTest.
 *
 * The SandboxUserRepository is mocked to verify save/no-save behaviour.
 */
public class ReplenishTest {

    private static final int COOLDOWN_DAYS = 30;
    private static final double MAX_AMOUNT = 200_000.0;

    /**
     * Mirrors the replenish() method in SandboxTradingService for isolated testing.
     */
    private String replenish(SandboxUserRepository repo, String userId, String userName, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > MAX_AMOUNT) {
            return "❌ Сумма пополнения должна быть от 1 до 200 000 ₽.";
        }
        SandboxUser user = repo.findById(userId);
        if (user == null) return "Сначала выполните +регистрация";
        LocalDate today = LocalDate.now();
        if (user.getLastReplenishDate() != null &&
            user.getLastReplenishDate().plusDays(COOLDOWN_DAYS).isAfter(today)) {
            long daysLeft = ChronoUnit.DAYS.between(today, user.getLastReplenishDate().plusDays(COOLDOWN_DAYS));
            return "⏳ Пополнение доступно раз в 30 дней. Следующее — через **" + daysLeft + " дн.**";
        }
        user.setCash(user.getCash().add(BigDecimal.valueOf(amount)));
        user.setLastReplenishDate(today);
        repo.save(userId, user);
        return String.format("💰 Счёт пополнен на **%.0f ₽**. Новый баланс: **%.0f ₽**. Следующее пополнение через 30 дней.",
                amount, user.getCash().doubleValue());
    }

    @Test
    void replenish_addsMoneyToBalance() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);
        SandboxUser user = new SandboxUser("u1", "Alice", BigDecimal.valueOf(100_000.0));
        when(repo.findById("u1")).thenReturn(user);

        String result = replenish(repo, "u1", "Alice", 50_000.0);

        assertTrue(result.contains("50000"), "Result should mention the replenished amount");
        assertEquals(0, BigDecimal.valueOf(150_000.0).compareTo(user.getCash()), "Cash should be 150 000 after replenish");
        assertEquals(LocalDate.now(), user.getLastReplenishDate());
        verify(repo).save(eq("u1"), eq(user));
    }

    @Test
    void replenish_blockedWithin30Days() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);
        SandboxUser user = new SandboxUser("u1", "Alice", BigDecimal.valueOf(100_000.0));
        user.setLastReplenishDate(LocalDate.now().minusDays(5));
        when(repo.findById("u1")).thenReturn(user);

        String result = replenish(repo, "u1", "Alice", 10_000.0);

        assertTrue(result.startsWith("⏳"), "Should return cooldown message");
        assertTrue(result.contains("дн."), "Should mention remaining days");
        verify(repo, never()).save(any(), any());
    }

    @Test
    void replenish_allowedAfter30Days() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);
        SandboxUser user = new SandboxUser("u1", "Alice", BigDecimal.valueOf(100_000.0));
        user.setLastReplenishDate(LocalDate.now().minusDays(31));
        when(repo.findById("u1")).thenReturn(user);

        String result = replenish(repo, "u1", "Alice", 30_000.0);

        assertFalse(result.startsWith("⏳"), "Should not be blocked after 31 days");
        assertEquals(0, BigDecimal.valueOf(130_000.0).compareTo(user.getCash()));
        verify(repo).save(eq("u1"), eq(user));
    }

    @Test
    void replenish_rejectsOver200k() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);

        String result = replenish(repo, "u1", "Alice", 200_001.0);

        assertTrue(result.startsWith("❌"), "Should reject amount over 200 000");
        verifyNoInteractions(repo);
    }

    @Test
    void replenish_rejectsZero() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);

        String result = replenish(repo, "u1", "Alice", 0.0);

        assertTrue(result.startsWith("❌"), "Should reject zero amount");
        verifyNoInteractions(repo);
    }

    @Test
    void replenish_rejectsNaN() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);

        String result = replenish(repo, "u1", "Alice", Double.NaN);

        assertTrue(result.startsWith("❌"), "NaN must be rejected: it passes all comparisons and corrupts the balance");
        verifyNoInteractions(repo);
    }

    @Test
    void replenish_rejectsInfinity() {
        SandboxUserRepository repo = mock(SandboxUserRepository.class);

        String result = replenish(repo, "u1", "Alice", Double.POSITIVE_INFINITY);

        assertTrue(result.startsWith("❌"), "Infinity must be rejected");
        verifyNoInteractions(repo);
    }
}
