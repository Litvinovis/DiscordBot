package services.sandbox.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.ignite.SandboxIgniteManager;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;
import services.sandbox.repository.LimitOrderRepository;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.PriceAlertRepository;
import services.sandbox.repository.SandboxUserRepository;
import services.sandbox.repository.StopOrderRepository;
import services.sandbox.repository.TradeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs schema migrations across all 6 Ignite 3 sandbox tables on startup.
 *
 * <p>For each table row the service checks the {@code schemaVersion}. If it is below
 * the current version, the record is upgraded and re-saved. Corrupt records that
 * cannot be read are collected and removed after the scan.
 *
 * <p>Data is read via {@code SELECT * FROM table} through the repository {@code findAll()}
 * method, which uses {@link org.apache.ignite.client.IgniteClient#sql()}.
 */
public class SandboxMigrationService {

    private static final Logger log = LoggerFactory.getLogger(SandboxMigrationService.class);

    private final SandboxIgniteManager igniteManager;

    /**
     * Создаёт сервис миграций с указанным менеджером Ignite 3.
     *
     * @param igniteManager менеджер репозиториев Ignite 3
     */
    public SandboxMigrationService(SandboxIgniteManager igniteManager) {
        this.igniteManager = igniteManager;
    }

    /**
     * Runs migrations for all 6 tables and logs a summary.
     *
     * @return map of table-name -> {@link CacheMigrationResult}
     */
    public Map<String, CacheMigrationResult> runMigrations() {
        Map<String, CacheMigrationResult> summary = new LinkedHashMap<>();

        summary.put("sandbox_users",       migrateUsers(igniteManager.usersRepo()));
        summary.put("sandbox_positions",   migratePositions(igniteManager.positionsRepo()));
        summary.put("sandbox_trades",      migrateTrades(igniteManager.tradesRepo()));
        summary.put("sandbox_limit_orders", migrateLimitOrders(igniteManager.limitOrdersRepo()));
        summary.put("sandbox_stop_orders", migrateStopOrders(igniteManager.stopOrdersRepo()));
        summary.put("sandbox_price_alerts", migratePriceAlerts(igniteManager.priceAlertsRepo()));

        int totalMigrated = summary.values().stream().mapToInt(r -> r.migrated).sum();
        int totalRemoved  = summary.values().stream().mapToInt(r -> r.removed).sum();
        log.info("[Migration] Complete. Migrated={} Removed={} Details={}",
                totalMigrated, totalRemoved, summary);

        return summary;
    }

    // ------------------------------------------------------------------
    // Per-table migration helpers
    // ------------------------------------------------------------------

    private CacheMigrationResult migrateUsers(SandboxUserRepository repo) {
        String tableName = "sandbox_users";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<SandboxUser> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (SandboxUser value : all) {
            String key = value.getUserId();
            try {
                if (value.getSchemaVersion() < SandboxUser.CURRENT_SCHEMA_VERSION) {
                    // v1 -> v2: initialise currencyHoldings if null
                    if (value.getSchemaVersion() < 2) {
                        if (value.getCurrencyHoldings() == null) {
                            value.setCurrencyHoldings(new HashMap<>());
                        }
                    }
                    value.setSchemaVersion(SandboxUser.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate user key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migratePositions(PositionRepository repo) {
        String tableName = "sandbox_positions";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<Position> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (Position value : all) {
            String key = value.getUserId() + "::" + value.getTicker();
            try {
                if (value.getSchemaVersion() < Position.CURRENT_SCHEMA_VERSION) {
                    value.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate position key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateTrades(TradeRepository repo) {
        String tableName = "sandbox_trades";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<TradeRecord> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (TradeRecord value : all) {
            String key = value.getId();
            try {
                if (value.getSchemaVersion() < TradeRecord.CURRENT_SCHEMA_VERSION) {
                    value.setSchemaVersion(TradeRecord.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate trade key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateLimitOrders(LimitOrderRepository repo) {
        String tableName = "sandbox_limit_orders";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<LimitOrder> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (LimitOrder value : all) {
            String key = value.getId();
            try {
                if (value.getSchemaVersion() < LimitOrder.CURRENT_SCHEMA_VERSION) {
                    value.setSchemaVersion(LimitOrder.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate limit order key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migrateStopOrders(StopOrderRepository repo) {
        String tableName = "sandbox_stop_orders";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<StopOrder> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (StopOrder value : all) {
            String key = value.getId();
            try {
                if (value.getSchemaVersion() < StopOrder.CURRENT_SCHEMA_VERSION) {
                    value.setSchemaVersion(StopOrder.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate stop order key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    private CacheMigrationResult migratePriceAlerts(PriceAlertRepository repo) {
        String tableName = "sandbox_price_alerts";
        int migrated = 0;
        List<String> badKeys = new ArrayList<>();

        List<PriceAlert> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            log.warn("[Migration] Failed to read table '{}': {}", tableName, e.getMessage());
            return new CacheMigrationResult(0, 0);
        }

        for (PriceAlert value : all) {
            String key = value.getId();
            try {
                if (value.getSchemaVersion() < PriceAlert.CURRENT_SCHEMA_VERSION) {
                    value.setSchemaVersion(PriceAlert.CURRENT_SCHEMA_VERSION);
                    repo.save(key, value);
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to migrate price alert key='{}': {}", key, e.getMessage());
                badKeys.add(key);
            }
        }

        for (String key : badKeys) {
            try { repo.delete(key); } catch (Exception ignored) {}
        }
        log.info("[Migration] table='{}' migrated={} removed={}", tableName, migrated, badKeys.size());
        return new CacheMigrationResult(migrated, badKeys.size());
    }

    // ------------------------------------------------------------------
    // Result holder
    // ------------------------------------------------------------------

    /**
     * Итог миграции одной таблицы: количество обновлённых и удалённых записей.
     */
    public static class CacheMigrationResult {
        public final int migrated;
        public final int removed;

        /**
         * Создаёт результат миграции таблицы.
         *
         * @param migrated количество обновлённых (смигрированных) записей
         * @param removed  количество удалённых повреждённых записей
         */
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
