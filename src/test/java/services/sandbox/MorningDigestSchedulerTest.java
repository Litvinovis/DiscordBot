package services.sandbox;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import services.sandbox.model.Position;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.SandboxUserRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MorningDigestScheduler — no Spring context, no Discord/JDA required.
 * Tests the buildDigest() method which is package-private and accessible from the same package.
 */
public class MorningDigestSchedulerTest {

    /**
     * Creates a MorningDigestScheduler with all dependencies mocked.
     * JDA is null — buildDigest() does not use it, so this is safe.
     */
    private MorningDigestScheduler scheduler(SandboxTradingService tradingService) {
        return new MorningDigestScheduler(
                null,                   // JDA — not used by buildDigest()
                tradingService,
                mock(PositionRepository.class),
                mock(SandboxUserRepository.class)
        );
    }

    @Test
    void buildDigest_skipsUsersWithNoOpenPositions() {
        SandboxTradingService tradingService = mock(SandboxTradingService.class);
        MorningDigestScheduler sched = scheduler(tradingService);

        SandboxUser user = new SandboxUser("u1", "Alice", BigDecimal.valueOf(100_000.0));
        Position closedPos = new Position("u1", "SBER", "uid-sber", 0, BigDecimal.valueOf(300.0));

        String digest = sched.buildDigest(user, List.of(closedPos));

        assertNull(digest, "Digest should be null when all positions have quantity=0");
    }

    @Test
    void buildDigest_includesOpenPositionTicker() {
        SandboxTradingService tradingService = mock(SandboxTradingService.class);
        when(tradingService.price("GAZP")).thenReturn("GAZP = 180 ₽");
        MorningDigestScheduler sched = scheduler(tradingService);

        SandboxUser user = new SandboxUser("u1", "Alice", BigDecimal.valueOf(50_000.0));
        Position openPos = new Position("u1", "GAZP", "uid-gazp", 10, BigDecimal.valueOf(175.0));

        String digest = sched.buildDigest(user, List.of(openPos));

        assertNotNull(digest, "Digest should not be null when there is an open position");
        assertTrue(digest.contains("GAZP"), "Digest should contain the ticker name");
        assertTrue(digest.contains("10"), "Digest should contain the quantity");
        assertTrue(digest.contains("50000"), "Digest should contain the cash balance");
    }
}
