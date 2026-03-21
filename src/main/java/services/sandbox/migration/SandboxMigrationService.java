package services.sandbox.migration;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.ignite.SandboxIgniteManager;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs schema migrations across all 6 Ignite sandbox caches on startup.
 *
 * <p>For each cache entry the service attempts to read the value. If reading
 * succeeds and the entry's {@code schemaVersion} is below the current version,
 * it is upgraded and re-saved. If reading fails (e.g. corrupted binary data),
 * the key is collected and removed after the scan.
 */
public class SandboxMigrationService {

    private static final Logger log = LoggerFactory.getLogger(SandboxMigrationService.class);

    private final SandboxIgniteManager igniteManager;

    public SandboxMigrationService(SandboxIgniteManager igniteManager) {
        this.igniteManager = igniteManager;
    }

    /**
     * Runs migrations for all 6 caches and logs a summary.
     *
     * @return map of cache-name -> {@link CacheMigrationResult}
     */
    public Map<String, CacheMigrationResult> runMigrations() {
        Map<String, CacheMigrationResult> summary = new LinkedHashMap<>();

        summary.put("stonks_sandbox_users",
                migrateUsers(igniteManager.usersCache()));
        summary.put("stonks_sandbox_positions",
                migratePositions(igniteManager.positionsCache()));
        summary.put("stonks_sandbox_trades",
                migrateTrades(igniteManager.tradesCache()));
        summary.put("stonks_sandbox_limit_orders",
                migrateLimitOrders(igniteManager.limitOrdersCache()));
        summary.put("stonks_sandbox_stop_orders",
                migrateStopOrders(igniteManager.stopOrdersCache()));
        summary.put("stonks_sandbox_price_alerts",
                migratePriceAlerts(igniteManager.priceAlertsCache()));

        int totalMigrated = summary.values().stream().mapToInt(r -> r.migrated).sum();
        int totalRemoved  = summary.values().stream().mapToInt(r -> r.removed).sum();
        log.info("[Migration] Complete. Migrated={} Removed={} Details={}",
                totalMigrated, totalRemoved, summary);

        return summary;
    }

    // ------------------------------------------------------------------
    // Per-cache migration helpers
    // ------------------------------------------------------------------

    private CacheMigrationResult migrateUsers(IgniteCache<String, SandboxUser> cache) {
        String cacheName = "stonks_sandbox_users";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, SandboxUser>())) {
            for (Cache.Entry<String, SandboxUser> entry : cursor) {
                String key = entry.getKey();
                try {
                    SandboxUser value = entry.getValue();
                    if (value.getSchemaVersion() < SandboxUser.CURRENT_SCHEMA_VERSION) {
                        // v1 -> v2: initialise currencyHoldings if null
                        if (value.getSchemaVersion() < 2) {
                            if (value.getCurrencyHoldings() == null) {
                                value.setCurrencyHoldings(new HashMap<>());
                            }
                        }
                        value.setSchemaVersion(SandboxUser.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migratePositions(IgniteCache<String, Position> cache) {
        String cacheName = "stonks_sandbox_positions";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, Position>())) {
            for (Cache.Entry<String, Position> entry : cursor) {
                String key = entry.getKey();
                try {
                    Position value = entry.getValue();
                    if (value.getSchemaVersion() < Position.CURRENT_SCHEMA_VERSION) {
                        value.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateTrades(IgniteCache<String, TradeRecord> cache) {
        String cacheName = "stonks_sandbox_trades";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, TradeRecord>())) {
            for (Cache.Entry<String, TradeRecord> entry : cursor) {
                String key = entry.getKey();
                try {
                    TradeRecord value = entry.getValue();
                    if (value.getSchemaVersion() < TradeRecord.CURRENT_SCHEMA_VERSION) {
                        value.setSchemaVersion(TradeRecord.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateLimitOrders(IgniteCache<String, LimitOrder> cache) {
        String cacheName = "stonks_sandbox_limit_orders";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, LimitOrder>())) {
            for (Cache.Entry<String, LimitOrder> entry : cursor) {
                String key = entry.getKey();
                try {
                    LimitOrder value = entry.getValue();
                    if (value.getSchemaVersion() < LimitOrder.CURRENT_SCHEMA_VERSION) {
                        value.setSchemaVersion(LimitOrder.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateStopOrders(IgniteCache<String, StopOrder> cache) {
        String cacheName = "stonks_sandbox_stop_orders";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, StopOrder>())) {
            for (Cache.Entry<String, StopOrder> entry : cursor) {
                String key = entry.getKey();
                try {
                    StopOrder value = entry.getValue();
                    if (value.getSchemaVersion() < StopOrder.CURRENT_SCHEMA_VERSION) {
                        value.setSchemaVersion(StopOrder.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migratePriceAlerts(IgniteCache<String, PriceAlert> cache) {
        String cacheName = "stonks_sandbox_price_alerts";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        try (var cursor = cache.query(new ScanQuery<String, PriceAlert>())) {
            for (Cache.Entry<String, PriceAlert> entry : cursor) {
                String key = entry.getKey();
                try {
                    PriceAlert value = entry.getValue();
                    if (value.getSchemaVersion() < PriceAlert.CURRENT_SCHEMA_VERSION) {
                        value.setSchemaVersion(PriceAlert.CURRENT_SCHEMA_VERSION);
                        cache.put(key, value);
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("[Migration] Failed to read entry key='{}' cache='{}': {}", key, cacheName, e.getMessage());
                    badKeys.add(key);
                }
            }
        }

        for (String key : badKeys) {
            cache.remove(key);
        }
        log.info("[Migration] cache='{}' migrated={} removed={}", cacheName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    // ------------------------------------------------------------------
    // Result holder
    // ------------------------------------------------------------------

    public static class CacheMigrationResult {
        public final int migrated;
        public final int removed;

        public CacheMigrationResult(int migrated, int removed) {
            this.migrated = migrated;
            this.removed  = removed;
        }

        @Override
        public String toString() {
            return "{migrated=" + migrated + ", removed=" + removed + "}";
        }
    }
}
