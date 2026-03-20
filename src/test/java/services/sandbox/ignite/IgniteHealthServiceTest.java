package services.sandbox.ignite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import org.apache.ignite.IgniteCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;

/**
 * Unit tests for {@link IgniteHealthService}.
 *
 * All Ignite infrastructure is mocked — no embedded Ignite node is started.
 * Tests call {@code runCheck()} directly (package-private) to avoid scheduler timing.
 */
@SuppressWarnings("unchecked")
class IgniteHealthServiceTest {

    private SandboxIgniteManager manager;
    private IgniteCache<String, SandboxUser>   usersCache;
    private IgniteCache<String, Position>       positionsCache;
    private IgniteCache<String, TradeRecord>    tradesCache;
    private IgniteCache<String, LimitOrder>     limitOrdersCache;
    private IgniteCache<String, StopOrder>      stopOrdersCache;
    private IgniteCache<String, PriceAlert>     priceAlertsCache;

    @BeforeEach
    void setUp() {
        manager          = mock(SandboxIgniteManager.class);
        usersCache       = mock(IgniteCache.class);
        positionsCache   = mock(IgniteCache.class);
        tradesCache      = mock(IgniteCache.class);
        limitOrdersCache = mock(IgniteCache.class);
        stopOrdersCache  = mock(IgniteCache.class);
        priceAlertsCache = mock(IgniteCache.class);

        when(manager.usersCache()).thenReturn(usersCache);
        when(manager.positionsCache()).thenReturn(positionsCache);
        when(manager.tradesCache()).thenReturn(tradesCache);
        when(manager.limitOrdersCache()).thenReturn(limitOrdersCache);
        when(manager.stopOrdersCache()).thenReturn(stopOrdersCache);
        when(manager.priceAlertsCache()).thenReturn(priceAlertsCache);
    }

    private void stubAllCachesHealthy(int size) {
        when(usersCache.size()).thenReturn(size);
        when(positionsCache.size()).thenReturn(size);
        when(tradesCache.size()).thenReturn(size);
        when(limitOrdersCache.size()).thenReturn(size);
        when(stopOrdersCache.size()).thenReturn(size);
        when(priceAlertsCache.size()).thenReturn(size);
    }

    // ------------------------------------------------------------------
    // Test 1: all caches healthy — no failure counted
    // ------------------------------------------------------------------

    @Test
    void healthyIgnite_noFailuresCounted() {
        stubAllCachesHealthy(0);

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(0, service.getHealthCheckFailures(),
                "No failures should be counted when all caches respond normally");
    }

    @Test
    void healthyIgniteWithData_noFailuresCounted() {
        stubAllCachesHealthy(42);

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(0, service.getHealthCheckFailures());
    }

    // ------------------------------------------------------------------
    // Test 2: one cache throws — failure is counted
    // ------------------------------------------------------------------

    @Test
    void usersCache_throwsException_failureIsCounted() {
        stubAllCachesHealthy(0);
        when(usersCache.size()).thenThrow(new RuntimeException("Connection refused"));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(1, service.getHealthCheckFailures(),
                "Failure counter should increment when a cache throws");
    }

    @Test
    void limitOrdersCache_throwsException_failureIsCounted() {
        stubAllCachesHealthy(0);
        when(limitOrdersCache.size()).thenThrow(new RuntimeException("Cache unavailable"));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(1, service.getHealthCheckFailures());
    }

    // ------------------------------------------------------------------
    // Test 3: multiple consecutive failures accumulate
    // ------------------------------------------------------------------

    @Test
    void multipleFailures_counterAccumulates() {
        stubAllCachesHealthy(0);
        when(usersCache.size()).thenThrow(new RuntimeException("Network timeout"));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();
        service.runCheck();
        service.runCheck();

        assertEquals(3, service.getHealthCheckFailures(),
                "Each failed check should add 1 to the failure counter");
    }

    // ------------------------------------------------------------------
    // Test 4: recovery after failure — counter does not reset but stops growing
    // ------------------------------------------------------------------

    @Test
    void recoveryAfterFailure_counterStopsGrowing() {
        // First call fails — use doThrow to avoid Mockito calling size() during stubbing
        doThrow(new RuntimeException("Transient error")).when(usersCache).size();
        when(positionsCache.size()).thenReturn(0);
        when(tradesCache.size()).thenReturn(0);
        when(limitOrdersCache.size()).thenReturn(0);
        when(stopOrdersCache.size()).thenReturn(0);
        when(priceAlertsCache.size()).thenReturn(0);

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();
        assertEquals(1, service.getHealthCheckFailures());

        // Second call succeeds (cache recovered) — use doReturn to safely override stubbing
        doReturn(0).when(usersCache).size();
        service.runCheck();
        assertEquals(1, service.getHealthCheckFailures(),
                "Counter must not grow when subsequent check passes");
    }

    // ------------------------------------------------------------------
    // Test 5: null cache reference causes a failure
    // ------------------------------------------------------------------

    @Test
    void nullCache_causesFailure() {
        when(manager.usersCache()).thenReturn(null);

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(1, service.getHealthCheckFailures(),
                "A null cache should cause the health-check to fail");
    }

    // ------------------------------------------------------------------
    // Test 6: check period constant is 5 minutes
    // ------------------------------------------------------------------

    @Test
    void checkPeriod_isFiveMinutes() {
        assertEquals(5 * 60 * 1000L, IgniteHealthService.CHECK_PERIOD_MS,
                "Health-check period must be 5 minutes (300 000 ms)");
    }

    // ------------------------------------------------------------------
    // Test 7: initial failure count is zero
    // ------------------------------------------------------------------

    @Test
    void initialFailureCount_isZero() {
        stubAllCachesHealthy(0);
        IgniteHealthService service = new IgniteHealthService(manager);
        assertEquals(0, service.getHealthCheckFailures(),
                "Before any check the failure counter must be 0");
    }
}
