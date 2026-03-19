/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.cache.Cache$Entry
 *  org.apache.ignite.IgniteCache
 *  ru.tinkoff.piapi.contract.v1.LastPrice
 *  ru.tinkoff.piapi.contract.v1.Quotation
 *  ru.tinkoff.piapi.contract.v1.Share
 */
package services.sandbox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.cache.Cache;
import org.apache.ignite.IgniteCache;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.ignite.SandboxIgniteManager;
import services.sandbox.model.Position;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.TradeRecord;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class SandboxTradingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");
    private final TInvestApi api;
    private final IgniteCache<String, SandboxUser> users;
    private final IgniteCache<String, Position> positions;
    private final IgniteCache<String, TradeRecord> trades;
    private final Map<String, Share> shareByTicker;
    private final double startBalance = ConfigLoader.getSandboxStartBalance();
    private final double commissionRate = ConfigLoader.getSandboxCommissionRate();
    private final double maxLeverage = ConfigLoader.getSandboxMaxLeverage();
    private final double maintenanceMargin = ConfigLoader.getSandboxMaintenanceMargin();

    public SandboxTradingService(TInvestApi api) {
        this.api = api;
        SandboxIgniteManager manager = new SandboxIgniteManager();
        this.users = manager.usersCache();
        this.positions = manager.positionsCache();
        this.trades = manager.tradesCache();
        Set<String> allowed = ConfigLoader.getSandboxAllowedTickers().stream().map(String::toUpperCase).collect(Collectors.toSet());
        this.shareByTicker = api.getInstrumentsService().getAllSharesSync().stream().filter(s -> "rub".equalsIgnoreCase(s.getCurrency())).filter(s -> allowed.contains(s.getTicker().toUpperCase())).collect(Collectors.toMap(s -> s.getTicker().toUpperCase(), s -> s, (a, b) -> a));
    }

    public synchronized String register(String userId, String userName) {
        SandboxUser existing = (SandboxUser)this.users.get(userId);
        if (existing != null) {
            return "\u0412\u044b \u0443\u0436\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u044b \u0432 \u043f\u0435\u0441\u043e\u0447\u043d\u0438\u0446\u0435.";
        }
        SandboxUser user = new SandboxUser(userId, userName, this.startBalance);
        // Initialize baselines so top() has a valid starting point from day one
        this.recordBaseline(user);
        this.users.put(userId, user);
        return "\u2705 \u0420\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f \u0443\u0441\u043f\u0435\u0448\u043d\u0430. \u0421\u0442\u0430\u0440\u0442\u043e\u0432\u044b\u0439 \u0431\u0430\u043b\u0430\u043d\u0441: " + this.fmt(this.startBalance) + " \u20bd";
    }

    public synchronized String assets() {
        if (this.shareByTicker.isEmpty()) {
            return "\u0421\u043f\u0438\u0441\u043e\u043a \u0430\u043a\u0442\u0438\u0432\u043e\u0432 \u043f\u0443\u0441\u0442.";
        }
        return "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0435 \u0442\u0438\u043a\u0435\u0440\u044b: " + String.join((CharSequence)", ", new TreeSet<String>(this.shareByTicker.keySet()));
    }

    public synchronized String buy(String userId, String userName, String ticker, int qty) {
        return this.trade(userId, userName, ticker, qty, true);
    }

    public synchronized String sell(String userId, String userName, String ticker, int qty) {
        return this.trade(userId, userName, ticker, qty, false);
    }

    private String trade(String userId, String userName, String ticker, int qty, boolean buy) {
        Share share;
        if (qty <= 0) {
            return "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u0434\u043e\u043b\u0436\u043d\u043e \u0431\u044b\u0442\u044c > 0";
        }
        SandboxUser user = (SandboxUser)this.users.get(userId);
        if (user == null) {
            return "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 +\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f";
        }
        if (user.getUserName() == null || !user.getUserName().equals(userName)) {
            user.setUserName(userName);
        }
        if ((share = this.shareByTicker.get(ticker = ticker.toUpperCase(Locale.ROOT))) == null) {
            return "\u0422\u0438\u043a\u0435\u0440 \u043d\u0435 \u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d \u0432 \u043f\u0435\u0441\u043e\u0447\u043d\u0438\u0446\u0435.";
        }
        // Record baselines BEFORE the trade so we capture equity at the start of the period
        this.recordBaseline(user);
        double price = this.loadPrice(share.getUid());
        if (price <= 0.0) {
            return "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u0446\u0435\u043d\u0443 \u0434\u043b\u044f " + ticker;
        }
        String pKey = this.posKey(userId, ticker);
        Position posInCache = (Position)this.positions.get(pKey);
        Position pos = posInCache != null ? posInCache : new Position(userId, ticker, share.getUid(), 0, 0.0);
        if (!buy && pos.getQuantity() < qty) {
            return "\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0431\u0443\u043c\u0430\u0433 \u0432 \u043f\u043e\u0440\u0442\u0444\u0435\u043b\u0435.";
        }
        double turnover = price * (double)qty;
        double fee = Math.max(1.0, turnover * this.commissionRate);

        // Сохраняем состояние до сделки для возможного отката
        double origCash = user.getCash();
        double origBorrowed = user.getBorrowed();
        double origTotalFees = user.getTotalFees();
        int origQty = pos.getQuantity();
        double origAvgPrice = pos.getAvgPrice();

        if (buy) {
            user.setCash(user.getCash() - turnover - fee);
            int newQty = pos.getQuantity() + qty;
            double newAvg = (pos.getAvgPrice() * (double)pos.getQuantity() + turnover) / (double)newQty;
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvg);
            this.positions.put(pKey, pos);
        } else {
            user.setCash(user.getCash() + turnover - fee);
            pos.setQuantity(pos.getQuantity() - qty);
            if (pos.getQuantity() == 0) {
                this.positions.remove(pKey);
            } else {
                this.positions.put(pKey, pos);
            }
        }
        user.setTotalFees(user.getTotalFees() + fee);
        this.rebalanceDebt(user, userId);
        if (!this.checkRisk(user, userId)) {
            // Откатываем изменения в позиции
            if (posInCache == null) {
                this.positions.remove(pKey);
            } else {
                pos.setQuantity(origQty);
                pos.setAvgPrice(origAvgPrice);
                this.positions.put(pKey, pos);
            }
            // Откатываем состояние пользователя
            user.setCash(origCash);
            user.setBorrowed(origBorrowed);
            user.setTotalFees(origTotalFees);
            this.users.put(userId, user);
            return "\u274c \u0421\u0434\u0435\u043b\u043a\u0430 \u043e\u0442\u043a\u043b\u043e\u043d\u0435\u043d\u0430: \u043f\u0440\u0435\u0432\u044b\u0448\u0435\u043d \u0440\u0438\u0441\u043a/\u043f\u043b\u0435\u0447\u043e.";
        }
        this.users.put(userId, user);
        String tradeId = UUID.randomUUID().toString();
        this.trades.put(tradeId, new TradeRecord(tradeId, userId, ticker, buy ? "BUY" : "SELL", qty, price, fee, Instant.now()));
        return (buy ? "\ud83d\udfe2 \u041a\u0443\u043f\u043b\u0435\u043d\u043e " : "\ud83d\udd34 \u041f\u0440\u043e\u0434\u0430\u043d\u043e ") + qty + " " + ticker + " \u043f\u043e " + this.fmt(price) + " \u20bd. \u041a\u043e\u043c\u0438\u0441\u0441\u0438\u044f " + this.fmt(fee) + " \u20bd";
    }

    private void rebalanceDebt(SandboxUser user, String userId) {
        if (user.getCash() < 0.0) {
            user.setBorrowed(user.getBorrowed() + Math.abs(user.getCash()));
            user.setCash(0.0);
        } else if (user.getBorrowed() > 0.0 && user.getCash() > 0.0) {
            double repay = Math.min(user.getCash(), user.getBorrowed());
            user.setCash(user.getCash() - repay);
            user.setBorrowed(user.getBorrowed() - repay);
        }
        this.users.put(userId, user);
    }

    private boolean checkRisk(SandboxUser user, String userId) {
        double marginLevel;
        double lev;
        double eq = this.equity(userId, user);
        double gross = this.grossPositionValue(userId);
        if (eq <= 0.0) {
            this.liquidate(userId, user);
            return false;
        }
        double d = lev = gross <= 0.0 ? 0.0 : gross / eq;
        if (lev > this.maxLeverage) {
            return false;
        }
        if (user.getBorrowed() > 0.0 && (marginLevel = eq / user.getBorrowed()) < 0.2) {
            this.liquidate(userId, user);
        }
        return true;
    }

    private void liquidate(String userId, SandboxUser user) {
        List<Position> ps = this.userPositions(userId);
        for (Position p : ps) {
            double price = this.loadPrice(p.getInstrumentId());
            double turnover = price * (double)p.getQuantity();
            double fee = Math.max(1.0, turnover * this.commissionRate);
            user.setCash(user.getCash() + turnover - fee);
            this.positions.remove(this.posKey(userId, p.getTicker()));
        }
        this.rebalanceDebt(user, userId);
        this.users.put(userId, user);
    }

    public synchronized String portfolio(String userId) {
        SandboxUser user = (SandboxUser)this.users.get(userId);
        if (user == null) {
            return "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 +\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f";
        }
        List<Position> ps = this.userPositions(userId);
        if (ps.isEmpty()) {
            return "\u041f\u043e\u0440\u0442\u0444\u0435\u043b\u044c \u043f\u0443\u0441\u0442.";
        }
        StringBuilder sb = new StringBuilder("\u041f\u043e\u0440\u0442\u0444\u0435\u043b\u044c:\n");
        double totalPnl = 0.0;
        for (Position p : ps) {
            double price = this.loadPrice(p.getInstrumentId());
            double pnl = (price - p.getAvgPrice()) * p.getQuantity();
            double pnlPct = p.getAvgPrice() > 0.0 ? (price - p.getAvgPrice()) / p.getAvgPrice() * 100.0 : 0.0;
            totalPnl += pnl;
            String pnlSign = pnl >= 0.0 ? "+" : "";
            String pnlPctSign = pnlPct >= 0.0 ? "+" : "";
            sb.append(p.getTicker()).append(": ").append(p.getQuantity()).append(" \u0448\u0442, \u0441\u0440. ").append(this.fmt(p.getAvgPrice())).append(" \u20bd, \u0442\u0435\u043a\u0443\u0449. ").append(this.fmt(price)).append(" \u20bd, P&L: ").append(pnlSign).append(this.fmt(pnl)).append(" \u20bd (").append(pnlPctSign).append(String.format(Locale.US, "%.2f%%", pnlPct)).append(")\n");
        }
        String totalSign = totalPnl >= 0.0 ? "+" : "";
        sb.append("\u0418\u0442\u043e\u0433\u043e P&L: ").append(totalSign).append(this.fmt(totalPnl)).append(" \u20bd");
        return sb.toString();
    }

    public synchronized String balance(String userId) {
        SandboxUser user = (SandboxUser)this.users.get(userId);
        if (user == null) {
            return "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 +\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f";
        }
        double eq = this.equity(userId, user);
        double gross = this.grossPositionValue(userId);
        double lev = eq <= 0.0 ? 0.0 : gross / eq;
        double roi = this.safeRoi(eq, this.startBalance) * 100.0;
        String roiSign = roi >= 0.0 ? "+" : "";
        return "\ud83d\udcb0 \u0421\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0435 \u0441\u0440\u0435\u0434\u0441\u0442\u0432\u0430: " + this.fmt(user.getCash()) + " \u20bd\n\ud83d\udcc8 \u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c \u0430\u043a\u0442\u0438\u0432\u043e\u0432: " + this.fmt(gross) + " \u20bd\n\ud83d\udcb3 \u0417\u0430\u0451\u043c: " + this.fmt(user.getBorrowed()) + " \u20bd\n\ud83d\udcca Equity (\u0438\u0442\u043e\u0433\u043e): " + this.fmt(eq) + " \u20bd\n\ud83d\udcc9 ROI \u043e\u0442 \u0441\u0442\u0430\u0440\u0442\u0430: " + roiSign + String.format(Locale.US, "%.2f%%", roi) + "\n\u2696\ufe0f \u041f\u043b\u0435\u0447\u043e: x" + String.format(Locale.US, "%.2f", lev);
    }

    public synchronized String price(String ticker) {
        Share s = this.shareByTicker.get(ticker.toUpperCase(Locale.ROOT));
        if (s == null) {
            return "\u0422\u0438\u043a\u0435\u0440 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d.";
        }
        return ticker.toUpperCase() + " = " + this.fmt(this.loadPrice(s.getUid())) + " \u20bd";
    }

    public synchronized String margin(String userId) {
        SandboxUser user = (SandboxUser)this.users.get(userId);
        if (user == null) {
            return "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 +\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f";
        }
        double eq = this.equity(userId, user);
        if (user.getBorrowed() <= 0.0) {
            return "\u041c\u0430\u0440\u0436\u0438 \u043d\u0435\u0442. \u0417\u0430\u0435\u043c = 0.";
        }
        double level = eq / user.getBorrowed();
        return "Margin level: " + String.format(Locale.US, "%.2f", level) + "\n\u041f\u043e\u0440\u043e\u0433 margin call: " + this.maintenanceMargin + "\n\u041f\u043e\u0440\u043e\u0433 \u043b\u0438\u043a\u0432\u0438\u0434\u0430\u0446\u0438\u0438: 0.20";
    }

    public synchronized String top(String period) {
        ArrayList<SandboxUser> all = new ArrayList<SandboxUser>();
        for (Cache.Entry e : this.users) {
            all.add((SandboxUser)e.getValue());
        }
        if (all.isEmpty()) {
            return "\u041d\u0435\u0442 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0445 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u0439.";
        }
        // Do NOT record baselines here — baselines are set in trade()/register() so that
        // they capture equity BEFORE the period's trades, not after.
        all.sort((a, b) -> Double.compare(this.metric((SandboxUser)b, period), this.metric((SandboxUser)a, period)));
        StringBuilder sb = new StringBuilder("\ud83c\udfc6 \u0422\u043e\u043f-5 (" + period + ")\n");
        int n = Math.min(5, all.size());
        for (int i = 0; i < n; ++i) {
            SandboxUser u = (SandboxUser)all.get(i);
            sb.append(i + 1).append(") ").append(u.getUserName()).append(" \u2014 ").append(String.format(Locale.US, "%.2f%%", this.metric(u, period) * 100.0)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Records period baselines for a single user if a new period has started.
     * Must be called BEFORE executing a trade so that the captured equity
     * reflects the start-of-period value, not the post-trade value.
     */
    private void recordBaseline(SandboxUser u) {
        LocalDate now = LocalDate.now(ZONE);
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        double eq = this.equity(u.getUserId(), u);
        if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
            u.setDailyBaselineDate(now);
            u.setDailyBaselineEquity(eq);
        }
        if (u.getWeeklyBaselineDate() == null || u.getWeeklyBaselineDate().get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
            u.setWeeklyBaselineDate(now);
            u.setWeeklyBaselineEquity(eq);
        }
        if (u.getMonthlyBaselineDate() == null || u.getMonthlyBaselineDate().getMonthValue() != now.getMonthValue()) {
            u.setMonthlyBaselineDate(now);
            u.setMonthlyBaselineEquity(eq);
        }
        this.users.put(u.getUserId(), u);
    }

    private void recordBaselines(List<SandboxUser> usersList) {
        for (SandboxUser u : usersList) {
            this.recordBaseline(u);
        }
    }

    private double metric(SandboxUser u, String period) {
        double eq = this.equity(u.getUserId(), u);
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "\u0434\u0435\u043d\u044c" -> this.safeRoi(eq, u.getDailyBaselineEquity());
            case "\u043d\u0435\u0434\u0435\u043b\u044f" -> this.safeRoi(eq, u.getWeeklyBaselineEquity());
            case "\u043c\u0435\u0441\u044f\u0446" -> this.safeRoi(eq, u.getMonthlyBaselineEquity());
            default -> this.safeRoi(eq, this.startBalance);
        };
    }

    private double safeRoi(double now, double base) {
        if (base <= 0.0) {
            return 0.0;
        }
        return (now - base) / base;
    }

    private List<Position> userPositions(String userId) {
        ArrayList<Position> ps = new ArrayList<Position>();
        for (Cache.Entry e : this.positions) {
            if (!((Position)e.getValue()).getUserId().equals(userId)) continue;
            ps.add((Position)e.getValue());
        }
        return ps;
    }

    private double grossPositionValue(String userId) {
        return this.userPositions(userId).stream().mapToDouble(p -> this.loadPrice(p.getInstrumentId()) * (double)p.getQuantity()).sum();
    }

    private double equity(String userId, SandboxUser user) {
        return user.getCash() + this.grossPositionValue(userId) - user.getBorrowed();
    }

    private double loadPrice(String instrumentId) {
        List<LastPrice> prices = this.api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
        if (prices == null || prices.isEmpty()) {
            return 0.0;
        }
        return this.quotationToDouble(prices.get(0).getPrice());
    }

    private double quotationToDouble(Quotation q) {
        return (double)q.getUnits() + (double)q.getNano() / 1.0E9;
    }

    private String posKey(String userId, String ticker) {
        return userId + "::" + ticker;
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%,.2f", value).replace(',', ' ');
    }
}

