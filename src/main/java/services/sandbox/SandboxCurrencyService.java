package services.sandbox;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.SandboxUserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Сервис валютных операций в песочнице.
 * Создаётся как Spring bean в JdaConfig, чтобы разделять userLocks с SandboxTradingService.
 */
public class SandboxCurrencyService {

	private static final Logger log = LoggerFactory.getLogger(SandboxCurrencyService.class);
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private static final int SCALE = 6;

	private final SandboxUserRepository users;
	private final CbrRateService cbrRateService;
	private final ConcurrentHashMap<String, ReentrantLock> userLocks;

	private static final Map<String, String> SYMBOLS = Map.of(
			"USD", "$",
			"EUR", "€",
			"CNY", "¥",
			"GBP", "£",
			"CHF", "₣",
			"JPY", "¥",
			"HKD", "HK$"
	);

	public SandboxCurrencyService(SandboxUserRepository users,
								  CbrRateService cbrRateService,
								  ConcurrentHashMap<String, ReentrantLock> userLocks) {
		this.users = users;
		this.cbrRateService = cbrRateService;
		this.userLocks = userLocks;
	}

	// -----------------------------------------------------------------------
	// Buy currency for RUB
	// -----------------------------------------------------------------------

	/**
	 * Buy {@code rubAmount} RUB worth of the given currency.
	 *
	 * @param userId    Discord user id
	 * @param isoCode   ISO currency code (e.g. "USD")
	 * @param rubAmount amount of RUB to spend
	 * @return human-readable result message
	 */
	public String buyCurrency(String userId, String isoCode, BigDecimal rubAmount) {
		String code = isoCode.toUpperCase(Locale.ROOT);
		if (!CbrRateService.SUPPORTED_CURRENCIES.contains(code)) {
			return "Валюта " + code + " не поддерживается. Доступны: " +
					String.join(", ", CbrRateService.SUPPORTED_CURRENCIES);
		}
		if (rubAmount.compareTo(ZERO) <= 0) {
			return "Сумма должна быть > 0";
		}

		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser user = users.findById(userId);
			if (user == null) {
				return "Сначала выполните +регистрация";
			}

			Map<String, BigDecimal> rates = cbrRateService.fetchRates();
			BigDecimal rate = rates.get(code);
			if (rate == null || rate.compareTo(ZERO) <= 0) {
				return "Не удалось получить курс " + code + " от ЦБ РФ. Попробуйте позже.";
			}

			BigDecimal cash = BigDecimal.valueOf(user.getCash());
			if (cash.compareTo(rubAmount) < 0) {
				return "Недостаточно рублей. Доступно: " + fmt(cash) + " ₽";
			}

			BigDecimal currencyAmount = rubAmount.divide(rate, SCALE, RoundingMode.HALF_UP);

			// Update cash
			user.setCash(cash.subtract(rubAmount).doubleValue());

			// Update holdings
			Map<String, Double> holdings = user.getCurrencyHoldings();
			if (holdings == null) {
				holdings = new HashMap<>();
			}
			double prev = holdings.getOrDefault(code, 0.0);
			holdings.put(code, prev + currencyAmount.doubleValue());
			user.setCurrencyHoldings(holdings);

			users.save(userId, user);

			String sym = SYMBOLS.getOrDefault(code, code);
			return "🟢 Куплено " + fmtCcy(currencyAmount) + " " + code + " (" + sym + ")"
					+ " за " + fmt(rubAmount) + " ₽"
					+ " (курс " + fmt(rate) + " ₽/" + code + ")";
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------
	// Sell currency for RUB
	// -----------------------------------------------------------------------

	/**
	 * Sell {@code currencyAmount} units of the given currency for RUB.
	 *
	 * @param userId         Discord user id
	 * @param isoCode        ISO currency code
	 * @param currencyAmount amount of currency to sell
	 * @return human-readable result message
	 */
	public String sellCurrency(String userId, String isoCode, BigDecimal currencyAmount) {
		String code = isoCode.toUpperCase(Locale.ROOT);
		if (!CbrRateService.SUPPORTED_CURRENCIES.contains(code)) {
			return "Валюта " + code + " не поддерживается. Доступны: " +
					String.join(", ", CbrRateService.SUPPORTED_CURRENCIES);
		}
		if (currencyAmount.compareTo(ZERO) <= 0) {
			return "Количество должно быть > 0";
		}

		ReentrantLock lock = lockFor(userId);
		lock.lock();
		try {
			SandboxUser user = users.findById(userId);
			if (user == null) {
				return "Сначала выполните +регистрация";
			}

			Map<String, Double> holdings = user.getCurrencyHoldings();
			if (holdings == null) {
				holdings = new HashMap<>();
			}
			double held = holdings.getOrDefault(code, 0.0);
			BigDecimal heldBD = BigDecimal.valueOf(held);

			if (heldBD.compareTo(currencyAmount) < 0) {
				return "Недостаточно " + code + ". В наличии: " + fmtCcy(heldBD);
			}

			Map<String, BigDecimal> rates = cbrRateService.fetchRates();
			BigDecimal rate = rates.get(code);
			if (rate == null || rate.compareTo(ZERO) <= 0) {
				return "Не удалось получить курс " + code + " от ЦБ РФ. Попробуйте позже.";
			}

			BigDecimal rubReceived = currencyAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

			// Update holdings
			BigDecimal newHeld = heldBD.subtract(currencyAmount);
			if (newHeld.compareTo(ZERO) <= 0) {
				holdings.remove(code);
			} else {
				holdings.put(code, newHeld.doubleValue());
			}
			user.setCurrencyHoldings(holdings);

			// Update cash
			BigDecimal cash = BigDecimal.valueOf(user.getCash());
			user.setCash(cash.add(rubReceived).doubleValue());

			users.save(userId, user);

			String sym = SYMBOLS.getOrDefault(code, code);
			return "🔴 Продано " + fmtCcy(currencyAmount) + " " + code + " (" + sym + ")"
					+ " → " + fmt(rubReceived) + " ₽"
					+ " (курс " + fmt(rate) + " ₽/" + code + ")";
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------
	// Currency portfolio summary
	// -----------------------------------------------------------------------

	/**
	 * Returns a formatted summary of the user's currency holdings with
	 * current RUB value and P&L estimation.
	 *
	 * @param userId Discord user id
	 * @return formatted string
	 */
	public String currencyPortfolio(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) {
			return "Сначала выполните +регистрация";
		}

		Map<String, Double> holdings = user.getCurrencyHoldings();
		if (holdings == null || holdings.isEmpty()) {
			return "Валютных позиций нет.";
		}

		Map<String, BigDecimal> rates = cbrRateService.fetchRates();

		StringBuilder sb = new StringBuilder("Валютные позиции:\n");
		BigDecimal totalRubValue = ZERO;

		for (Map.Entry<String, Double> entry : holdings.entrySet()) {
			String code = entry.getKey();
			BigDecimal amount = BigDecimal.valueOf(entry.getValue());
			if (amount.compareTo(ZERO) <= 0) continue;

			BigDecimal rate = rates.getOrDefault(code, ZERO);
			BigDecimal rubValue = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
			totalRubValue = totalRubValue.add(rubValue);

			String sym = SYMBOLS.getOrDefault(code, code);
			sb.append(code).append(" (").append(sym).append("): ")
					.append(fmtCcy(amount))
					.append(" (~").append(fmt(rubValue)).append(" ₽");
			if (rate.compareTo(ZERO) > 0) {
				sb.append(", курс ").append(fmt(rate)).append(" ₽/").append(code);
			}
			sb.append(")\n");
		}

		sb.append("Итого валюта: ~").append(fmt(totalRubValue)).append(" ₽");
		return sb.toString();
	}

	// -----------------------------------------------------------------------
	// Balance line for +баланс
	// -----------------------------------------------------------------------

	/**
	 * Returns a short one-line summary of currency holdings for the balance display.
	 *
	 * @param userId Discord user id
	 * @return formatted line, or empty string if no holdings
	 */
	public String currencyBalanceLine(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return "";

		Map<String, Double> holdings = user.getCurrencyHoldings();
		if (holdings == null || holdings.isEmpty()) return "";

		Map<String, BigDecimal> rates = cbrRateService.fetchRates();

		StringBuilder sb = new StringBuilder("💱 Валюта: ");
		boolean first = true;
		BigDecimal totalRub = ZERO;

		for (Map.Entry<String, Double> entry : holdings.entrySet()) {
			String code = entry.getKey();
			BigDecimal amount = BigDecimal.valueOf(entry.getValue());
			if (amount.compareTo(ZERO) <= 0) continue;

			BigDecimal rate = rates.getOrDefault(code, ZERO);
			BigDecimal rubValue = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
			totalRub = totalRub.add(rubValue);

			String sym = SYMBOLS.getOrDefault(code, code);
			if (!first) sb.append(" | ");
			sb.append(code).append(" ").append(fmtCcy(amount))
					.append(" (").append(sym).append(", ~").append(fmt(rubValue)).append(" ₽)");
			first = false;
		}

		if (first) return ""; // all amounts were zero
		sb.append(" | Итого ~").append(fmt(totalRub)).append(" ₽");
		return sb.toString();
	}

	/**
	 * Returns the total RUB value of all currency holdings for equity calculation.
	 */
	public BigDecimal totalCurrencyValueInRub(String userId) {
		SandboxUser user = users.findById(userId);
		if (user == null) return ZERO;

		Map<String, Double> holdings = user.getCurrencyHoldings();
		if (holdings == null || holdings.isEmpty()) return ZERO;

		Map<String, BigDecimal> rates = cbrRateService.fetchRates();
		BigDecimal total = ZERO;
		for (Map.Entry<String, Double> entry : holdings.entrySet()) {
			BigDecimal amount = BigDecimal.valueOf(entry.getValue());
			BigDecimal rate = rates.getOrDefault(entry.getKey(), ZERO);
			total = total.add(amount.multiply(rate));
		}
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private ReentrantLock lockFor(String userId) {
		return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
	}

	private static String fmt(BigDecimal v) {
		return String.format(Locale.ROOT, "%,.2f", v);
	}

	private static String fmtCcy(BigDecimal v) {
		// Show up to 4 decimal places for currency amounts, strip trailing zeros
		return v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}
}
