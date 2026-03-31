package services.sandbox.ignite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.IgniteSql;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IgniteHealthService}.
 *
 * Apache Ignite 3 client is mocked — no embedded cluster is started.
 * Tests call {@code runCheck()} directly (package-private) to avoid scheduler timing.
 */
@SuppressWarnings("unchecked")
class IgniteHealthServiceTest {

    private SandboxIgniteManager manager;
    private IgniteClient igniteClient;
    private IgniteSql sql;

    @BeforeEach
    void setUp() {
        manager = mock(SandboxIgniteManager.class);
        igniteClient = mock(IgniteClient.class);
        sql = mock(IgniteSql.class);

        when(manager.getIgniteClient()).thenReturn(igniteClient);
        when(igniteClient.sql()).thenReturn(sql);
    }

    private ResultSet<SqlRow> mockResultSet(boolean hasNext) {
        ResultSet<SqlRow> rs = mock(ResultSet.class);
        when(rs.hasNext()).thenReturn(hasNext);
        if (hasNext) {
            when(rs.next()).thenReturn(mock(SqlRow.class));
        }
        return rs;
    }

    // ------------------------------------------------------------------
    // Test 1: healthy cluster — no failure counted
    // ------------------------------------------------------------------

    @Test
    void healthyIgnite_noFailuresCounted() {
        when(sql.execute(any(), anyString())).thenReturn(mockResultSet(true));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(0, service.getHealthCheckFailures(),
                "No failures should be counted when Ignite responds normally");
    }

    @Test
    void healthyIgniteEmptyResult_noFailuresCounted() {
        when(sql.execute(any(), anyString())).thenReturn(mockResultSet(false));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(0, service.getHealthCheckFailures());
    }

    // ------------------------------------------------------------------
    // Test 2: SQL throws — failure is counted
    // ------------------------------------------------------------------

    @Test
    void sqlThrowsException_failureIsCounted() {
        when(sql.execute(any(), anyString())).thenThrow(new RuntimeException("Connection refused"));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(1, service.getHealthCheckFailures(),
                "Failure counter should increment when SQL throws");
    }

    // ------------------------------------------------------------------
    // Test 3: multiple consecutive failures accumulate
    // ------------------------------------------------------------------

    @Test
    void multipleFailures_counterAccumulates() {
        when(sql.execute(any(), anyString())).thenThrow(new RuntimeException("Network timeout"));

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
        when(sql.execute(any(), anyString()))
                .thenThrow(new RuntimeException("Transient error"))
                .thenReturn(mockResultSet(true));

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();
        assertEquals(1, service.getHealthCheckFailures());

        service.runCheck();
        assertEquals(1, service.getHealthCheckFailures(),
                "Counter must not grow when subsequent check passes");
    }

    // ------------------------------------------------------------------
    // Test 5: null client causes a failure
    // ------------------------------------------------------------------

    @Test
    void nullIgniteClient_causesFailure() {
        when(manager.getIgniteClient()).thenReturn(null);

        IgniteHealthService service = new IgniteHealthService(manager);
        service.runCheck();

        assertEquals(1, service.getHealthCheckFailures(),
                "A null Ignite client should cause the health-check to fail");
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
        when(sql.execute(any(), anyString())).thenReturn(mockResultSet(true));
        IgniteHealthService service = new IgniteHealthService(manager);
        assertEquals(0, service.getHealthCheckFailures(),
                "Before any check the failure counter must be 0");
    }
}
