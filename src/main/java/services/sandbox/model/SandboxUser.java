package services.sandbox.model;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Профиль участника торговой песочницы.
 *
 * <p>Хранит рублёвый баланс, заёмные средства, комиссии, базовые значения
 * equity для расчёта доходности за день/неделю/месяц, а также валютные позиции.
 * Хранится в PostgreSQL-таблице {@code sandbox_users}.
 */
public class SandboxUser implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int CURRENT_SCHEMA_VERSION = 2;

	private int schemaVersion = 0;

	private String userId;
	private String userName;
	private BigDecimal cash;
	private BigDecimal borrowed;
	private BigDecimal totalFees;
	private LocalDate dailyBaselineDate;
	private BigDecimal dailyBaselineEquity;
	private LocalDate weeklyBaselineDate;
	private BigDecimal weeklyBaselineEquity;
	private LocalDate monthlyBaselineDate;
	private BigDecimal monthlyBaselineEquity;

	private LocalDate lastReplenishDate;
	private boolean morningDigestEnabled = false;

	/** Currency holdings: ISO code (e.g. "USD") -> amount held */
	private Map<String, BigDecimal> currencyHoldings = new HashMap<>();

	public SandboxUser() {
	}

	public SandboxUser(String userId, String userName, BigDecimal cash) {
		this.userId = userId;
		this.userName = userName;
		this.cash = cash;
		this.borrowed = BigDecimal.ZERO;
		this.totalFees = BigDecimal.ZERO;
		this.currencyHoldings = new HashMap<>();
	}

	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return this.userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public BigDecimal getCash() {
		return this.cash;
	}

	public void setCash(BigDecimal cash) {
		this.cash = cash;
	}

	public BigDecimal getBorrowed() {
		return this.borrowed;
	}

	public void setBorrowed(BigDecimal borrowed) {
		this.borrowed = borrowed;
	}

	public BigDecimal getTotalFees() {
		return this.totalFees;
	}

	public void setTotalFees(BigDecimal totalFees) {
		this.totalFees = totalFees;
	}

	public LocalDate getDailyBaselineDate() {
		return this.dailyBaselineDate;
	}

	public void setDailyBaselineDate(LocalDate dailyBaselineDate) {
		this.dailyBaselineDate = dailyBaselineDate;
	}

	public BigDecimal getDailyBaselineEquity() {
		return this.dailyBaselineEquity;
	}

	public void setDailyBaselineEquity(BigDecimal dailyBaselineEquity) {
		this.dailyBaselineEquity = dailyBaselineEquity;
	}

	public LocalDate getWeeklyBaselineDate() {
		return this.weeklyBaselineDate;
	}

	public void setWeeklyBaselineDate(LocalDate weeklyBaselineDate) {
		this.weeklyBaselineDate = weeklyBaselineDate;
	}

	public BigDecimal getWeeklyBaselineEquity() {
		return this.weeklyBaselineEquity;
	}

	public void setWeeklyBaselineEquity(BigDecimal weeklyBaselineEquity) {
		this.weeklyBaselineEquity = weeklyBaselineEquity;
	}

	public LocalDate getMonthlyBaselineDate() {
		return this.monthlyBaselineDate;
	}

	public void setMonthlyBaselineDate(LocalDate monthlyBaselineDate) {
		this.monthlyBaselineDate = monthlyBaselineDate;
	}

	public BigDecimal getMonthlyBaselineEquity() {
		return this.monthlyBaselineEquity;
	}

	public void setMonthlyBaselineEquity(BigDecimal monthlyBaselineEquity) {
		this.monthlyBaselineEquity = monthlyBaselineEquity;
	}

	public LocalDate getLastReplenishDate() {
		return this.lastReplenishDate;
	}

	public void setLastReplenishDate(LocalDate lastReplenishDate) {
		this.lastReplenishDate = lastReplenishDate;
	}

	public boolean isMorningDigestEnabled() {
		return this.morningDigestEnabled;
	}

	public void setMorningDigestEnabled(boolean morningDigestEnabled) {
		this.morningDigestEnabled = morningDigestEnabled;
	}

	public Map<String, BigDecimal> getCurrencyHoldings() {
		if (this.currencyHoldings == null) {
			this.currencyHoldings = new HashMap<>();
		}
		return this.currencyHoldings;
	}

	public void setCurrencyHoldings(Map<String, BigDecimal> currencyHoldings) {
		this.currencyHoldings = currencyHoldings;
	}

	public int getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}
}
