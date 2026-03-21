package services.sandbox;

import org.apache.ignite.IgniteCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.sandbox.model.SandboxUser;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SandboxCurrencyService}.
 *
 * All Ignite and CBR dependencies are mocked.
 */
@SuppressWarnings("unchecked")
class SandboxCurrencyServiceTest {

    private IgniteCache<String, SandboxUser> usersCache;
    private CbrRateService cbrRateService;
    private ConcurrentHashMap<String, ReentrantLock> locks;
    private SandboxCurrencyService service;

    private static final String USER_ID = "user123";
    private static final double START_CASH = 100_000.0;

    @BeforeEach
    void setUp() {
        usersCache = mock(IgniteCache.class);
        cbrRateService = mock(CbrRateService.class);
        locks = new ConcurrentHashMap<>();
        service = new SandboxCurrencyService(usersCache, cbrRateService, locks);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SandboxUser newUser() {
        SandboxUser u = new SandboxUser(USER_ID, "TestUser", START_CASH);
        return u;
    }

    private Map<String, BigDecimal> ratesMap() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("90.00"));
        rates.put("EUR", new BigDecimal("100.00"));
        rates.put("CNY", new BigDecimal("12.50"));
        rates.put("GBP", new BigDecimal("115.00"));
        rates.put("CHF", new BigDecimal("102.00"));
        rates.put("JPY", new BigDecimal("0.62"));
        rates.put("HKD", new BigDecimal("11.50"));
        return rates;
    }

    // -----------------------------------------------------------------------
    // buyCurrency — happy path
    // -----------------------------------------------------------------------

    @Test
    void buyCurrency_usd_deductsCashAndAddsHolding() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.buyCurrency(USER_ID, "USD", new BigDecimal("9000"));

        assertTrue(result.contains("Куплено"), "Should contain 'Куплено'");
        assertTrue(result.contains("USD"), "Should mention USD");
        // 9000 RUB / 90 RUB/USD = 100 USD
        assertTrue(result.contains("100"), "Should show 100 USD");

        // Verify user was saved
        verify(usersCache).put(eq(USER_ID), any(SandboxUser.class));

        // Verify cash was deducted
        assertEquals(START_CASH - 9000.0, user.getCash(), 0.01);

        // Verify holding
        double held = user.getCurrencyHoldings().getOrDefault("USD", 0.0);
        assertEquals(100.0, held, 0.001);
    }

    @Test
    void buyCurrency_caseSensitivity_lowerCaseConverted() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.buyCurrency(USER_ID, "usd", new BigDecimal("900"));
        assertTrue(result.contains("Куплено"));
        assertTrue(result.contains("USD"));
    }

    @Test
    void buyCurrency_addsToExistingHolding() {
        SandboxUser user = newUser();
        // User already has 50 USD
        user.getCurrencyHoldings().put("USD", 50.0);
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        service.buyCurrency(USER_ID, "USD", new BigDecimal("9000")); // +100 USD

        double held = user.getCurrencyHoldings().get("USD");
        assertEquals(150.0, held, 0.001);
    }

    // -----------------------------------------------------------------------
    // buyCurrency — error cases
    // -----------------------------------------------------------------------

    @Test
    void buyCurrency_userNotRegistered_returnsError() {
        when(usersCache.get(USER_ID)).thenReturn(null);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.buyCurrency(USER_ID, "USD", new BigDecimal("1000"));
        assertTrue(result.contains("регистрация"));
    }

    @Test
    void buyCurrency_unsupportedCurrency_returnsError() {
        String result = service.buyCurrency(USER_ID, "RUB", new BigDecimal("1000"));
        assertTrue(result.contains("не поддерживается"));
    }

    @Test
    void buyCurrency_negativeAmount_returnsError() {
        String result = service.buyCurrency(USER_ID, "USD", new BigDecimal("-100"));
        assertTrue(result.contains("> 0"));
    }

    @Test
    void buyCurrency_zeroAmount_returnsError() {
        String result = service.buyCurrency(USER_ID, "USD", BigDecimal.ZERO);
        assertTrue(result.contains("> 0"));
    }

    @Test
    void buyCurrency_insufficientCash_returnsError() {
        SandboxUser user = new SandboxUser(USER_ID, "TestUser", 500.0); // only 500 RUB
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.buyCurrency(USER_ID, "USD", new BigDecimal("1000"));
        assertTrue(result.contains("Недостаточно"));
    }

    @Test
    void buyCurrency_rateNotAvailable_returnsError() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(new HashMap<>()); // empty rates

        String result = service.buyCurrency(USER_ID, "USD", new BigDecimal("1000"));
        assertTrue(result.contains("Не удалось получить курс"));
    }

    // -----------------------------------------------------------------------
    // sellCurrency — happy path
    // -----------------------------------------------------------------------

    @Test
    void sellCurrency_usd_creditsRubAndReducesHolding() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 100.0);
        user.setCash(0.0); // spent all cash buying USD earlier
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.sellCurrency(USER_ID, "USD", new BigDecimal("50"));

        assertTrue(result.contains("Продано"));
        assertTrue(result.contains("USD"));
        // 50 USD * 90 RUB = 4500 RUB
        assertEquals(4500.0, user.getCash(), 0.01);
        assertEquals(50.0, user.getCurrencyHoldings().get("USD"), 0.001);
        verify(usersCache).put(eq(USER_ID), any(SandboxUser.class));
    }

    @Test
    void sellCurrency_allHolding_removesEntry() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 100.0);
        user.setCash(0.0);
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        service.sellCurrency(USER_ID, "USD", new BigDecimal("100"));

        assertFalse(user.getCurrencyHoldings().containsKey("USD"),
                "USD entry should be removed when holding reaches zero");
    }

    // -----------------------------------------------------------------------
    // sellCurrency — error cases
    // -----------------------------------------------------------------------

    @Test
    void sellCurrency_userNotRegistered_returnsError() {
        when(usersCache.get(USER_ID)).thenReturn(null);

        String result = service.sellCurrency(USER_ID, "USD", new BigDecimal("10"));
        assertTrue(result.contains("регистрация"));
    }

    @Test
    void sellCurrency_insufficientHolding_returnsError() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 10.0);
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.sellCurrency(USER_ID, "USD", new BigDecimal("100"));
        assertTrue(result.contains("Недостаточно"));
    }

    @Test
    void sellCurrency_noHolding_returnsError() {
        SandboxUser user = newUser();
        // no USD holding
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.sellCurrency(USER_ID, "USD", new BigDecimal("10"));
        assertTrue(result.contains("Недостаточно"));
    }

    @Test
    void sellCurrency_unsupportedCurrency_returnsError() {
        String result = service.sellCurrency(USER_ID, "BTC", new BigDecimal("1"));
        assertTrue(result.contains("не поддерживается"));
    }

    // -----------------------------------------------------------------------
    // currencyPortfolio
    // -----------------------------------------------------------------------

    @Test
    void currencyPortfolio_userNotRegistered_returnsError() {
        when(usersCache.get(USER_ID)).thenReturn(null);
        String result = service.currencyPortfolio(USER_ID);
        assertTrue(result.contains("регистрация"));
    }

    @Test
    void currencyPortfolio_noHoldings_returnsEmptyMessage() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.currencyPortfolio(USER_ID);
        assertTrue(result.contains("нет"));
    }

    @Test
    void currencyPortfolio_withHoldings_showsDetails() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 100.0);
        user.getCurrencyHoldings().put("EUR", 50.0);
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String result = service.currencyPortfolio(USER_ID);
        assertTrue(result.contains("USD"));
        assertTrue(result.contains("EUR"));
        assertTrue(result.contains("₽"));
    }

    // -----------------------------------------------------------------------
    // currencyBalanceLine
    // -----------------------------------------------------------------------

    @Test
    void currencyBalanceLine_noHoldings_returnsEmpty() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String line = service.currencyBalanceLine(USER_ID);
        assertTrue(line == null || line.isBlank());
    }

    @Test
    void currencyBalanceLine_withHolding_containsCurrencyInfo() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 200.0);
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        String line = service.currencyBalanceLine(USER_ID);
        assertFalse(line == null || line.isBlank());
        assertTrue(line.contains("USD"));
        assertTrue(line.contains("200"));
    }

    // -----------------------------------------------------------------------
    // totalCurrencyValueInRub
    // -----------------------------------------------------------------------

    @Test
    void totalCurrencyValueInRub_withUsd_returnsCorrectValue() {
        SandboxUser user = newUser();
        user.getCurrencyHoldings().put("USD", 100.0); // 100 * 90 = 9000
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        BigDecimal total = service.totalCurrencyValueInRub(USER_ID);
        assertEquals(new BigDecimal("9000.00"), total);
    }

    @Test
    void totalCurrencyValueInRub_noHoldings_returnsZero() {
        SandboxUser user = newUser();
        when(usersCache.get(USER_ID)).thenReturn(user);
        when(cbrRateService.fetchRates()).thenReturn(ratesMap());

        BigDecimal total = service.totalCurrencyValueInRub(USER_ID);
        assertEquals(0, total.compareTo(BigDecimal.ZERO));
    }

    @Test
    void totalCurrencyValueInRub_userNull_returnsZero() {
        when(usersCache.get(USER_ID)).thenReturn(null);

        BigDecimal total = service.totalCurrencyValueInRub(USER_ID);
        assertEquals(0, total.compareTo(BigDecimal.ZERO));
    }
}
