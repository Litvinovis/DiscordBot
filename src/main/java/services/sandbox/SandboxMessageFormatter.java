package services.sandbox;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Component
public class SandboxMessageFormatter {

	private static final Locale RU = new Locale("ru", "RU");

	public String format(BigDecimal value) {
		if (value == null) return "0.00";
		NumberFormat nf = NumberFormat.getInstance(RU);
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
		nf.setGroupingUsed(true);
		return nf.format(value).replace(' ', ' ');
	}

	public String currencySymbol(String currency) {
		if (currency == null) return "₽";
		return switch (currency.toUpperCase(Locale.ROOT)) {
			case "USD" -> "$";
			case "EUR" -> "€";
			case "CNY" -> "¥";
			case "GBP" -> "£";
			default    -> "₽";
		};
	}

	public String leverageStatus(BigDecimal leverage) {
		if (leverage.compareTo(new BigDecimal("2.0")) < 0) return "✅ БЕЗОПАСНО";
		if (leverage.compareTo(new BigDecimal("4.0")) <= 0) return "⚠️ ВНИМАНИЕ";
		return "🚨 КРИТИЧНО (ликвидация скоро)";
	}
}
