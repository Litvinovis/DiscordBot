package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.StopOrderType;
import services.sandbox.model.TradeSide;
import services.sandbox.repository.LimitOrderRepository;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.PriceAlertRepository;
import services.sandbox.repository.SandboxUserRepository;
import services.sandbox.repository.StopOrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Checks and executes triggered stop orders, limit orders, and price alerts.
 * Called periodically by {@link SandboxOrderScheduler}.
 */
@Service
public class SandboxOrderProcessor {

	private static final Logger log = LoggerFactory.getLogger(SandboxOrderProcessor.class);

	private final SandboxTradingService tradingService;
	private final SandboxPriceService priceService;
	private final SandboxMessageFormatter formatter;
	private final StopOrderRepository stopOrders;
	private final LimitOrderRepository limitOrders;
	private final PriceAlertRepository priceAlerts;
	private final PositionRepository positions;
	private final SandboxUserRepository users;

	public SandboxOrderProcessor(SandboxTradingService tradingService,
								  SandboxPriceService priceService,
								  SandboxMessageFormatter formatter,
								  StopOrderRepository stopOrders,
								  LimitOrderRepository limitOrders,
								  PriceAlertRepository priceAlerts,
								  PositionRepository positions,
								  SandboxUserRepository users) {
		this.tradingService = tradingService;
		this.priceService = priceService;
		this.formatter = formatter;
		this.stopOrders = stopOrders;
		this.limitOrders = limitOrders;
		this.priceAlerts = priceAlerts;
		this.positions = positions;
		this.users = users;
	}

	/** Returns notifications for each triggered stop/take-profit order. */
	public List<Notification> checkStopOrders() {
		Map<String, Share> shareByTicker = tradingService.getShareByTicker();
		List<Notification> notifications = new ArrayList<>();
		List<String> toRemove = new ArrayList<>();

		List<StopOrder> allOrders = stopOrders.findAll();
		Map<String, BigDecimal> prices = loadPricesFor(allOrders.stream().map(StopOrder::getTicker).toList(), shareByTicker);

		for (StopOrder so : allOrders) {
			Share share = shareByTicker.get(so.getTicker());
			if (share == null) continue;
			BigDecimal price = prices.getOrDefault(share.getUid(), BigDecimal.ZERO);
			if (price.compareTo(BigDecimal.ZERO) <= 0) continue;

			BigDecimal triggerPrice = so.getTriggerPrice();
			boolean triggered = so.getType() == StopOrderType.SL
					? price.compareTo(triggerPrice) <= 0
					: price.compareTo(triggerPrice) >= 0;

			if (triggered) {
				ReentrantLock lock = tradingService.userLocks.computeIfAbsent(so.getUserId(), k -> new ReentrantLock(true));
				lock.lock();
				try {
					// Позицию и пользователя читаем под локом, чтобы не продать устаревшее количество
					Position pos = positions.findById(posKey(so.getUserId(), so.getTicker()));
					if (pos == null || pos.getQuantity() <= 0) { toRemove.add(so.getId()); continue; }
					SandboxUser user = users.findById(so.getUserId());
					if (user == null) { toRemove.add(so.getId()); continue; }

					String result = tradingService.trade(so.getUserId(), user.getUserName(), so.getTicker(), pos.getQuantity(), false);
					if (isTransientFailure(result)) {
						// Цена временно недоступна — оставляем ордер до следующего прохода
						log.warn("Стоп-ордер {} не исполнен (повторим): {}", so.getId(), result);
						continue;
					}
					String typeName = so.getType() == StopOrderType.SL ? "Стоп-лосс" : "Тейк-профит";
					notifications.add(new Notification(so.getUserId(),
							"⚡ " + typeName + " сработал! " + so.getTicker() + " @ " + formatter.format(price) + " ₽ → " + result));
					toRemove.add(so.getId());
				} finally {
					lock.unlock();
				}
			}
		}
		toRemove.forEach(stopOrders::delete);
		return notifications;
	}

	/** Returns notifications for each triggered limit order. */
	public List<Notification> checkLimitOrders() {
		Map<String, Share> shareByTicker = tradingService.getShareByTicker();
		List<Notification> notifications = new ArrayList<>();
		List<String> toRemove = new ArrayList<>();

		List<LimitOrder> allOrders = new ArrayList<>(limitOrders.findAll());
		allOrders.sort(Comparator.comparing(LimitOrder::getCreatedAt));
		Map<String, BigDecimal> prices = loadPricesFor(allOrders.stream().map(LimitOrder::getTicker).toList(), shareByTicker);

		for (LimitOrder lo : allOrders) {
			Share share = shareByTicker.get(lo.getTicker());
			if (share == null) continue;
			BigDecimal price = prices.getOrDefault(share.getUid(), BigDecimal.ZERO);
			if (price.compareTo(BigDecimal.ZERO) <= 0) continue;

			BigDecimal loLimitPrice = lo.getLimitPrice();
			boolean isBuy = lo.getSide() == TradeSide.BUY;
			boolean triggered = isBuy
					? price.compareTo(loLimitPrice) <= 0
					: price.compareTo(loLimitPrice) >= 0;

			if (triggered) {
				ReentrantLock lock = tradingService.userLocks.computeIfAbsent(lo.getUserId(), k -> new ReentrantLock(true));
				lock.lock();
				try {
					String result = tradingService.trade(lo.getUserId(), lo.getUserName(), lo.getTicker(), lo.getQty(), isBuy);
					if (isTransientFailure(result)) {
						// Цена временно недоступна — оставляем заявку до следующего прохода
						log.warn("Лимитная заявка {} не исполнена (повторим): {}", lo.getId(), result);
						continue;
					}
					String sideLabel = isBuy ? "покупка" : "продажа";
					notifications.add(new Notification(lo.getUserId(),
							"✅ Лимитная заявка исполнена: " + sideLabel + " " + lo.getQty() + " " + lo.getTicker()
									+ " @ " + formatter.format(price) + " ₽\n" + result));
					toRemove.add(lo.getId());
				} finally {
					lock.unlock();
				}
			}
		}
		toRemove.forEach(limitOrders::delete);
		return notifications;
	}

	/** Returns notifications for each triggered price alert. */
	public List<Notification> checkPriceAlerts() {
		Map<String, Share> shareByTicker = tradingService.getShareByTicker();
		List<Notification> notifications = new ArrayList<>();

		List<PriceAlert> allAlerts = priceAlerts.findAll();
		Map<String, BigDecimal> prices = loadPricesFor(allAlerts.stream().map(PriceAlert::getTicker).toList(), shareByTicker);

		for (PriceAlert alert : allAlerts) {
			Share share = shareByTicker.get(alert.getTicker());
			if (share == null) continue;
			BigDecimal price = prices.getOrDefault(share.getUid(), BigDecimal.ZERO);
			if (price.compareTo(BigDecimal.ZERO) <= 0) continue;

			BigDecimal alertTarget = alert.getTargetPrice();
			boolean triggered = alert.isAbove()
					? price.compareTo(alertTarget) >= 0
					: price.compareTo(alertTarget) <= 0;

			if (triggered) {
				// Delete immediately before continuing to prevent duplicate firing
				priceAlerts.delete(alert.getId());
				notifications.add(new Notification(alert.getUserId(),
						"🔔 Алерт! " + alert.getTicker() + " = " + formatter.format(price) + " ₽ (целевая: " + formatter.format(alertTarget) + " ₽)"));
			}
		}
		return notifications;
	}

	/** Загружает котировки для всех тикеров одним запросом к API. */
	private Map<String, BigDecimal> loadPricesFor(List<String> tickers, Map<String, Share> shareByTicker) {
		Set<String> uids = tickers.stream()
				.map(shareByTicker::get)
				.filter(Objects::nonNull)
				.map(Share::getUid)
				.collect(Collectors.toSet());
		return priceService.loadPrices(uids);
	}

	/** Временная ошибка (например, недоступна котировка) — ордер стоит повторить, а не удалять. */
	private static boolean isTransientFailure(String tradeResult) {
		return tradeResult != null && tradeResult.startsWith("⚠️");
	}

	private String posKey(String userId, String ticker) {
		return userId + "::" + ticker;
	}
}
