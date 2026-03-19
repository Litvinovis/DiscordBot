package services.sandbox.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class SandboxUser implements Serializable {
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

    public SandboxUser() {
    }

    public SandboxUser(String userId, String userName, BigDecimal cash) {
        this.userId = userId;
        this.userName = userName;
        this.cash = cash;
        this.borrowed = BigDecimal.ZERO;
        this.totalFees = BigDecimal.ZERO;
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
}
