package services.sandbox;

import com.discord.stonks.config.SandboxProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SandboxRiskManager {

	private static final int SCALE = 8;
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private static final BigDecimal MARGIN_CALL_THRESHOLD = new BigDecimal("0.2");

	private final BigDecimal maxLeverage;
	private final BigDecimal maintenanceMargin;

	public SandboxRiskManager(SandboxProperties props) {
		this.maxLeverage = props.maxLeverage();
		this.maintenanceMargin = props.maintenanceMargin();
	}

	/**
	 * Evaluates current risk state given pre-computed portfolio values.
	 * Pure calculation — no side effects.
	 */
	public RiskCheckResult evaluate(BigDecimal equity, BigDecimal grossValue, BigDecimal borrowed) {
		if (equity.compareTo(ZERO) <= 0) return RiskCheckResult.EQUITY_ZERO;

		BigDecimal leverage = grossValue.compareTo(ZERO) <= 0
				? ZERO
				: grossValue.divide(equity, SCALE, RoundingMode.HALF_UP);
		if (leverage.compareTo(maxLeverage) > 0) return RiskCheckResult.LEVERAGE_EXCEEDED;

		if (borrowed.compareTo(ZERO) > 0) {
			BigDecimal marginLevel = equity.divide(borrowed, SCALE, RoundingMode.HALF_UP);
			if (marginLevel.compareTo(MARGIN_CALL_THRESHOLD) < 0) return RiskCheckResult.MARGIN_CALL;
		}

		return RiskCheckResult.OK;
	}

	public BigDecimal getMaintenanceMargin() {
		return maintenanceMargin;
	}
}
