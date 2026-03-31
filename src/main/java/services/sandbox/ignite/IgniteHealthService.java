package services.sandbox.ignite;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodic health-check service for Apache Ignite 3.
 *
 * Every 5 minutes it verifies that the Ignite 3 cluster is reachable by
 * executing a lightweight SQL query. On failure it logs a WARNING with details
 * and increments a simple failure counter.
 */
public class IgniteHealthService {

    private static final Logger log = LoggerFactory.getLogger(IgniteHealthService.class);

    /** How often to run the health-check (milliseconds, overridable in tests). */
    static final long CHECK_PERIOD_MS = TimeUnit.MINUTES.toMillis(5);

    private final IgniteClient igniteClient;

    /** Simple failure counter — incremented every time a health-check fails. */
    private final AtomicLong healthCheckFailures = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-check");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт health-check сервис на основе Ignite 3 manager.
     *
     * @param manager менеджер Ignite 3
     */
    public IgniteHealthService(SandboxIgniteManager manager) {
        this.igniteClient = manager.getIgniteClient();
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
     * Perform a single health-check: execute a lightweight SQL query against Ignite 3.
     * Exposed as package-private for testing without starting the scheduler.
     */
    void runCheck() {
        try {
            try (var rs = igniteClient.sql().execute(null, "SELECT 1")) {
                if (rs.hasNext()) {
                    rs.next(); // consume result
                }
            }
            log.debug("IgniteHealthService: Ignite 3 cluster is OK");
        } catch (Exception e) {
            long failures = healthCheckFailures.incrementAndGet();
            log.warn("IgniteHealthService: health-check FAILED (total failures: {}). Details: {}",
                    failures, e.getMessage());
        }
    }
}
