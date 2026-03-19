package services.sandbox.migration;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import services.sandbox.ignite.SandboxIgniteManager;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;

import javax.cache.Cache;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SandboxMigrationService}.
 *
 * All Ignite infrastructure is mocked — no embedded Ignite node is started.
 */
@SuppressWarnings("unchecked")
class SandboxMigrationServiceTest {

    // Mocked manager and all 6 caches
    private SandboxIgniteManager manager;
    private IgniteCache<String, SandboxUser>   usersCache;
    private IgniteCache<String, Position>       positionsCache;
    private IgniteCache<String, TradeRecord>    tradesCache;
    private IgniteCache<String, LimitOrder>     limitOrdersCache;
    private IgniteCache<String, StopOrder>      stopOrdersCache;
    private IgniteCache<String, PriceAlert>     priceAlertsCache;

    @BeforeEach
    void setUp() {
        manager         = mock(SandboxIgniteManager.class);
        usersCache      = mock(IgniteCache.class);
        positionsCache  = mock(IgniteCache.class);
        tradesCache     = mock(IgniteCache.class);
        limitOrdersCache = mock(IgniteCache.class);
        stopOrdersCache = mock(IgniteCache.class);
        priceAlertsCache = mock(IgniteCache.class);

        when(manager.usersCache()).thenReturn(usersCache);
        when(manager.positionsCache()).thenReturn(positionsCache);
        when(manager.tradesCache()).thenReturn(tradesCache);
        when(manager.limitOrdersCache()).thenReturn(limitOrdersCache);
        when(manager.stopOrdersCache()).thenReturn(stopOrdersCache);
        when(manager.priceAlertsCache()).thenReturn(priceAlertsCache);
    }

    // ------------------------------------------------------------------
    // Helper: stub a cache query cursor with the given entries
    // ------------------------------------------------------------------

    private <K, V> void stubCacheQuery(IgniteCache<K, V> cache, List<Cache.Entry<K, V>> entries) {
        var cursor = mock(org.apache.ignite.cache.query.QueryCursor.class);
        when(cursor.iterator()).thenReturn((Iterator) entries.iterator());
        when(cache.query(any(ScanQuery.class))).thenReturn(cursor);
    }

    /** Build a simple Cache.Entry mock. */
    private <K, V> Cache.Entry<K, V> entry(K key, V value) {
        Cache.Entry<K, V> e = mock(Cache.Entry.class);
        when(e.getKey()).thenReturn(key);
        when(e.getValue()).thenReturn(value);
        return e;
    }

    /**
     * An entry whose getValue() throws, simulating a corrupted / unreadable record.
     */
    private <K, V> Cache.Entry<K, V> corruptedEntry(K key) {
        Cache.Entry<K, V> e = mock(Cache.Entry.class);
        when(e.getKey()).thenReturn(key);
        when(e.getValue()).thenThrow(new RuntimeException("Simulated deserialization failure"));
        return e;
    }

    // ------------------------------------------------------------------
    // Empty stubs for caches we don't care about in a given test
    // ------------------------------------------------------------------

    private void stubAllCachesEmpty() {
        for (IgniteCache<?, ?> cache : List.of(usersCache, positionsCache, tradesCache,
                limitOrdersCache, stopOrdersCache, priceAlertsCache)) {
            stubCacheQuery((IgniteCache) cache, List.of());
        }
    }

    // ==================================================================
    // Test 1: entries with schemaVersion=0 get upgraded to 1
    // ==================================================================

    @Test
    void normalMigration_oldEntries_getUpgradedToCurrentVersion() {
        // Arrange: one SandboxUser with schemaVersion=0 (the default)
        SandboxUser user = new SandboxUser("u1", "Alice", 100_000.0);
        assertEquals(0, user.getSchemaVersion(), "Default schemaVersion must be 0");

        stubCacheQuery(usersCache, List.of(entry("u1", user)));

        // All other caches are empty for this test
        stubCacheQuery(positionsCache, List.of());
        stubCacheQuery(tradesCache, List.of());
        stubCacheQuery(limitOrdersCache, List.of());
        stubCacheQuery(stopOrdersCache, List.of());
        stubCacheQuery(priceAlertsCache, List.of());

        // Act
        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        // Assert: the entry was re-saved with the new schema version
        ArgumentCaptor<SandboxUser> captor = ArgumentCaptor.forClass(SandboxUser.class);
        verify(usersCache, times(1)).put(eq("u1"), captor.capture());
        assertEquals(SandboxUser.CURRENT_SCHEMA_VERSION, captor.getValue().getSchemaVersion());

        // Summary reflects 1 migrated, 0 removed for the users cache
        SandboxMigrationService.CacheMigrationResult usersResult = results.get("stonks_sandbox_users");
        assertNotNull(usersResult);
        assertEquals(1, usersResult.migrated);
        assertEquals(0, usersResult.removed);
    }

    // ==================================================================
    // Test 2: corrupted entry gets removed
    // ==================================================================

    @Test
    void corruptedEntry_getsRemovedFromCache() {
        // Arrange: users cache has one corrupted entry
        Cache.Entry<String, SandboxUser> bad = corruptedEntry("corrupt-key");
        stubCacheQuery(usersCache, List.of(bad));

        // Other caches empty
        stubCacheQuery(positionsCache, List.of());
        stubCacheQuery(tradesCache, List.of());
        stubCacheQuery(limitOrdersCache, List.of());
        stubCacheQuery(stopOrdersCache, List.of());
        stubCacheQuery(priceAlertsCache, List.of());

        // Act
        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        // Assert: the bad key is removed, never re-saved
        verify(usersCache, times(1)).remove("corrupt-key");
        verify(usersCache, never()).put(any(), any());

        SandboxMigrationService.CacheMigrationResult usersResult = results.get("stonks_sandbox_users");
        assertNotNull(usersResult);
        assertEquals(0, usersResult.migrated);
        assertEquals(1, usersResult.removed);
    }

    // ==================================================================
    // Test 3: already-migrated entries (schemaVersion=1) cause no writes
    // ==================================================================

    @Test
    void cleanCache_allAtCurrentVersion_noWritesHappen() {
        // Arrange: build entries already at CURRENT_SCHEMA_VERSION for every cache
        SandboxUser user = new SandboxUser("u1", "Bob", 50_000.0);
        user.setSchemaVersion(SandboxUser.CURRENT_SCHEMA_VERSION);

        Position pos = new Position("u1", "SBER", "inst1", 10, 300.0);
        pos.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);

        TradeRecord trade = new TradeRecord("t1", "u1", "SBER", "BUY", 10, 300.0, 0.3, Instant.now());
        trade.setSchemaVersion(TradeRecord.CURRENT_SCHEMA_VERSION);

        LimitOrder lo = new LimitOrder("lo1", "u1", "Bob", "SBER", "BUY", 5, 295.0, Instant.now());
        lo.setSchemaVersion(LimitOrder.CURRENT_SCHEMA_VERSION);

        StopOrder so = new StopOrder("so1", "u1", "SBER", "SL", 270.0, Instant.now());
        so.setSchemaVersion(StopOrder.CURRENT_SCHEMA_VERSION);

        PriceAlert pa = new PriceAlert("pa1", "u1", "SBER", 320.0, true, Instant.now());
        pa.setSchemaVersion(PriceAlert.CURRENT_SCHEMA_VERSION);

        stubCacheQuery(usersCache,       List.of(entry("u1",  user)));
        stubCacheQuery(positionsCache,   List.of(entry("p1",  pos)));
        stubCacheQuery(tradesCache,      List.of(entry("t1",  trade)));
        stubCacheQuery(limitOrdersCache, List.of(entry("lo1", lo)));
        stubCacheQuery(stopOrdersCache,  List.of(entry("so1", so)));
        stubCacheQuery(priceAlertsCache, List.of(entry("pa1", pa)));

        // Act
        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        // Assert: no cache.put() and no cache.remove() called on any cache
        verify(usersCache,       never()).put(any(), any());
        verify(positionsCache,   never()).put(any(), any());
        verify(tradesCache,      never()).put(any(), any());
        verify(limitOrdersCache, never()).put(any(), any());
        verify(stopOrdersCache,  never()).put(any(), any());
        verify(priceAlertsCache, never()).put(any(), any());

        verify(usersCache,       never()).remove(any());
        verify(positionsCache,   never()).remove(any());
        verify(tradesCache,      never()).remove(any());
        verify(limitOrdersCache, never()).remove(any());
        verify(stopOrdersCache,  never()).remove(any());
        verify(priceAlertsCache, never()).remove(any());

        // Every result should have 0 migrated and 0 removed
        for (SandboxMigrationService.CacheMigrationResult r : results.values()) {
            assertEquals(0, r.migrated, "No entries should be migrated in a clean cache");
            assertEquals(0, r.removed,  "No entries should be removed from a clean cache");
        }
    }

    // ==================================================================
    // Test 4: mixed cache — some old, some current, some corrupted
    // ==================================================================

    @Test
    void mixedCache_correctlyCountsMigratedAndRemoved() {
        // Arrange: positions cache has 2 old, 1 current, 1 corrupted
        Position old1 = new Position("u1", "SBER", "i1", 10, 300.0);
        // schemaVersion defaults to 0

        Position old2 = new Position("u2", "GAZP", "i2", 20, 180.0);
        // schemaVersion defaults to 0

        Position current = new Position("u3", "LKOH", "i3", 5, 7500.0);
        current.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);

        stubCacheQuery(positionsCache, List.of(
                entry("p1", old1),
                entry("p2", old2),
                entry("p3", current),
                corruptedEntry("p4")
        ));

        // Other caches empty
        stubCacheQuery(usersCache, List.of());
        stubCacheQuery(tradesCache, List.of());
        stubCacheQuery(limitOrdersCache, List.of());
        stubCacheQuery(stopOrdersCache, List.of());
        stubCacheQuery(priceAlertsCache, List.of());

        // Act
        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        // Assert
        SandboxMigrationService.CacheMigrationResult posResult = results.get("stonks_sandbox_positions");
        assertNotNull(posResult);
        assertEquals(2, posResult.migrated, "2 old entries should be migrated");
        assertEquals(1, posResult.removed,  "1 corrupted entry should be removed");

        verify(positionsCache, times(2)).put(any(), any());
        verify(positionsCache, times(1)).remove("p4");
        verify(positionsCache, never()).remove("p3"); // current version — must not be touched
    }
}
