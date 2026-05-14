package services.sandbox.model;

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
	private double cash;
	private double borrowed;
	private double totalFees;
	private LocalDate dailyBaselineDate;
	private double dailyBaselineEquity;
	private LocalDate weeklyBaselineDate;
	private double weeklyBaselineEquity;
	private LocalDate monthlyBaselineDate;
	private double monthlyBaselineEquity;

	private LocalDate lastReplenishDate;

	/** Currency holdings: ISO code (e.g. "USD") -> amount held */
	private Map<String, Double> currencyHoldings = new HashMap<>();

	public SandboxUser() {
	}

	public SandboxUser(String userId, String userName, double cash) {
		this.userId = userId;
		this.userName = userName;
		this.cash = cash;
		this.borrowed = 0.0;
		this.totalFees = 0.0;
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

	public double getCash() {
		return this.cash;
	}

	public void setCash(double cash) {
		this.cash = cash;
	}

	public double getBorrowed() {
		return this.borrowed;
	}

	public void setBorrowed(double borrowed) {
		this.borrowed = borrowed;
	}

	public double getTotalFees() {
		return this.totalFees;
	}

	public void setTotalFees(double totalFees) {
		this.totalFees = totalFees;
	}

	public LocalDate getDailyBaselineDate() {
		return this.dailyBaselineDate;
	}

	public void setDailyBaselineDate(LocalDate dailyBaselineDate) {
		this.dailyBaselineDate = dailyBaselineDate;
	}

	public double getDailyBaselineEquity() {
		return this.dailyBaselineEquity;
	}

	public void setDailyBaselineEquity(double dailyBaselineEquity) {
		this.dailyBaselineEquity = dailyBaselineEquity;
	}

	public LocalDate getWeeklyBaselineDate() {
		return this.weeklyBaselineDate;
	}

	public void setWeeklyBaselineDate(LocalDate weeklyBaselineDate) {
		this.weeklyBaselineDate = weeklyBaselineDate;
	}

	public double getWeeklyBaselineEquity() {
		return this.weeklyBaselineEquity;
	}

	public void setWeeklyBaselineEquity(double weeklyBaselineEquity) {
		this.weeklyBaselineEquity = weeklyBaselineEquity;
	}

	public LocalDate getMonthlyBaselineDate() {
		return this.monthlyBaselineDate;
	}

	public void setMonthlyBaselineDate(LocalDate monthlyBaselineDate) {
		this.monthlyBaselineDate = monthlyBaselineDate;
	}

	public double getMonthlyBaselineEquity() {
		return this.monthlyBaselineEquity;
	}

	public void setMonthlyBaselineEquity(double monthlyBaselineEquity) {
		this.monthlyBaselineEquity = monthlyBaselineEquity;
	}

	public LocalDate getLastReplenishDate() {
		return this.lastReplenishDate;
	}

	public void setLastReplenishDate(LocalDate lastReplenishDate) {
		this.lastReplenishDate = lastReplenishDate;
	}

	public Map<String, Double> getCurrencyHoldings() {
		if (this.currencyHoldings == null) {
			this.currencyHoldings = new HashMap<>();
		}
		return this.currencyHoldings;
	}

	public void setCurrencyHoldings(Map<String, Double> currencyHoldings) {
		this.currencyHoldings = currencyHoldings;
	}

	public int getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}
}
