package services.sandbox.ignite;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ignite.IgniteCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;

/**
 * Periodic health-check service for Apache Ignite.
 *
 * Every 5 minutes it verifies that all sandbox caches are reachable.
 * On failure it logs a WARNING with details and increments a simple failure counter.
 * No external metrics framework (Prometheus etc.) is used — just an AtomicLong field.
 */
public class IgniteHealthService {

    private static final Logger log = LoggerFactory.getLogger(IgniteHealthService.class);

    /** How often to run the health-check (milliseconds, overridable in tests). */
    static final long CHECK_PERIOD_MS = TimeUnit.MINUTES.toMillis(5);

    private final IgniteCache<String, SandboxUser> usersCache;
    private final IgniteCache<String, Position> positionsCache;
    private final IgniteCache<String, TradeRecord> tradesCache;
    private final IgniteCache<String, LimitOrder> limitOrdersCache;
    private final IgniteCache<String, StopOrder> stopOrdersCache;
    private final IgniteCache<String, PriceAlert> priceAlertsCache;

    /** Simple failure counter — incremented every time a health-check fails. */
    private final AtomicLong healthCheckFailures = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-check");
        t.setDaemon(true);
        return t;
    });

    public IgniteHealthService(SandboxIgniteManager manager) {
        this.usersCache = manager.usersCache();
        this.positionsCache = manager.positionsCache();
        this.tradesCache = manager.tradesCache();
        this.limitOrdersCache = manager.limitOrdersCache();
        this.stopOrdersCache = manager.stopOrdersCache();
        this.priceAlertsCache = manager.priceAlertsCache();
    }

    /**
     * Start the periodic health-check.
     * The first check runs after one period to let the node fully start.
     */
    public void start() {
        long periodSeconds = CHECK_PERIOD_MS / 1000;
        scheduler.scheduleAtFixedRate(
                this::runCheck,
                periodSeconds,
                periodSeconds,
                TimeUnit.SECONDS
        );
        log.info("IgniteHealthService started (period={}s)", periodSeconds);
    }

    /**
     * Stop the periodic health-check gracefully.
     */
    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Returns the total number of health-check failures since startup.
     */
    public long getHealthCheckFailures() {
        return healthCheckFailures.get();
    }

    /**
     * Perform a single health-check: attempt a lightweight operation on every cache.
     * Exposed as package-private for testing without starting the scheduler.
     */
    void runCheck() {
        try {
            checkCache("users", usersCache);
            checkCache("positions", positionsCache);
            checkCache("trades", tradesCache);
            checkCache("limitOrders", limitOrdersCache);
            checkCache("stopOrders", stopOrdersCache);
            checkCache("priceAlerts", priceAlertsCache);
            log.debug("IgniteHealthService: all caches OK");
        } catch (Exception e) {
            long failures = healthCheckFailures.incrementAndGet();
            log.warn("IgniteHealthService: health-check FAILED (total failures: {}). Details: {}",
                    failures, e.getMessage());
        }
    }

    private void checkCache(String cacheName, IgniteCache<?, ?> cache) {
        if (cache == null) {
            throw new IllegalStateException("Cache '" + cacheName + "' is null");
        }
        // A size() call is a lightweight way to verify the cache is reachable
        int size = cache.size();
        log.debug("IgniteHealthService: cache '{}' size={}", cacheName, size);
    }
}
