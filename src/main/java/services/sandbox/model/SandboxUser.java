/*
 * Decompiled with CFR 0.152.
 */
package services.sandbox.model;

import java.io.Serializable;
import java.time.LocalDate;

public class SandboxUser
implements Serializable {
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

    public SandboxUser() {
    }

    public SandboxUser(String userId, String userName, double cash) {
        this.userId = userId;
        this.userName = userName;
        this.cash = cash;
        this.borrowed = 0.0;
        this.totalFees = 0.0;
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
}

