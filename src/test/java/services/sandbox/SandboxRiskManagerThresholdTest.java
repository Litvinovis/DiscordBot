package services.sandbox;

import com.discord.stonks.config.SandboxProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Порог margin call должен браться из конфигурации.
 * Раньше в RiskManager был зашит 0.2, а настраиваемые 0.25 только печатались в +маржа.
 */
class SandboxRiskManagerThresholdTest {

	private SandboxRiskManager manager(String maintenanceMargin) {
		SandboxProperties props = new SandboxProperties(
				new BigDecimal("1000000"), new BigDecimal("0.001"),
				new BigDecimal("3.0"), new BigDecimal(maintenanceMargin), List.of("SBER"));
		return new SandboxRiskManager(props);
	}

	@Test
	void marginCallUsesConfiguredThreshold() {
		SandboxRiskManager risk = manager("0.25");
		// equity/borrowed = 0.24 — ниже настроенных 0.25, но выше прежнего хардкода 0.2
		RiskCheckResult result = risk.evaluate(new BigDecimal("24"), new BigDecimal("50"), new BigDecimal("100"));
		assertEquals(RiskCheckResult.MARGIN_CALL, result);
	}

	@Test
	void aboveThresholdIsOk() {
		SandboxRiskManager risk = manager("0.25");
		RiskCheckResult result = risk.evaluate(new BigDecimal("30"), new BigDecimal("50"), new BigDecimal("100"));
		assertEquals(RiskCheckResult.OK, result);
	}

	@Test
	void lowerConfiguredThresholdDelaysMarginCall() {
		SandboxRiskManager risk = manager("0.15");
		RiskCheckResult result = risk.evaluate(new BigDecimal("24"), new BigDecimal("50"), new BigDecimal("100"));
		assertEquals(RiskCheckResult.OK, result);
	}
}
