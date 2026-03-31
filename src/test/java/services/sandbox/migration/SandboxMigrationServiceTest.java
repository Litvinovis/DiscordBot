package services.sandbox.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SandboxMigrationService}.
 *
 * All Ignite 3 infrastructure is mocked — no cluster is started.
 */
@SuppressWarnings("unchecked")
class SandboxMigrationServiceTest {

    private SandboxIgniteManager manager;
    private SandboxUserRepository usersRepo;
    private PositionRepository positionsRepo;
    private TradeRepository tradesRepo;
    private LimitOrderRepository limitOrdersRepo;
    private StopOrderRepository stopOrdersRepo;
    private PriceAlertRepository priceAlertsRepo;

    @BeforeEach
    void setUp() {
        manager          = mock(SandboxIgniteManager.class);
        usersRepo        = mock(SandboxUserRepository.class);
        positionsRepo    = mock(PositionRepository.class);
        tradesRepo       = mock(TradeRepository.class);
        limitOrdersRepo  = mock(LimitOrderRepository.class);
        stopOrdersRepo   = mock(StopOrderRepository.class);
        priceAlertsRepo  = mock(PriceAlertRepository.class);

        when(manager.usersRepo()).thenReturn(usersRepo);
        when(manager.positionsRepo()).thenReturn(positionsRepo);
        when(manager.tradesRepo()).thenReturn(tradesRepo);
        when(manager.limitOrdersRepo()).thenReturn(limitOrdersRepo);
        when(manager.stopOrdersRepo()).thenReturn(stopOrdersRepo);
        when(manager.priceAlertsRepo()).thenReturn(priceAlertsRepo);
    }

    private void stubAllReposEmpty() {
        when(usersRepo.findAll()).thenReturn(Collections.emptyList());
        when(positionsRepo.findAll()).thenReturn(Collections.emptyList());
        when(tradesRepo.findAll()).thenReturn(Collections.emptyList());
        when(limitOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(stopOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(priceAlertsRepo.findAll()).thenReturn(Collections.emptyList());
    }

    // ==================================================================
    // Test 1: entries with schemaVersion=0 get upgraded to current version
    // ==================================================================

    @Test
    void normalMigration_oldEntries_getUpgradedToCurrentVersion() {
        SandboxUser user = new SandboxUser("u1", "Alice", 100_000.0);
        assertEquals(0, user.getSchemaVersion(), "Default schemaVersion must be 0");

        when(usersRepo.findAll()).thenReturn(List.of(user));
        when(positionsRepo.findAll()).thenReturn(Collections.emptyList());
        when(tradesRepo.findAll()).thenReturn(Collections.emptyList());
        when(limitOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(stopOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(priceAlertsRepo.findAll()).thenReturn(Collections.emptyList());

        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        verify(usersRepo, times(1)).save(eq("u1"), any(SandboxUser.class));

        SandboxMigrationService.CacheMigrationResult usersResult = results.get("sandbox_users");
        assertNotNull(usersResult);
        assertEquals(1, usersResult.migrated);
        assertEquals(0, usersResult.removed);
    }

    // ==================================================================
    // Test 2: already-migrated entries cause no writes
    // ==================================================================

    @Test
    void cleanRepo_allAtCurrentVersion_noWritesHappen() {
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

        when(usersRepo.findAll()).thenReturn(List.of(user));
        when(positionsRepo.findAll()).thenReturn(List.of(pos));
        when(tradesRepo.findAll()).thenReturn(List.of(trade));
        when(limitOrdersRepo.findAll()).thenReturn(List.of(lo));
        when(stopOrdersRepo.findAll()).thenReturn(List.of(so));
        when(priceAlertsRepo.findAll()).thenReturn(List.of(pa));

        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        verify(usersRepo,       never()).save(any(), any());
        verify(positionsRepo,   never()).save(any(), any());
        verify(tradesRepo,      never()).save(any(), any());
        verify(limitOrdersRepo, never()).save(any(), any());
        verify(stopOrdersRepo,  never()).save(any(), any());
        verify(priceAlertsRepo, never()).save(any(), any());

        verify(usersRepo,       never()).delete(any());
        verify(positionsRepo,   never()).delete(any());
        verify(tradesRepo,      never()).delete(any());
        verify(limitOrdersRepo, never()).delete(any());
        verify(stopOrdersRepo,  never()).delete(any());
        verify(priceAlertsRepo, never()).delete(any());

        for (SandboxMigrationService.CacheMigrationResult r : results.values()) {
            assertEquals(0, r.migrated, "No entries should be migrated in a clean repo");
            assertEquals(0, r.removed,  "No entries should be removed from a clean repo");
        }
    }

    // ==================================================================
    // Test 3: mixed — some old, some current
    // ==================================================================

    @Test
    void mixedRepo_correctlyCountsMigrated() {
        Position old1 = new Position("u1", "SBER", "i1", 10, 300.0); // schemaVersion=0
        Position old2 = new Position("u2", "GAZP", "i2", 20, 180.0); // schemaVersion=0
        Position current = new Position("u3", "LKOH", "i3", 5, 7500.0);
        current.setSchemaVersion(Position.CURRENT_SCHEMA_VERSION);

        when(usersRepo.findAll()).thenReturn(Collections.emptyList());
        when(positionsRepo.findAll()).thenReturn(List.of(old1, old2, current));
        when(tradesRepo.findAll()).thenReturn(Collections.emptyList());
        when(limitOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(stopOrdersRepo.findAll()).thenReturn(Collections.emptyList());
        when(priceAlertsRepo.findAll()).thenReturn(Collections.emptyList());

        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        SandboxMigrationService.CacheMigrationResult posResult = results.get("sandbox_positions");
        assertNotNull(posResult);
        assertEquals(2, posResult.migrated, "2 old entries should be migrated");
        assertEquals(0, posResult.removed,  "no corrupted entries in this test");

        verify(positionsRepo, times(2)).save(any(), any());
        verify(positionsRepo, never()).delete(any());
    }

    // ==================================================================
    // Test 4: all repos empty — summary is all zeros
    // ==================================================================

    @Test
    void emptyRepos_summaryAllZeros() {
        stubAllReposEmpty();

        SandboxMigrationService svc = new SandboxMigrationService(manager);
        Map<String, SandboxMigrationService.CacheMigrationResult> results = svc.runMigrations();

        assertEquals(6, results.size(), "Should have results for all 6 tables");
        for (SandboxMigrationService.CacheMigrationResult r : results.values()) {
            assertEquals(0, r.migrated);
            assertEquals(0, r.removed);
        }
    }
}
