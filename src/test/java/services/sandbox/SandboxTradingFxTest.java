package services.sandbox;

import com.discord.stonks.config.SandboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.*;
import services.tbank.TInvestApi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Регрессии на настоящем SandboxTradingService: пересчёт валюты, пополнения в ROI, рейтинг.
 */
class SandboxTradingFxTest {

	private static final BigDecimal USD_RATE = new BigDecimal("90");

	private SandboxUserRepository users;
	private PositionRepository positions;
	private SandboxPriceService priceService;
	private CbrRateService cbr;
	private SandboxTradingService service;
	private SandboxUser alice;

	@BeforeEach
	void setUp() {
		TInvestApi api = mock(TInvestApi.class);
		TInvestApi.InstrumentsService instruments = mock(TInvestApi.InstrumentsService.class);
		when(api.getInstrumentsService()).thenReturn(instruments);
		when(instruments.getAllSharesSync()).thenReturn(List.of(
				Share.newBuilder().setTicker("SBER").setUid("uid-sber").setCurrency("rub").build(),
				Share.newBuilder().setTicker("AAPL").setUid("uid-aapl").setCurrency("usd").build()));

		users = mock(SandboxUserRepository.class);
		positions = mock(PositionRepository.class);
		priceService = mock(SandboxPriceService.class);
		cbr = mock(CbrRateService.class);
		when(cbr.fetchRates()).thenReturn(Map.of("USD", USD_RATE));

		PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
		when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

		SandboxProperties props = new SandboxProperties(null, null, null, null, List.of("SBER", "AAPL"));
		service = new SandboxTradingService(api, props, users, positions, mock(TradeRepository.class),
				mock(LimitOrderRepository.class), mock(StopOrderRepository.class), mock(PriceAlertRepository.class),
				priceService, new SandboxMessageFormatter(), new SandboxRiskManager(props), tm);
		ReflectionTestUtils.setField(service, "cbrRateService", cbr);
		SandboxCurrencyService currencies = mock(SandboxCurrencyService.class);
		when(currencies.totalCurrencyValueInRub(anyString())).thenReturn(BigDecimal.ZERO);
		when(currencies.currencyBalanceLine(anyString())).thenReturn("");
		ReflectionTestUtils.setField(service, "currencyService", currencies);

		alice = new SandboxUser("alice", "Alice", new BigDecimal("1000000"));
		when(users.findById("alice")).thenReturn(alice);
		when(positions.findByUserId(anyString())).thenReturn(List.of());
	}

	@Test
	void buyingUsdShare_debitsRublesAtCbrRate() throws Exception {
		when(priceService.loadPrice("uid-aapl")).thenReturn(new BigDecimal("200"));

		String result = service.buy("alice", "Alice", "AAPL", 10);

		assertTrue(SandboxTradingService.isExecuted(result), result);
		// 10 × $200 × 90 = 180 000 ₽ + комиссия 0,1% = 180 ₽. Было: списывалось 2 000 «₽»
		assertEquals(0, new BigDecimal("819820").compareTo(alice.getCash()), "cash=" + alice.getCash());
	}

	@Test
	void buyingRubShare_unchanged() throws Exception {
		when(priceService.loadPrice("uid-sber")).thenReturn(new BigDecimal("300"));

		service.buy("alice", "Alice", "SBER", 10);

		// 3 000 ₽ + минимальная комиссия 3 ₽
		assertEquals(0, new BigDecimal("996997").compareTo(alice.getCash()), "cash=" + alice.getCash());
	}

	@Test
	void usdShare_withoutRate_tradeRefusedAsTransient() throws Exception {
		when(cbr.fetchRates()).thenReturn(Map.of());
		when(priceService.loadPrice("uid-aapl")).thenReturn(new BigDecimal("200"));

		String result = service.buy("alice", "Alice", "AAPL", 10);

		assertTrue(result.startsWith("⚠️"), result);
		assertEquals(0, new BigDecimal("1000000").compareTo(alice.getCash()));
	}

	@Test
	void replenish_isNotCountedAsProfit() {
		alice.setDailyBaselineEquity(new BigDecimal("1000000"));
		alice.setWeeklyBaselineEquity(new BigDecimal("1000000"));
		alice.setMonthlyBaselineEquity(new BigDecimal("1000000"));

		service.replenish("alice", "Alice", 200_000);

		assertEquals(0, new BigDecimal("200000").compareTo(alice.getTotalDeposits()));
		assertEquals(0, new BigDecimal("1200000").compareTo(alice.getWeeklyBaselineEquity()));
		assertTrue(service.balance("alice").contains("ROI от старта: +0.00%"), service.balance("alice"));
	}

	@Test
	void top_ranksRealGainAboveDeposit() {
		SandboxUser depositor = new SandboxUser("dep", "Depositor", new BigDecimal("1200000"));
		depositor.setTotalDeposits(new BigDecimal("200000"));
		SandboxUser trader = new SandboxUser("tr", "Trader", new BigDecimal("1100000"));
		when(users.findAll()).thenReturn(new java.util.ArrayList<>(List.of(depositor, trader)));

		String top = service.top("все");

		assertTrue(top.indexOf("Trader") < top.indexOf("Depositor"), top);
		assertTrue(top.contains("Depositor — 0.00%"), top);
	}

	@Test
	void isExecuted_distinguishesRejections() {
		assertTrue(SandboxTradingService.isExecuted("🟢 Куплено 1 SBER"));
		assertTrue(SandboxTradingService.isExecuted("🔴 Продано 1 SBER"));
		assertFalse(SandboxTradingService.isExecuted("❌ Сделка отклонена: превышен риск/плечо."));
		assertFalse(SandboxTradingService.isExecuted("Недостаточно бумаг в портфеле."));
		assertFalse(SandboxTradingService.isExecuted(null));
	}

	@Test
	void rollBaselines_refreshesStaleWeek() {
		alice.setWeeklyBaselineDate(LocalDate.of(2020, 1, 1));
		alice.setWeeklyBaselineEquity(new BigDecimal("1"));
		when(users.findAll()).thenReturn(List.of(alice));

		service.rollBaselines();

		assertNotEquals(LocalDate.of(2020, 1, 1), alice.getWeeklyBaselineDate());
		assertEquals(0, new BigDecimal("1000000").compareTo(alice.getWeeklyBaselineEquity()));
		verify(users, atLeastOnce()).save("alice", alice);
	}
}
