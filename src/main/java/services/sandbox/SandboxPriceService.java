package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import services.tbank.TInvestApi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Котировки инструментов с короткоживущим кэшем.
 * <p>
 * Одна команда пользователя пересчитывает портфель несколько раз (equity,
 * grossPositionValue, проверка риска), а планировщик заявок обходит заявки
 * раз в минуту — раньше каждый такой шаг делал отдельный сетевой вызов
 * к API T-Банка на один инструмент. Кэш на {@value #TTL_MILLIS} мс и
 * загрузка списком убирают лавину запросов и риск упереться в лимиты.
 */
@Service
public class SandboxPriceService {

	private static final Logger log = LoggerFactory.getLogger(SandboxPriceService.class);

	/** Время жизни котировки в кэше. Меньше периода планировщика заявок (60 с). */
	static final long TTL_MILLIS = 5_000;

	private final TInvestApi api;
	private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();

	public SandboxPriceService(TInvestApi api) {
		this.api = api;
	}

	private record CachedPrice(BigDecimal price, long expiresAt) {
		boolean isFresh(long now) {
			return expiresAt > now;
		}
	}

	/**
	 * Возвращает цену инструмента. Бросает исключение, если API недоступен —
	 * вызывающий код (исполнение сделки) обязан это заметить.
	 */
	public BigDecimal loadPrice(String instrumentId) {
		BigDecimal cached = fromCache(instrumentId);
		if (cached != null) return cached;

		List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
		if (prices == null || prices.isEmpty()) return BigDecimal.ZERO;
		BigDecimal price = quotationToBigDecimal(prices.getFirst().getPrice());
		put(instrumentId, price);
		return price;
	}

	/** Как {@link #loadPrice}, но при ошибке возвращает 0 вместо исключения. */
	public BigDecimal loadPriceSafe(String instrumentId) {
		try {
			return loadPrice(instrumentId);
		} catch (Exception e) {
			log.warn("loadPriceSafe failed for {}: {}", instrumentId, e.getMessage());
			return BigDecimal.ZERO;
		}
	}

	/**
	 * Загружает цены сразу для нескольких инструментов: свежее берётся из кэша,
	 * недостающее запрашивается одним вызовом API.
	 *
	 * @return карта instrumentId → цена; инструменты без котировки в неё не попадают
	 */
	public Map<String, BigDecimal> loadPrices(Collection<String> instrumentIds) {
		Map<String, BigDecimal> result = new HashMap<>();
		if (instrumentIds == null || instrumentIds.isEmpty()) return result;

		long now = System.currentTimeMillis();
		List<String> missing = new ArrayList<>();
		for (String id : instrumentIds) {
			CachedPrice cached = cache.get(id);
			if (cached != null && cached.isFresh(now)) result.put(id, cached.price());
			else if (!missing.contains(id)) missing.add(id);
		}
		if (missing.isEmpty()) return result;

		try {
			List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(missing);
			if (prices != null) {
				for (LastPrice lp : prices) {
					BigDecimal price = quotationToBigDecimal(lp.getPrice());
					put(lp.getInstrumentUid(), price);
					result.put(lp.getInstrumentUid(), price);
				}
			}
		} catch (Exception e) {
			log.warn("loadPrices failed for {} инструментов: {}", missing.size(), e.getMessage());
		}
		return result;
	}

	private BigDecimal fromCache(String instrumentId) {
		CachedPrice cached = cache.get(instrumentId);
		return cached != null && cached.isFresh(System.currentTimeMillis()) ? cached.price() : null;
	}

	private void put(String instrumentId, BigDecimal price) {
		cache.put(instrumentId, new CachedPrice(price, System.currentTimeMillis() + TTL_MILLIS));
	}

	private BigDecimal quotationToBigDecimal(Quotation q) {
		return BigDecimal.valueOf(q.getUnits()).add(BigDecimal.valueOf(q.getNano(), 9));
	}
}
