package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fetches daily exchange rates from the Central Bank of Russia (CBR) XML API.
 *
 * <p>Endpoint: {@code https://www.cbr.ru/scripts/XML_daily.asp}
 *
 * <p>Returns rates as RUB-per-1-unit-of-foreign-currency (or per-nominal).
 */
@Service
public class CbrRateService {

	private static final Logger log = LoggerFactory.getLogger(CbrRateService.class);

	static final String CBR_URL = "https://www.cbr.ru/scripts/XML_daily.asp";

	/** Supported ISO currency codes (upper-case). */
	public static final Set<String> SUPPORTED_CURRENCIES =
			Set.of("USD", "EUR", "CNY", "GBP", "CHF", "JPY", "HKD");

	private static final int SCALE = 6;

	/** Last successfully fetched rates; used as fallback when CBR is unavailable. */
	/** ЦБ публикует курсы раз в день — часового кэша достаточно. */
	static final long CACHE_TTL_MS = 60 * 60 * 1000L;
	private static final int TIMEOUT_MS = 5_000;
	private volatile Map<String, BigDecimal> cachedRates = new HashMap<>();
	private volatile long cachedAt = 0;

	/** Returns true when the last fetchRates() call failed and the cache is being served. */
	private volatile boolean stale = false;

	public boolean isStale() {
		return stale;
	}

	/**
	 * Fetches current CBR rates and returns a map of ISO-code -> RUB rate per 1 unit.
	 * On error, returns the last cached rates (if any), or an empty map.
	 *
	 * @return map from ISO code to RUB price for 1 unit
	 */
	public Map<String, BigDecimal> fetchRates() {
		// Раньше каждый +баланс/+портфель ходил на cbr.ru; теперь курсы нужны и в каждой
		// сделке и оценке портфеля — без кэша это десятки запросов на один рейтинг
		if (!cachedRates.isEmpty() && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
			return new HashMap<>(cachedRates);
		}
		Map<String, BigDecimal> result = new HashMap<>();
		try {
			URL url = URI.create(CBR_URL).toURL();
			// Без таймаутов зависший cbr.ru вешал поток вместе с захваченной блокировкой пользователя
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(TIMEOUT_MS);
			connection.setReadTimeout(TIMEOUT_MS);
			try (InputStream is = connection.getInputStream()) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				// Disable XXE
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(is);
				NodeList valutes = doc.getElementsByTagName("Valute");
				for (int i = 0; i < valutes.getLength(); i++) {
					Element el = (Element) valutes.item(i);
					String charCode = text(el, "CharCode").trim().toUpperCase();
					if (!SUPPORTED_CURRENCIES.contains(charCode)) continue;
					int nominal = Integer.parseInt(text(el, "Nominal").trim());
					String valueStr = text(el, "Value").trim().replace(',', '.');
					BigDecimal value = new BigDecimal(valueStr);
					// rate per 1 unit = value / nominal
					BigDecimal ratePerUnit = value.divide(
							BigDecimal.valueOf(nominal), SCALE, RoundingMode.HALF_UP);
					result.put(charCode, ratePerUnit);
				}
			}
			cachedRates = result;
			cachedAt = System.currentTimeMillis();
			stale = false;
		} catch (Exception e) {
			log.warn("Failed to fetch CBR exchange rates: {}. Returning cached rates (stale={})",
					e.getMessage(), !cachedRates.isEmpty());
			stale = true;
			if (!cachedRates.isEmpty()) {
				return new HashMap<>(cachedRates);
			}
		}
		return result;
	}

	private static String text(Element parent, String tag) {
		NodeList nl = parent.getElementsByTagName(tag);
		if (nl.getLength() == 0) return "";
		return nl.item(0).getTextContent();
	}
}
