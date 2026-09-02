package services.sandbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.Share;
import com.discord.stonks.config.SandboxProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.StopOrderType;
import services.sandbox.model.TradeSide;
import services.sandbox.model.TradeRecord;
import services.sandbox.repository.LimitOrderRepository;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.PriceAlertRepository;
import services.sandbox.repository.SandboxUserRepository;
import services.sandbox.repository.StopOrderRepository;
import services.sandbox.repository.TradeRepository;
import services.tbank.TInvestApi;

/**
 * Основной сервис торговой песочницы Stonks Bot.
 * Все данные хранятся в PostgreSQL через репозитории, управляемые Spring DataSource.
 */
@Service
public class SandboxTradingService implements
		services.sandbox.api.ISandboxOrderService,
		services.sandbox.api.ISandboxPortfolioService,
		services.sandbox.api.ISandboxRatingService {

	private static final Logger log = LoggerFactory.getLogger(SandboxTradingService.class);
	private static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");
	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.ROOT);
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private static final BigDecimal ONE = BigDecimal.ONE;
	private static final int SCALE = 8;

	private final SandboxUserRepository users;
	private final PositionRepository positions;
	private final TradeRepository trades;
	private final LimitOrderRepository limitOrders;
	private final StopOrderRepository stopOrders;
	private final PriceAlertRepository priceAlerts;
	private final SandboxPriceService priceService;
	private final SandboxMessageFormatter formatter;
	private final SandboxRiskManager riskManager;
	private final Map<String, Share> shareByTicker;
	private final BigDecimal startBalance;
	private final BigDecimal commissionRate;
	private final TransactionTemplate transactions;

	public final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

	@Autowired @Lazy
	private JDA jda;

	@Autowired @Lazy
	private SandboxCurrencyService currencyService;

	public SandboxTradingService(TInvestApi api,
								  SandboxProperties props,
								  SandboxUserRepository users,
								  PositionRepository positions,
								  TradeRepository trades,
								  LimitOrderRepository limitOrders,
								  StopOrderRepository stopOrders,
								  PriceAlertRepository priceAlerts,
								  SandboxPriceService priceService,
								  SandboxMessageFormatter formatter,
								  SandboxRiskManager riskManager,
								  PlatformTransactionManager transactionManager) {
		this.users = users;
		this.positions = positions;
		this.trades = trades;
		this.limitOrders = limitOrders;
		this.stopOrders = stopOrders;
		this.priceAlerts = priceAlerts;
		this.priceService = priceService;
		this.formatter = formatter;
		this.riskManager = riskManager;
		this.transactions = new TransactionTemplate(transactionManager);
		this.startBalance = props.startBalance();
		this.commissionRate = props.commissionRate();

		Set<String> allowed = props.allowedTickers().stream()
				.map(String::toUpperCase)
				.collect(Collectors.toSet());
		this.shareByTicker = api.getInstrumentsService().getAllSharesSync().stream()
				.filter(s -> allowed.contains(s.getTicker().toUpperCase()))
				.collect(Collectors.toMap(s -> s.getTicker().toUpperCase(), s -> s, (a, b) -> a));
	}

	/** Exposes the instrument map for {@link SandboxOrderProcessor}. */
	public Map<String, Share> getShareByTicker() {
		return Collections.unmodifiableMap(shareByTicker);
	}

	// ── User lock ────────────────────────────────────────────────────────────

	private ReentrantLock lockFor(String userId) {
		return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
	}

	// ── Public commands ──────────────────────────────────────────────────────

	public String register(String userId, String userName) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser existing = users.findById(userId);
			if (existing != null) return "Вы уже зарегистрированы в песочнице.";
			SandboxUser user = new SandboxUser(userId, userName, startBalance);
			recordBaseline(user);
			users.save(userId, user);
			return "✅ Регистрация успешна. Стартовый баланс: " + formatter.format(startBalance) + " ₽";
		} finally {
			lock.unlock();
		}
	}

	public String replenish(String userId, String userName, double amount) {
		// NaN проходит любые сравнения как false: "+пополнить NaN" делал баланс NaN
		// и ронял рейтинг для всех (BigDecimal.valueOf(NaN) бросает исключение)
		if (!Double.isFinite(amount) || amount <= 0 || amount > 200_000) {
			return "❌ Сумма пополнения должна быть от 1 до 200 000 ₽.";
		}
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser user = users.findById(userId);
			if (user == null) return "Сначала выполните +регистрация";
			LocalDate today = LocalDate.now(ZONE);
			if (user.getLastReplenishDate() != null &&
				user.getLastReplenishDate().plusDays(30).isAfter(today)) {
				long daysLeft = ChronoUnit.DAYS.between(today, user.getLastReplenishDate().plusDays(30));
				return "⏳ Пополнение доступно раз в 30 дней. Следующее — через **" + daysLeft + " дн.**";
			}
			user.setCash(user.getCash().add(BigDecimal.valueOf(amount)));
			user.setLastReplenishDate(today);
			users.save(userId, user);
			return String.format("💰 Счёт пополнен на **%.0f ₽**. Новый баланс: **%.0f ₽**. Следующее пополнение через 30 дней.",
					amount, user.getCash().doubleValue());
		} finally {
			lock.unlock();
		}
	}

	public String toggleMorningDigest(String userId, String userName, boolean enable) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser user = users.findById(userId);
			if (user == null) return "Сначала выполните +регистрация";
			user.setMorningDigestEnabled(enable);
			users.save(userId, user);
			return enable
				? "☀️ Дайджест портфеля **включён**. По понедельникам в 9:00 (Екатеринбург, UTC+5) ты будешь получать DM с позициями."
				: "🌙 Дайджест портфеля **выключен**.";
		} finally {
			lock.unlock();
		}
	}

	public String assets() {
		if (shareByTicker.isEmpty()) return "Список активов пуст.";
		return "Доступные тикеры: " + String.join(", ", new TreeSet<>(shareByTicker.keySet()));
	}

	public String buy(String userId, String userName, String ticker, int qty) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			return trade(userId, userName, ticker, qty, true);
		} finally {
			lock.unlock();
		}
	}

	public String sell(String userId, String userName, String ticker, int qty) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			return trade(userId, userName, ticker, qty, false);
		} finally {
			lock.unlock();
		}
	}

	// ── Core trade execution (package-private: also called by SandboxOrderProcessor) ──

	String trade(String userId, String userName, String ticker, int qty, boolean buy) {
		if (qty <= 0) return "Количество должно быть > 0";

		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		if (user.getUserName() == null || !user.getUserName().equals(userName)) {
			user.setUserName(userName);
		}
		String upperTicker = ticker.toUpperCase(Locale.ROOT);
		Share share = shareByTicker.get(upperTicker);
		if (share == null) return "Тикер не доступен в песочнице.";
		recordBaseline(user);

		BigDecimal price;
		try {
			price = priceService.loadPrice(share.getUid());
		} catch (Exception e) {
			log.warn("Не удалось загрузить цену для {} : {}", upperTicker, e.getMessage());
			return "⚠️ Не удалось получить текущую цену для " + upperTicker + ". Торговля временно недоступна.";
		}
		if (price.compareTo(ZERO) <= 0) {
			return "⚠️ Цена для " + upperTicker + " недоступна (получено 0.0). Торговля заблокирована до восстановления котировок.";
		}

		String pKey = posKey(userId, upperTicker);
		Position posInCache = positions.findById(pKey);
		Position pos = posInCache != null
				? posInCache
				: new Position(userId, upperTicker, share.getUid(), 0, ZERO);

		if (!buy && pos.getQuantity() < qty) return "Недостаточно бумаг в портфеле.";

		BigDecimal qtyBD = BigDecimal.valueOf(qty);
		BigDecimal turnover = price.multiply(qtyBD);
		// Комиссия считается от точного оборота и округляется до копеек:
		// прогон через double терял точность ровно там, где её обещал сохранить
		BigDecimal feeRaw = turnover.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
		BigDecimal fee = feeRaw.compareTo(ONE) < 0 ? ONE : feeRaw;

		BigDecimal origCash = user.getCash();
		BigDecimal origTotalFees = user.getTotalFees();
		BigDecimal origAvgPrice = pos.getAvgPrice();

		// Все записи идут одной транзакцией: раньше позиция, пользователь и сделка
		// сохранялись независимо, и сбой между ними оставлял портфель рассогласованным
		// (бумаги есть — деньги не списаны, или наоборот).
		boolean[] marginCalled = {false};
		String result = transactions.execute(status -> {
			if (buy) {
				user.setCash(origCash.subtract(turnover).subtract(fee));
				int newQty = pos.getQuantity() + qty;
				BigDecimal newAvg = origAvgPrice
						.multiply(BigDecimal.valueOf(pos.getQuantity()))
						.add(turnover)
						.divide(BigDecimal.valueOf(newQty), SCALE, RoundingMode.HALF_UP);
				pos.setQuantity(newQty);
				pos.setAvgPrice(newAvg);
				positions.save(pKey, pos);
			} else {
				user.setCash(origCash.add(turnover).subtract(fee));
				pos.setQuantity(pos.getQuantity() - qty);
				if (pos.getQuantity() == 0) {
					positions.delete(pKey);
					stopOrders.deleteByUserAndTicker(userId, upperTicker);
				} else {
					positions.save(pKey, pos);
				}
			}
			user.setTotalFees(origTotalFees.add(fee));
			rebalanceDebt(user, userId);

			BigDecimal eq = equity(userId, user);
			BigDecimal gross = grossPositionValue(userId);
			BigDecimal borrowed = user.getBorrowed();
			RiskCheckResult risk = riskManager.evaluate(eq, gross, borrowed);

			// EQUITY_ZERO и LEVERAGE_EXCEEDED — откат сделки целиком: транзакция
			// отменяет и позицию, и изменения счёта, ручной откат больше не нужен
			if (risk == RiskCheckResult.EQUITY_ZERO || risk == RiskCheckResult.LEVERAGE_EXCEEDED) {
				status.setRollbackOnly();
				return "❌ Сделка отклонена: превышен риск/плечо.";
			}
			if (risk == RiskCheckResult.MARGIN_CALL) {
				liquidate(userId, user);
				marginCalled[0] = true;
			}

			users.save(userId, user);
			String tradeId = UUID.randomUUID().toString();
			trades.save(tradeId, new TradeRecord(tradeId, userId, upperTicker, buy ? TradeSide.BUY : TradeSide.SELL, qty,
					price, fee, Instant.now()));
			String cur = formatter.currencySymbol(share.getCurrency());
			return (buy ? "🟢 Куплено " : "🔴 Продано ") + qty + " " + upperTicker + " по " + formatter.format(price)
					+ " " + cur + ". Комиссия " + formatter.format(fee) + " " + cur;
		});

		// Discord дёргаем уже после коммита, а не внутри транзакции
		if (marginCalled[0]) {
			sendDm(userId, "🚨 Margin call / Ликвидация! Ваши позиции принудительно закрыты.");
		}
		return result;
	}

	// ── Portfolio queries ────────────────────────────────────────────────────

	public String portfolio(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		List<Position> ps = userPositions(userId);
		if (ps.isEmpty()) return "Портфель пуст.";

		StringBuilder sb = new StringBuilder("Портфель:\n");
		BigDecimal totalPnl = ZERO;
		Map<String, BigDecimal> prices = priceService.loadPrices(
				ps.stream().map(Position::getInstrumentId).collect(Collectors.toSet()));
		for (Position p : ps) {
			BigDecimal price = prices.getOrDefault(p.getInstrumentId(), ZERO);
			BigDecimal avgPrice = p.getAvgPrice();
			BigDecimal pnl = price.subtract(avgPrice)
					.multiply(BigDecimal.valueOf(p.getQuantity()))
					.setScale(2, RoundingMode.HALF_UP);
			BigDecimal pnlPct = avgPrice.compareTo(ZERO) > 0
					? price.subtract(avgPrice).divide(avgPrice, SCALE, RoundingMode.HALF_UP)
							.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
					: ZERO;
			totalPnl = totalPnl.add(pnl);
			String pnlSign = pnl.compareTo(ZERO) >= 0 ? "+" : "";
			String pnlPctSign = pnlPct.compareTo(ZERO) >= 0 ? "+" : "";
			sb.append(p.getTicker()).append(": ").append(p.getQuantity())
					.append(" шт, ср. ").append(formatter.format(avgPrice))
					.append(" ₽, текущ. ").append(price.compareTo(ZERO) > 0 ? formatter.format(price) : "N/A")
					.append(" ₽, P&L: ").append(pnlSign).append(formatter.format(pnl))
					.append(" ₽ (").append(pnlPctSign).append(pnlPct.toPlainString()).append("%)\n");
		}
		String totalSign = totalPnl.compareTo(ZERO) >= 0 ? "+" : "";
		sb.append("Итого P&L акции: ").append(totalSign).append(formatter.format(totalPnl)).append(" ₽");

		String ccyPortfolio = currencyService.currencyPortfolio(userId);
		if (ccyPortfolio != null && !ccyPortfolio.equals("Валютных позиций нет.")) {
			sb.append("\n\n").append(ccyPortfolio);
		}
		return sb.toString();
	}

	public String balance(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";

		BigDecimal eq = equity(userId, user);
		BigDecimal gross = grossPositionValue(userId);
		BigDecimal lev = eq.compareTo(ZERO) <= 0 ? ZERO : gross.divide(eq, SCALE, RoundingMode.HALF_UP);
		BigDecimal roi = safeRoi(eq, startBalance).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
		String roiSign = roi.compareTo(ZERO) >= 0 ? "+" : "";

		StringBuilder result = new StringBuilder();
		result.append("💰 Рублёвый счёт: ").append(formatter.format(user.getCash())).append(" ₽\n");
		String ccyLine = currencyService.currencyBalanceLine(userId);
		if (ccyLine != null && !ccyLine.isBlank()) result.append(ccyLine).append("\n");
		result.append("📈 Стоимость акций: ").append(formatter.format(gross)).append(" ₽\n")
				.append("💳 Заём: ").append(formatter.format(user.getBorrowed())).append(" ₽\n")
				.append("📊 Equity (итого): ").append(formatter.format(eq)).append(" ₽\n")
				.append("📉 ROI от старта: ").append(roiSign).append(roi.toPlainString()).append("%\n")
				.append("⚖️ Плечо: x").append(lev.setScale(2, RoundingMode.HALF_UP).toPlainString())
				.append(" ").append(formatter.leverageStatus(lev));
		return result.toString();
	}

	public String margin(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";

		BigDecimal eq = equity(userId, user);
		BigDecimal borrowed = user.getBorrowed();
		if (borrowed.compareTo(ZERO) <= 0) return "Маржи нет. Заём = 0.";

		BigDecimal level = eq.divide(borrowed, SCALE, RoundingMode.HALF_UP);
		BigDecimal gross = grossPositionValue(userId);
		BigDecimal lev = eq.compareTo(ZERO) <= 0 ? ZERO : gross.divide(eq, SCALE, RoundingMode.HALF_UP);

		return """
				Margin level: %s
				Порог margin call: %s
				Плечо: x%s %s""".formatted(
				level.setScale(2, RoundingMode.HALF_UP).toPlainString(),
				riskManager.getMaintenanceMargin().toPlainString(),
				lev.setScale(2, RoundingMode.HALF_UP).toPlainString(),
				formatter.leverageStatus(lev));
	}

	public String price(String ticker) {
		Share s = shareByTicker.get(ticker.toUpperCase(Locale.ROOT));
		if (s == null) return "Тикер не найден.";
		BigDecimal p = priceService.loadPriceSafe(s.getUid());
		if (p.compareTo(ZERO) <= 0) return ticker.toUpperCase() + " — цена временно недоступна";
		return ticker.toUpperCase() + " = " + formatter.format(p) + " " + formatter.currencySymbol(s.getCurrency());
	}

	// ── Rating queries ───────────────────────────────────────────────────────

	public String top(String period) {
		List<SandboxUser> all = users.findAll();
		if (all.isEmpty()) return "Нет зарегистрированных пользователей.";
		all.sort((a, b) -> metric(b, period).compareTo(metric(a, period)));
		StringBuilder sb = new StringBuilder("🏆 Топ-5 (" + period + ")\n");
		int n = Math.min(5, all.size());
		for (int i = 0; i < n; i++) {
			SandboxUser u = all.get(i);
			BigDecimal pct = metric(u, period).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
			sb.append(i + 1).append(") ").append(u.getUserName()).append(" — ").append(pct.toPlainString()).append("%\n");
		}
		return sb.toString();
	}

	public String myRank(String userId) {
		SandboxUser target = users.findById(userId);
		if (target == null) return "Сначала выполните +регистрация";
		List<SandboxUser> all = users.findAll();
		all.sort((a, b) -> equity(b.getUserId(), b).compareTo(equity(a.getUserId(), a)));
		int rank = -1;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).getUserId().equals(userId)) { rank = i + 1; break; }
		}
		BigDecimal eq = equity(userId, target);
		BigDecimal roi = safeRoi(eq, startBalance).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
		String roiSign = roi.compareTo(ZERO) >= 0 ? "+" : "";
		return """
				📊 Ваш рейтинг: #%d из %d
				Equity: %s ₽
				ROI: %s%s%%""".formatted(rank, all.size(), formatter.format(eq), roiSign, roi.toPlainString());
	}

	public String stats(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		List<TradeRecord> userTrades = trades.findByUserId(userId);
		if (userTrades.isEmpty()) return "Статистика пуста — нет совершённых сделок.";

		Map<String, BigDecimal> avgCostByTicker = new HashMap<>();
		Map<String, Integer> qtyByTicker = new HashMap<>();
		List<TradeRecord> sorted = new ArrayList<>(userTrades);
		sorted.sort(Comparator.comparing(TradeRecord::getTimestamp));
		List<BigDecimal> realizedPnlList = new ArrayList<>();

		for (TradeRecord r : sorted) {
			BigDecimal rPrice = r.getPrice();
			BigDecimal rFee = r.getFee();
			if (r.getSide() == TradeSide.BUY) {
				BigDecimal prevAvg = avgCostByTicker.getOrDefault(r.getTicker(), ZERO);
				int prevQty = qtyByTicker.getOrDefault(r.getTicker(), 0);
				int newQty = prevQty + r.getQty();
				BigDecimal newAvg = prevAvg.multiply(BigDecimal.valueOf(prevQty))
						.add(rPrice.multiply(BigDecimal.valueOf(r.getQty())))
						.divide(BigDecimal.valueOf(newQty), SCALE, RoundingMode.HALF_UP);
				avgCostByTicker.put(r.getTicker(), newAvg);
				qtyByTicker.put(r.getTicker(), newQty);
			} else {
				BigDecimal avgCost = avgCostByTicker.getOrDefault(r.getTicker(), rPrice);
				BigDecimal pnl = rPrice.subtract(avgCost).multiply(BigDecimal.valueOf(r.getQty()))
						.subtract(rFee).setScale(2, RoundingMode.HALF_UP);
				realizedPnlList.add(pnl);
				int prevQty = qtyByTicker.getOrDefault(r.getTicker(), r.getQty());
				qtyByTicker.put(r.getTicker(), Math.max(0, prevQty - r.getQty()));
			}
		}
		if (realizedPnlList.isEmpty()) {
			return "Статистика: " + userTrades.size() + " сделок, закрытых позиций пока нет.";
		}
		long wins = realizedPnlList.stream().filter(p -> p.compareTo(ZERO) > 0).count();
		BigDecimal winRate = BigDecimal.valueOf(wins)
				.divide(BigDecimal.valueOf(realizedPnlList.size()), SCALE, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
		BigDecimal sum = realizedPnlList.stream().reduce(ZERO, BigDecimal::add);
		BigDecimal avgPnl = sum.divide(BigDecimal.valueOf(realizedPnlList.size()), SCALE, RoundingMode.HALF_UP)
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal bestPnl = realizedPnlList.stream().max(BigDecimal::compareTo).orElse(ZERO);
		BigDecimal worstPnl = realizedPnlList.stream().min(BigDecimal::compareTo).orElse(ZERO);

		return """
				📊 Статистика трейдинга:
				Всего сделок: %d
				Закрытых позиций: %d
				Win rate: %s%%
				Средний P&L: %s ₽
				Лучшая сделка: +%s ₽
				Худшая сделка: %s ₽""".formatted(
				userTrades.size(), realizedPnlList.size(),
				winRate.toPlainString(), formatter.format(avgPnl),
				formatter.format(bestPnl), formatter.format(worstPnl));
	}

	// ── Order commands ───────────────────────────────────────────────────────

	public String history(String userId, int page) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		List<TradeRecord> userTrades = trades.findByUserId(userId);
		if (userTrades.isEmpty()) return "История сделок пуста.";
		userTrades.sort(Comparator.comparing(TradeRecord::getTimestamp).reversed());
		int pageSize = 10;
		int totalPages = (userTrades.size() + pageSize - 1) / pageSize;
		if (page < 1) page = 1;
		if (page > totalPages) page = totalPages;
		int from = (page - 1) * pageSize;
		int to = Math.min(from + pageSize, userTrades.size());
		StringBuilder sb = new StringBuilder(
				"📋 История сделок (стр. " + page + "/" + totalPages + "):\n");
		for (int i = from; i < to; i++) {
			TradeRecord r = userTrades.get(i);
			String dt = ZonedDateTime.ofInstant(r.getTimestamp(), ZONE).format(DT_FMT);
			String side = r.getSide() == TradeSide.BUY ? "🟢 Покупка" : "🔴 Продажа";
			sb.append(dt).append(" | ").append(side).append(" ").append(r.getQty())
					.append(" ").append(r.getTicker())
					.append(" @ ").append(formatter.format(r.getPrice())).append(" ₽")
					.append(" (комиссия ").append(formatter.format(r.getFee())).append(" ₽)\n");
		}
		if (page < totalPages) {
			sb.append("➡️ +история ").append(page + 1).append(" — следующая страница");
		}
		return sb.toString().trim();
	}

	public String setStopLoss(String userId, String ticker, BigDecimal triggerPrice) {
		return setStopOrder(userId, ticker, StopOrderType.SL, triggerPrice);
	}

	public String setTakeProfit(String userId, String ticker, BigDecimal triggerPrice) {
		return setStopOrder(userId, ticker, StopOrderType.TP, triggerPrice);
	}

	private String setStopOrder(String userId, String ticker, StopOrderType type, BigDecimal triggerPrice) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser user = users.findById(userId);
			if (user == null) return "Сначала выполните +регистрация";
			ticker = ticker.toUpperCase(Locale.ROOT);
			if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
			Position pos = positions.findById(posKey(userId, ticker));
			if (pos == null || pos.getQuantity() <= 0) return "У вас нет открытой позиции по " + ticker;
			if (triggerPrice.compareTo(ZERO) <= 0) return "Цена триггера должна быть > 0";
			for (StopOrder so : stopOrders.findAll()) {
				if (userId.equals(so.getUserId()) && ticker.equals(so.getTicker()) && type == so.getType()) {
					stopOrders.delete(so.getId());
				}
			}
			String id = UUID.randomUUID().toString();
			stopOrders.save(id, new StopOrder(id, userId, ticker, type, triggerPrice, Instant.now()));
			String typeName = type == StopOrderType.SL ? "Стоп-лосс" : "Тейк-профит";
			return "✅ " + typeName + " на " + ticker + " установлен: " + formatter.format(triggerPrice) + " ₽";
		} finally {
			lock.unlock();
		}
	}

	public String placeLimitBuy(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
		return placeLimitOrder(userId, userName, ticker, qty, limitPrice, TradeSide.BUY);
	}

	public String placeLimitSell(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
		return placeLimitOrder(userId, userName, ticker, qty, limitPrice, TradeSide.SELL);
	}

	private String placeLimitOrder(String userId, String userName, String ticker, int qty, BigDecimal limitPrice, TradeSide side) {
		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		ticker = ticker.toUpperCase(Locale.ROOT);
		if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
		if (qty <= 0) return "Количество должно быть > 0";
		if (limitPrice.compareTo(ZERO) <= 0) return "❌ Цена ордера должна быть больше нуля.";
		long existingCount = limitOrders.countByUserAndTicker(userId, ticker);
		if (existingCount >= 10) return "❌ Максимум 10 лимитных ордеров на один тикер.";
		String id = UUID.randomUUID().toString();
		limitOrders.save(id, new LimitOrder(id, userId, userName, ticker, side, qty, limitPrice, Instant.now()));
		String sideLabel = side == TradeSide.BUY ? "покупку" : "продажу";
		return "✅ Лимитная заявка на " + sideLabel + " " + qty + " " + ticker
				+ " @ " + formatter.format(limitPrice) + " ₽ принята (ID: " + id.substring(0, 8) + "...)";
		} finally {
			lock.unlock();
		}
	}

	public String myOrders(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		List<LimitOrder> orders = limitOrders.findAll().stream()
				.filter(o -> userId.equals(o.getUserId()))
				.collect(Collectors.toList());
		if (orders.isEmpty()) return "Нет активных лимитных заявок.";
		orders.sort(Comparator.comparing(LimitOrder::getCreatedAt));
		StringBuilder sb = new StringBuilder("📋 Активные заявки:\n");
		for (LimitOrder o : orders) {
			String sideLabel = o.getSide() == TradeSide.BUY ? "Покупка" : "Продажа";
			String dt = ZonedDateTime.ofInstant(o.getCreatedAt(), ZONE).format(DT_FMT);
			sb.append("[").append(o.getId().substring(0, 8)).append("] ")
					.append(dt).append(" | ").append(sideLabel).append(" ")
					.append(o.getQty()).append(" ").append(o.getTicker())
					.append(" @ ").append(formatter.format(o.getLimitPrice())).append(" ₽\n");
		}
		return sb.toString().trim();
	}

	public String cancelOrder(String userId, String orderId) {
		String fullKey = null;
		LimitOrder found = null;
		for (LimitOrder o : limitOrders.findAll()) {
			if (userId.equals(o.getUserId())
					&& (o.getId().equals(orderId) || o.getId().startsWith(orderId))) {
				fullKey = o.getId();
				found = o;
				break;
			}
		}
		if (fullKey == null || found == null) return "Заявка не найдена или уже исполнена.";
		limitOrders.delete(fullKey);
		return "✅ Заявка [" + fullKey.substring(0, 8) + "] отменена: "
				+ found.getSide() + " " + found.getQty() + " " + found.getTicker()
				+ " @ " + formatter.format(found.getLimitPrice()) + " ₽";
	}

	public String setAlert(String userId, String ticker, BigDecimal targetPrice) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "Сначала выполните +регистрация";
		ticker = ticker.toUpperCase(Locale.ROOT);
		if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
		if (targetPrice.compareTo(ZERO) <= 0) return "Целевая цена должна быть > 0";
		Share share = shareByTicker.get(ticker);
		BigDecimal currentPrice = priceService.loadPriceSafe(share.getUid());
		boolean above = currentPrice.compareTo(ZERO) <= 0 || targetPrice.compareTo(currentPrice) > 0;
		String id = UUID.randomUUID().toString();
		priceAlerts.save(id, new PriceAlert(id, userId, ticker, targetPrice, above, Instant.now()));
		String direction = above ? "достигнет или превысит" : "упадёт до";
		return "🔔 Алерт установлен: уведомлю когда " + ticker + " " + direction + " " + formatter.format(targetPrice) + " ₽";
	}

	// ── DM sending ───────────────────────────────────────────────────────────

	public void sendDm(String userId, String message) {
		if (jda == null) return;
		try {
			jda.retrieveUserById(userId).queue(user -> {
				user.openPrivateChannel().queue(ch -> ch.sendMessage(message).queue());
			}, err -> log.warn("Cannot retrieve user {} for DM: {}", userId, err.getMessage()));
		} catch (Exception ex) {
			log.warn("Failed to send DM to {}: {}", userId, ex.getMessage());
		}
	}

	// ── Internal helpers ─────────────────────────────────────────────────────

	private void rebalanceDebt(SandboxUser user, String userId) {
		BigDecimal cash = user.getCash();
		BigDecimal borrowed = user.getBorrowed();
		if (cash.compareTo(ZERO) < 0) {
			BigDecimal loanAmount = cash.abs();
			user.setBorrowed(borrowed.add(loanAmount));
			user.setCash(ZERO);
			log.warn("Пользователь {} взял заём: {} ₽ (итого долг: {} ₽)",
					userId, loanAmount.setScale(2, RoundingMode.HALF_UP),
					user.getBorrowed().setScale(2, RoundingMode.HALF_UP));
		} else if (borrowed.compareTo(ZERO) > 0 && cash.compareTo(ZERO) > 0) {
			BigDecimal repay = cash.min(borrowed);
			user.setCash(cash.subtract(repay));
			user.setBorrowed(borrowed.subtract(repay));
		}
		users.save(userId, user);
	}

	private void liquidate(String userId, SandboxUser user) {
		List<Position> ps = userPositions(userId);
		BigDecimal cash = user.getCash();
		Map<String, BigDecimal> prices = priceService.loadPrices(
				ps.stream().map(Position::getInstrumentId).collect(Collectors.toSet()));
		for (Position p : ps) {
			BigDecimal avgPrice = p.getAvgPrice();
			BigDecimal price = prices.getOrDefault(p.getInstrumentId(), ZERO);
			if (price.compareTo(ZERO) <= 0) price = avgPrice;
			BigDecimal turnover = price.multiply(BigDecimal.valueOf(p.getQuantity()));
			// Комиссия округляется до копеек, как и при обычной сделке
			BigDecimal fee = turnover.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
			if (fee.compareTo(ONE) < 0) fee = ONE;
			cash = cash.add(turnover).subtract(fee);
			positions.delete(posKey(userId, p.getTicker()));
			stopOrders.deleteByUserAndTicker(userId, p.getTicker());
		}
		user.setCash(cash);
		rebalanceDebt(user, userId);
		users.save(userId, user);
	}

	private void recordBaseline(SandboxUser u) {
		LocalDate now = LocalDate.now(ZONE);
		BigDecimal eq = equity(u.getUserId(), u);
		if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
			u.setDailyBaselineDate(now);
			u.setDailyBaselineEquity(eq);
		}
		// Сравниваем неделю и месяц вместе с годом, иначе та же неделя/месяц
		// следующего года не сбрасывают baseline
		if (u.getWeeklyBaselineDate() == null || !sameIsoWeek(u.getWeeklyBaselineDate(), now)) {
			u.setWeeklyBaselineDate(now);
			u.setWeeklyBaselineEquity(eq);
		}
		if (u.getMonthlyBaselineDate() == null ||
				!java.time.YearMonth.from(u.getMonthlyBaselineDate()).equals(java.time.YearMonth.from(now))) {
			u.setMonthlyBaselineDate(now);
			u.setMonthlyBaselineEquity(eq);
		}
		users.save(u.getUserId(), u);
	}

	private static boolean sameIsoWeek(LocalDate a, LocalDate b) {
		return a.get(WeekFields.ISO.weekOfWeekBasedYear()) == b.get(WeekFields.ISO.weekOfWeekBasedYear())
				&& a.get(WeekFields.ISO.weekBasedYear()) == b.get(WeekFields.ISO.weekBasedYear());
	}

	private BigDecimal metric(SandboxUser u, String period) {
		BigDecimal eq = equity(u.getUserId(), u);
		return switch (period.toLowerCase(Locale.ROOT)) {
			case "день"   -> safeRoi(eq, u.getDailyBaselineEquity());
			case "неделя" -> safeRoi(eq, u.getWeeklyBaselineEquity());
			case "месяц"  -> safeRoi(eq, u.getMonthlyBaselineEquity());
			default       -> safeRoi(eq, startBalance);
		};
	}

	private BigDecimal safeRoi(BigDecimal now, BigDecimal base) {
		if (base == null || base.compareTo(ZERO) <= 0) return ZERO;
		return now.subtract(base).divide(base, SCALE, RoundingMode.HALF_UP);
	}

	private List<Position> userPositions(String userId) {
		return positions.findByUserId(userId);
	}

	private BigDecimal grossPositionValue(String userId) {
		List<Position> ps = userPositions(userId);
		if (ps.isEmpty()) return ZERO;
		// Одна загрузка списком вместо запроса на каждую позицию
		Map<String, BigDecimal> prices = priceService.loadPrices(
				ps.stream().map(Position::getInstrumentId).collect(Collectors.toSet()));
		return ps.stream()
				.map(p -> prices.getOrDefault(p.getInstrumentId(), ZERO).multiply(BigDecimal.valueOf(p.getQuantity())))
				.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal equity(String userId, SandboxUser user) {
		return user.getCash()
				.add(grossPositionValue(userId))
				.subtract(user.getBorrowed());
	}

	private String posKey(String userId, String ticker) {
		return userId + "::" + ticker;
	}
}
