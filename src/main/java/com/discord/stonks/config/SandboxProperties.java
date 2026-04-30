package com.discord.stonks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties("sandbox")
public record SandboxProperties(
		BigDecimal startBalance,
		BigDecimal commissionRate,
		BigDecimal maxLeverage,
		BigDecimal maintenanceMargin,
		List<String> allowedTickers
) {
	public SandboxProperties {
		if (startBalance == null)        startBalance       = new BigDecimal("1000000.00");
		if (commissionRate == null)      commissionRate     = new BigDecimal("0.001");
		if (maxLeverage == null)         maxLeverage        = new BigDecimal("3.0");
		if (maintenanceMargin == null)   maintenanceMargin  = new BigDecimal("0.25");
		if (allowedTickers == null)      allowedTickers     = defaultTickers();
	}

	private static List<String> defaultTickers() {
		return List.of(
				// === MOEX 1st echelon (blue-chips) ===
				"SBER", "SBERP", "GAZP", "LKOH", "ROSN", "NVTK", "YDEX", "TATN", "TATNP",
				"PLZL", "MGNT", "MTSS", "SNGS", "SNGSP", "ALRS", "CHMF", "NLMK", "VTBR",
				"GMKN", "RUAL", "MAGN", "TCSG", "FIVE", "OZON", "VKCO", "AFLT", "HYDR",
				"IRAO", "RTKM", "RTKMP", "FEES", "PHOR", "AKRN", "GLTR",
				// === MOEX 2nd echelon ===
				"BSPB", "SVCB", "MVID", "FIXP", "PIKK", "POSI", "ASTR", "HEAD", "SOFL",
				"TRMK", "RASP", "TGKB", "TGKD", "MSNG", "UPRO", "OGKB", "LSNG", "LSNGP",
				"SGZH", "AGRO", "SPBE", "CIAN", "GEMC", "MDMG", "RNFT", "BANEP", "BANE",
				"KMAZ", "UWGN", "NKNC", "NKNCP", "KZOS", "KZOSP", "SELG", "PMSB", "PMSBP",
				"MFGP", "GCHE", "GRNT", "NSVZ", "ZVEZ", "DIOD",
				// === MOEX 3rd echelon ===
				"LENT", "KART", "KLSB", "IRKT", "DSKI", "RKKE", "ELMT", "BRZL",
				"TGKN", "MISB", "MISBP", "MGTSP", "CHGZ", "KUBE", "AMEZ",
				// === SPB Exchange — foreign stocks (USD) ===
				"AAPL", "MSFT", "AMZN", "GOOGL", "GOOG", "TSLA", "META", "NVDA",
				"BRK.B", "JPM", "JNJ", "V", "PG", "UNH", "HD", "MA",
				"DIS", "NFLX", "PYPL", "INTC", "AMD", "CRM", "ORCL", "IBM",
				"BA", "GE", "XOM", "CVX", "KO", "PEP", "MCD", "WMT",
				"BABA", "JD", "NKE", "SBUX", "UBER", "LYFT", "SNAP", "TWTR",
				"SPOT", "SQ", "ROKU", "ZM", "SHOP", "ABNB", "COIN", "HOOD",
				"F", "GM", "T", "VZ", "CSCO", "QCOM", "TXN", "MU",
				"LRCX", "KLAC", "AMAT", "ASML", "TSM", "AVGO", "MRVL"
		);
	}
}
