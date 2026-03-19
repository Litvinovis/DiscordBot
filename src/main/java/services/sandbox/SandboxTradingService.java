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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.cache.Cache;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import org.apache.ignite.IgniteCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.ignite.SandboxIgniteManager;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class SandboxTradingService {
    private static final Logger log = LoggerFactory.getLogger(SandboxTradingService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.ROOT);

    private final TInvestApi api;
    private final IgniteCache<String, SandboxUser> users;
    private final IgniteCache<String, Position> positions;
    private final IgniteCache<String, TradeRecord> trades;
    private final IgniteCache<String, LimitOrder> limitOrders;
    private final IgniteCache<String, StopOrder> stopOrders;
    private final IgniteCache<String, PriceAlert> priceAlerts;
    private final Map<String, Share> shareByTicker;
    /** instrumentId -> ticker reverse map (for scheduler lookups) */
    private final Map<String, String> tickerByUid;
    private final double startBalance = ConfigLoader.getSandboxStartBalance();
    private final double commissionRate = ConfigLoader.getSandboxCommissionRate();
    private final double maxLeverage = ConfigLoader.getSandboxMaxLeverage();
    private final double maintenanceMargin = ConfigLoader.getSandboxMaintenanceMargin();

    /** Per-user locks to prevent race conditions on concurrent trades */
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    /** JDA reference for DM notifications (set after bot is ready) */
    private volatile JDA jda;

    public SandboxTradingService(TInvestApi api) {
        this.api = api;
        SandboxIgniteManager manager = new SandboxIgniteManager();
        this.users = manager.usersCache();
        this.positions = manager.positionsCache();
        this.trades = manager.tradesCache();
        this.limitOrders = manager.limitOrdersCache();
        this.stopOrders = manager.stopOrdersCache();
        this.priceAlerts = manager.priceAlertsCache();
        Set<String> allowed = ConfigLoader.getSandboxAllowedTickers().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        this.shareByTicker = api.getInstrumentsService().getAllSharesSync().stream()
                .filter(s -> "rub".equalsIgnoreCase(s.getCurrency()))
                .filter(s -> allowed.contains(s.getTicker().toUpperCase()))
                .collect(Collectors.toMap(s -> s.getTicker().toUpperCase(), s -> s, (a, b) -> a));
        this.tickerByUid = shareByTicker.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().getUid(), Map.Entry::getKey));
    }

    /** Inject JDA after bot is ready so we can send DMs */
    public void setJda(JDA jda) {
        this.jda = jda;
    }

    // -----------------------------------------------------------------------
    // Per-user locking (race condition protection — item 9)
    // -----------------------------------------------------------------------

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public String register(String userId, String userName) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            SandboxUser existing = users.get(userId);
            if (existing != null) {
                return "Вы уже зарегистрированы в песочнице.";
            }
            SandboxUser user = new SandboxUser(userId, userName, startBalance);
            recordBaseline(user);
            users.put(userId, user);
            return "✅ Регистрация успешна. Стартовый баланс: " + fmt(startBalance) + " ₽";
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Assets listing
    // -----------------------------------------------------------------------

    public synchronized String assets() {
        if (shareByTicker.isEmpty()) {
            return "Список активов пуст.";
        }
        return "Доступные тикеры: " + String.join(", ", new TreeSet<>(shareByTicker.keySet()));
    }

    // -----------------------------------------------------------------------
    // Buy / Sell  (item 9: per-user lock)
    // -----------------------------------------------------------------------

    public String buy(String userId, String userName, String ticker, int qty) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            return trade(userId, userName, ticker, qty, true);
        } finally {
            lock.unlock();
        }
    }

    public String sell(String userId, String userName, String ticker, int qty) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            return trade(userId, userName, ticker, qty, false);
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Core trade logic
    // -----------------------------------------------------------------------

    private String trade(String userId, String userName, String ticker, int qty, boolean buy) {
        if (qty <= 0) {
            return "Количество должно быть > 0";
        }
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        if (user.getUserName() == null || !user.getUserName().equals(userName)) {
            user.setUserName(userName);
        }
        ticker = ticker.toUpperCase(Locale.ROOT);
        Share share = shareByTicker.get(ticker);
        if (share == null) {
            return "Тикер не доступен в песочнице.";
        }
        recordBaseline(user);
        // Item 3: block trade if price is invalid
        double price;
        try {
            price = loadPrice(share.getUid());
        } catch (Exception e) {
            log.warn("Не удалось загрузить цену для {} : {}", ticker, e.getMessage());
            return "⚠️ Не удалось получить текущую цену для " + ticker + ". Торговля временно недоступна.";
        }
        if (price <= 0.0) {
            return "⚠️ Цена для " + ticker + " недоступна (получено 0.0). Торговля заблокирована до восстановления котировок.";
        }

        String pKey = posKey(userId, ticker);
        Position posInCache = positions.get(pKey);
        Position pos = posInCache != null ? posInCache : new Position(userId, ticker, share.getUid(), 0, 0.0);

        if (!buy && pos.getQuantity() < qty) {
            return "Недостаточно бумаг в портфеле.";
        }

        double turnover = price * qty;
        double fee = Math.max(1.0, turnover * commissionRate);

        double origCash = user.getCash();
        double origBorrowed = user.getBorrowed();
        double origTotalFees = user.getTotalFees();
        int origQty = pos.getQuantity();
        double origAvgPrice = pos.getAvgPrice();

        if (buy) {
            user.setCash(user.getCash() - turnover - fee);
            int newQty = pos.getQuantity() + qty;
            double newAvg = (pos.getAvgPrice() * pos.getQuantity() + turnover) / newQty;
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvg);
            positions.put(pKey, pos);
        } else {
            user.setCash(user.getCash() + turnover - fee);
            pos.setQuantity(pos.getQuantity() - qty);
            if (pos.getQuantity() == 0) {
                positions.remove(pKey);
            } else {
                positions.put(pKey, pos);
            }
        }
        user.setTotalFees(user.getTotalFees() + fee);
        rebalanceDebt(user, userId);

        if (!checkRisk(user, userId)) {
            // Rollback
            if (posInCache == null) {
                positions.remove(pKey);
            } else {
                pos.setQuantity(origQty);
                pos.setAvgPrice(origAvgPrice);
                positions.put(pKey, pos);
            }
            user.setCash(origCash);
            user.setBorrowed(origBorrowed);
            user.setTotalFees(origTotalFees);
            users.put(userId, user);
            return "❌ Сделка отклонена: превышен риск/плечо.";
        }

        users.put(userId, user);
        String tradeId = UUID.randomUUID().toString();
        trades.put(tradeId, new TradeRecord(tradeId, userId, ticker, buy ? "BUY" : "SELL", qty, price, fee, Instant.now()));
        return (buy ? "🟢 Куплено " : "🔴 Продано ") + qty + " " + ticker + " по " + fmt(price) + " ₽. Комиссия " + fmt(fee) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Portfolio
    // -----------------------------------------------------------------------

    public synchronized String portfolio(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<Position> ps = userPositions(userId);
        if (ps.isEmpty()) {
            return "Портфель пуст.";
        }
        StringBuilder sb = new StringBuilder("Портфель:\n");
        double totalPnl = 0.0;
        for (Position p : ps) {
            double price = loadPriceSafe(p.getInstrumentId());
            double pnl = (price - p.getAvgPrice()) * p.getQuantity();
            double pnlPct = p.getAvgPrice() > 0.0 ? (price - p.getAvgPrice()) / p.getAvgPrice() * 100.0 : 0.0;
            totalPnl += pnl;
            String pnlSign = pnl >= 0.0 ? "+" : "";
            String pnlPctSign = pnlPct >= 0.0 ? "+" : "";
            sb.append(p.getTicker()).append(": ").append(p.getQuantity())
                    .append(" шт, ср. ").append(fmt(p.getAvgPrice()))
                    .append(" ₽, текущ. ").append(price > 0 ? fmt(price) : "N/A")
                    .append(" ₽, P&L: ").append(pnlSign).append(fmt(pnl))
                    .append(" ₽ (").append(pnlPctSign)
                    .append(String.format(Locale.US, "%.2f%%", pnlPct)).append(")\n");
        }
        String totalSign = totalPnl >= 0.0 ? "+" : "";
        sb.append("Итого P&L: ").append(totalSign).append(fmt(totalPnl)).append(" ₽");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Balance  (item 6: leverage status)
    // -----------------------------------------------------------------------

    public synchronized String balance(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        double eq = equity(userId, user);
        double gross = grossPositionValue(userId);
        double lev = eq <= 0.0 ? 0.0 : gross / eq;
        double roi = safeRoi(eq, startBalance) * 100.0;
        String roiSign = roi >= 0.0 ? "+" : "";

        String leverageStatus;
        if (lev < 2.0) {
            leverageStatus = "✅ БЕЗОПАСНО";
        } else if (lev <= 4.0) {
            leverageStatus = "⚠️ ВНИМАНИЕ";
        } else {
            leverageStatus = "🚨 КРИТИЧНО (ликвидация скоро)";
        }

        return "💰 Свободные средства: " + fmt(user.getCash()) + " ₽\n"
                + "📈 Стоимость активов: " + fmt(gross) + " ₽\n"
                + "💳 Заём: " + fmt(user.getBorrowed()) + " ₽\n"
                + "📊 Equity (итого): " + fmt(eq) + " ₽\n"
                + "📉 ROI от старта: " + roiSign + String.format(Locale.US, "%.2f%%", roi) + "\n"
                + "⚖️ Плечо: x" + String.format(Locale.US, "%.2f", lev) + " " + leverageStatus;
    }

    // -----------------------------------------------------------------------
    // Margin
    // -----------------------------------------------------------------------

    public synchronized String margin(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        double eq = equity(userId, user);
        if (user.getBorrowed() <= 0.0) {
            return "Маржи нет. Заём = 0.";
        }
        double level = eq / user.getBorrowed();
        double lev = eq <= 0.0 ? 0.0 : grossPositionValue(userId) / eq;
        String leverageStatus;
        if (lev < 2.0) {
            leverageStatus = "✅ БЕЗОПАСНО";
        } else if (lev <= 4.0) {
            leverageStatus = "⚠️ ВНИМАНИЕ";
        } else {
            leverageStatus = "🚨 КРИТИЧНО";
        }
        return "Margin level: " + String.format(Locale.US, "%.2f", level) + "\n"
                + "Порог margin call: " + maintenanceMargin + "\n"
                + "Порог ликвидации: 0.20\n"
                + "Плечо: x" + String.format(Locale.US, "%.2f", lev) + " " + leverageStatus;
    }

    // -----------------------------------------------------------------------
    // Price
    // -----------------------------------------------------------------------

    public synchronized String price(String ticker) {
        Share s = shareByTicker.get(ticker.toUpperCase(Locale.ROOT));
        if (s == null) {
            return "Тикер не найден.";
        }
        double p = loadPriceSafe(s.getUid());
        if (p <= 0.0) {
            return ticker.toUpperCase() + " — цена временно недоступна";
        }
        return ticker.toUpperCase() + " = " + fmt(p) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Top
    // -----------------------------------------------------------------------

    public synchronized String top(String period) {
        List<SandboxUser> all = new ArrayList<>();
        for (Cache.Entry<String, SandboxUser> e : users) {
            all.add(e.getValue());
        }
        if (all.isEmpty()) {
            return "Нет зарегистрированных пользователей.";
        }
        all.sort((a, b) -> Double.compare(metric(b, period), metric(a, period)));
        StringBuilder sb = new StringBuilder("🏆 Топ-5 (" + period + ")\n");
        int n = Math.min(5, all.size());
        for (int i = 0; i < n; i++) {
            SandboxUser u = all.get(i);
            sb.append(i + 1).append(") ").append(u.getUserName())
                    .append(" — ").append(String.format(Locale.US, "%.2f%%", metric(u, period) * 100.0)).append("\n");
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Item 7: My rank
    // -----------------------------------------------------------------------

    public synchronized String myRank(String userId) {
        SandboxUser target = users.get(userId);
        if (target == null) {
            return "Сначала выполните +регистрация";
        }
        List<SandboxUser> all = new ArrayList<>();
        for (Cache.Entry<String, SandboxUser> e : users) {
            all.add(e.getValue());
        }
        all.sort((a, b) -> Double.compare(equity(b.getUserId(), b), equity(a.getUserId(), a)));
        int rank = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getUserId().equals(userId)) {
                rank = i + 1;
                break;
            }
        }
        double eq = equity(userId, target);
        double roi = safeRoi(eq, startBalance) * 100.0;
        String roiSign = roi >= 0.0 ? "+" : "";
        return "📊 Ваш рейтинг: #" + rank + " из " + all.size() + "\n"
                + "Equity: " + fmt(eq) + " ₽\n"
                + "ROI: " + roiSign + String.format(Locale.US, "%.2f%%", roi);
    }

    // -----------------------------------------------------------------------
    // Item 1: Trade history
    // -----------------------------------------------------------------------

    public synchronized String history(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<TradeRecord> userTrades = new ArrayList<>();
        for (Cache.Entry<String, TradeRecord> e : trades) {
            if (userId.equals(e.getValue().getUserId())) {
                userTrades.add(e.getValue());
            }
        }
        if (userTrades.isEmpty()) {
            return "История сделок пуста.";
        }
        // Sort newest first, show last 20
        userTrades.sort(Comparator.comparing(TradeRecord::getTimestamp).reversed());
        int limit = Math.min(20, userTrades.size());
        StringBuilder sb = new StringBuilder("📋 История сделок (последние " + limit + "):\n");
        for (int i = 0; i < limit; i++) {
            TradeRecord r = userTrades.get(i);
            String dt = ZonedDateTime.ofInstant(r.getTimestamp(), ZONE).format(DT_FMT);
            String side = "BUY".equals(r.getSide()) ? "🟢 Покупка" : "🔴 Продажа";
            sb.append(dt).append(" | ").append(side).append(" ").append(r.getQty())
                    .append(" ").append(r.getTicker())
                    .append(" @ ").append(fmt(r.getPrice())).append(" ₽")
                    .append(" (комиссия ").append(fmt(r.getFee())).append(" ₽)\n");
        }
        return sb.toString().trim();
    }

    // -----------------------------------------------------------------------
    // Item 8: Statistics
    // -----------------------------------------------------------------------

    public synchronized String stats(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<TradeRecord> userTrades = new ArrayList<>();
        for (Cache.Entry<String, TradeRecord> e : trades) {
            if (userId.equals(e.getValue().getUserId())) {
                userTrades.add(e.getValue());
            }
        }
        if (userTrades.isEmpty()) {
            return "Статистика пуста — нет совершённых сделок.";
        }

        // Reconstruct realized PnL per trade by pairing sells with buys (simplified: use current avg approach)
        // For stats we track individual sell trades and estimate PnL vs weighted avg cost
        // Since we don't store avgPrice at trade time, we use the trade price itself relative to 0.
        // Instead, we produce simple trade-level stats: fee-adjusted turnover groupings.
        // A "profitable" sell trade = price > avgCost at that moment.
        // Since we don't record avgCost per trade, we track per-ticker realized PnL using a running avg.

        // Simplified approach: compute realized PnL for sells using recorded avg prices by re-simulating
        // For now compute from BUY/SELL pairs per ticker
        Map<String, Double> avgCostByTicker = new java.util.HashMap<>();
        Map<String, Integer> qtyByTicker = new java.util.HashMap<>();

        List<TradeRecord> sorted = new ArrayList<>(userTrades);
        sorted.sort(Comparator.comparing(TradeRecord::getTimestamp));

        List<Double> realizedPnlList = new ArrayList<>();
        int totalTrades = userTrades.size();

        for (TradeRecord r : sorted) {
            if ("BUY".equals(r.getSide())) {
                double prevAvg = avgCostByTicker.getOrDefault(r.getTicker(), 0.0);
                int prevQty = qtyByTicker.getOrDefault(r.getTicker(), 0);
                int newQty = prevQty + r.getQty();
                double newAvg = (prevAvg * prevQty + r.getPrice() * r.getQty()) / newQty;
                avgCostByTicker.put(r.getTicker(), newAvg);
                qtyByTicker.put(r.getTicker(), newQty);
            } else {
                double avgCost = avgCostByTicker.getOrDefault(r.getTicker(), r.getPrice());
                double pnl = (r.getPrice() - avgCost) * r.getQty() - r.getFee();
                realizedPnlList.add(pnl);
                int prevQty = qtyByTicker.getOrDefault(r.getTicker(), r.getQty());
                int newQty = Math.max(0, prevQty - r.getQty());
                qtyByTicker.put(r.getTicker(), newQty);
            }
        }

        if (realizedPnlList.isEmpty()) {
            return "Статистика: " + totalTrades + " сделок, закрытых позиций пока нет.";
        }

        long wins = realizedPnlList.stream().filter(p -> p > 0).count();
        double winRate = (double) wins / realizedPnlList.size() * 100.0;
        double avgPnl = realizedPnlList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double bestPnl = realizedPnlList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double worstPnl = realizedPnlList.stream().mapToDouble(Double::doubleValue).min().orElse(0);

        return "📊 Статистика трейдинга:\n"
                + "Всего сделок: " + totalTrades + "\n"
                + "Закрытых позиций: " + realizedPnlList.size() + "\n"
                + "Win rate: " + String.format(Locale.US, "%.1f%%", winRate) + "\n"
                + "Средний P&L: " + fmt(avgPnl) + " ₽\n"
                + "Лучшая сделка: +" + fmt(bestPnl) + " ₽\n"
                + "Худшая сделка: " + fmt(worstPnl) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Item 2: Stop Loss / Take Profit
    // -----------------------------------------------------------------------

    public String setStopLoss(String userId, String ticker, double triggerPrice) {
        return setStopOrder(userId, ticker, "SL", triggerPrice);
    }

    public String setTakeProfit(String userId, String ticker, double triggerPrice) {
        return setStopOrder(userId, ticker, "TP", triggerPrice);
    }

    private String setStopOrder(String userId, String ticker, String type, double triggerPrice) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) {
            return "Тикер не доступен в песочнице.";
        }
        Position pos = positions.get(posKey(userId, ticker));
        if (pos == null || pos.getQuantity() <= 0) {
            return "У вас нет открытой позиции по " + ticker;
        }
        if (triggerPrice <= 0.0) {
            return "Цена триггера должна быть > 0";
        }
        // Remove existing SL/TP of same type for this ticker
        List<String> toRemove = new ArrayList<>();
        for (Cache.Entry<String, StopOrder> e : stopOrders) {
            StopOrder so = e.getValue();
            if (userId.equals(so.getUserId()) && ticker.equals(so.getTicker()) && type.equals(so.getType())) {
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(stopOrders::remove);

        String id = UUID.randomUUID().toString();
        stopOrders.put(id, new StopOrder(id, userId, ticker, type, triggerPrice, Instant.now()));
        String typeName = "SL".equals(type) ? "Стоп-лосс" : "Тейк-профит";
        return "✅ " + typeName + " на " + ticker + " установлен: " + fmt(triggerPrice) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Item 4: Limit orders
    // -----------------------------------------------------------------------

    public String placeLimitBuy(String userId, String userName, String ticker, int qty, double limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "BUY");
    }

    public String placeLimitSell(String userId, String userName, String ticker, int qty, double limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "SELL");
    }

    private String placeLimitOrder(String userId, String userName, String ticker, int qty, double limitPrice, String side) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) {
            return "Тикер не доступен в песочнице.";
        }
        if (qty <= 0) {
            return "Количество должно быть > 0";
        }
        if (limitPrice <= 0.0) {
            return "Цена должна быть > 0";
        }
        String id = UUID.randomUUID().toString();
        limitOrders.put(id, new LimitOrder(id, userId, userName, ticker, side, qty, limitPrice, Instant.now()));
        String sideLabel = "BUY".equals(side) ? "покупку" : "продажу";
        return "✅ Лимитная заявка на " + sideLabel + " " + qty + " " + ticker
                + " @ " + fmt(limitPrice) + " ₽ принята (ID: " + id.substring(0, 8) + "...)";
    }

    public synchronized String myOrders(String userId) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<LimitOrder> orders = new ArrayList<>();
        for (Cache.Entry<String, LimitOrder> e : limitOrders) {
            if (userId.equals(e.getValue().getUserId())) {
                orders.add(e.getValue());
            }
        }
        if (orders.isEmpty()) {
            return "Нет активных лимитных заявок.";
        }
        orders.sort(Comparator.comparing(LimitOrder::getCreatedAt));
        StringBuilder sb = new StringBuilder("📋 Активные заявки:\n");
        for (LimitOrder o : orders) {
            String sideLabel = "BUY".equals(o.getSide()) ? "Покупка" : "Продажа";
            String dt = ZonedDateTime.ofInstant(o.getCreatedAt(), ZONE).format(DT_FMT);
            sb.append("[").append(o.getId().substring(0, 8)).append("] ")
                    .append(dt).append(" | ").append(sideLabel).append(" ")
                    .append(o.getQty()).append(" ").append(o.getTicker())
                    .append(" @ ").append(fmt(o.getLimitPrice())).append(" ₽\n");
        }
        return sb.toString().trim();
    }

    public String cancelOrder(String userId, String orderId) {
        // Try full or prefix match
        String fullKey = null;
        for (Cache.Entry<String, LimitOrder> e : limitOrders) {
            LimitOrder o = e.getValue();
            if (userId.equals(o.getUserId())
                    && (e.getKey().equals(orderId) || e.getKey().startsWith(orderId))) {
                fullKey = e.getKey();
                break;
            }
        }
        if (fullKey == null) {
            return "Заявка не найдена или уже исполнена.";
        }
        LimitOrder o = limitOrders.get(fullKey);
        limitOrders.remove(fullKey);
        return "✅ Заявка [" + fullKey.substring(0, 8) + "] отменена: "
                + o.getSide() + " " + o.getQty() + " " + o.getTicker()
                + " @ " + fmt(o.getLimitPrice()) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Item 5: Price alerts
    // -----------------------------------------------------------------------

    public String setAlert(String userId, String ticker, double targetPrice) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) {
            return "Тикер не доступен в песочнице.";
        }
        if (targetPrice <= 0.0) {
            return "Целевая цена должна быть > 0";
        }
        Share share = shareByTicker.get(ticker);
        double currentPrice = loadPriceSafe(share.getUid());
        boolean above = currentPrice <= 0.0 || targetPrice > currentPrice;

        String id = UUID.randomUUID().toString();
        priceAlerts.put(id, new PriceAlert(id, userId, ticker, targetPrice, above, Instant.now()));
        String direction = above ? "достигнет или превысит" : "упадёт до";
        return "🔔 Алерт установлен: уведомлю когда " + ticker + " " + direction + " " + fmt(targetPrice) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Scheduler callbacks (called by SandboxOrderScheduler)
    // -----------------------------------------------------------------------

    /**
     * Check all stop-loss and take-profit orders. Called periodically by the scheduler.
     * Returns list of notification messages (userId -> message).
     */
    public List<String[]> checkStopOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, StopOrder> e : stopOrders) {
            StopOrder so = e.getValue();
            Share share = shareByTicker.get(so.getTicker());
            if (share == null) continue;

            double price = loadPriceSafe(share.getUid());
            if (price <= 0.0) continue;

            boolean triggered;
            if ("SL".equals(so.getType())) {
                triggered = price <= so.getTriggerPrice();
            } else { // TP
                triggered = price >= so.getTriggerPrice();
            }

            if (triggered) {
                // Get the user position
                Position pos = positions.get(posKey(so.getUserId(), so.getTicker()));
                if (pos == null || pos.getQuantity() <= 0) {
                    toRemove.add(e.getKey());
                    continue;
                }

                SandboxUser user = users.get(so.getUserId());
                if (user == null) {
                    toRemove.add(e.getKey());
                    continue;
                }

                // Execute the sell
                ReentrantLock lock = lockFor(so.getUserId());
                lock.lock();
                try {
                    String result = trade(so.getUserId(), user.getUserName(), so.getTicker(), pos.getQuantity(), false);
                    String typeName = "SL".equals(so.getType()) ? "Стоп-лосс" : "Тейк-профит";
                    String msg = "⚡ " + typeName + " сработал! " + so.getTicker()
                            + " @ " + fmt(price) + " ₽ → " + result;
                    notifications.add(new String[]{so.getUserId(), msg});
                    toRemove.add(e.getKey());
                } finally {
                    lock.unlock();
                }
            }
        }
        toRemove.forEach(stopOrders::remove);
        return notifications;
    }

    /**
     * Check all limit orders. Called periodically by the scheduler.
     */
    public List<String[]> checkLimitOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, LimitOrder> e : limitOrders) {
            LimitOrder lo = e.getValue();
            Share share = shareByTicker.get(lo.getTicker());
            if (share == null) continue;

            double price = loadPriceSafe(share.getUid());
            if (price <= 0.0) continue;

            boolean triggered;
            if ("BUY".equals(lo.getSide())) {
                triggered = price <= lo.getLimitPrice();
            } else {
                triggered = price >= lo.getLimitPrice();
            }

            if (triggered) {
                ReentrantLock lock = lockFor(lo.getUserId());
                lock.lock();
                try {
                    String result = trade(lo.getUserId(), lo.getUserName(), lo.getTicker(), lo.getQty(),
                            "BUY".equals(lo.getSide()));
                    String sideLabel = "BUY".equals(lo.getSide()) ? "покупка" : "продажа";
                    String msg = "✅ Лимитная заявка исполнена: " + sideLabel + " " + lo.getQty()
                            + " " + lo.getTicker() + " @ " + fmt(price) + " ₽\n" + result;
                    notifications.add(new String[]{lo.getUserId(), msg});
                    toRemove.add(e.getKey());
                } finally {
                    lock.unlock();
                }
            }
        }
        toRemove.forEach(limitOrders::remove);
        return notifications;
    }

    /**
     * Check all price alerts. Called periodically by the scheduler.
     */
    public List<String[]> checkPriceAlerts() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, PriceAlert> e : priceAlerts) {
            PriceAlert alert = e.getValue();
            Share share = shareByTicker.get(alert.getTicker());
            if (share == null) continue;

            double price = loadPriceSafe(share.getUid());
            if (price <= 0.0) continue;

            boolean triggered;
            if (alert.isAbove()) {
                triggered = price >= alert.getTargetPrice();
            } else {
                triggered = price <= alert.getTargetPrice();
            }

            if (triggered) {
                String msg = "🔔 Алерт! " + alert.getTicker() + " = " + fmt(price)
                        + " ₽ (целевая: " + fmt(alert.getTargetPrice()) + " ₽)";
                notifications.add(new String[]{alert.getUserId(), msg});
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(priceAlerts::remove);
        return notifications;
    }

    /**
     * Send DM notification to a user. Should be called from the scheduler
     * after acquiring JDA reference.
     */
    public void sendDm(String userId, String message) {
        if (jda == null) return;
        try {
            jda.retrieveUserById(userId).queue(user -> {
                user.openPrivateChannel().queue(ch -> ch.sendMessage(message).queue());
            }, err -> log.warn("Cannot retrieve user {} for DM: {}", userId, err.getMessage()));
        } catch (Exception ex) {
            log.warn("Failed to send DM to {}: {}", userId, ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void rebalanceDebt(SandboxUser user, String userId) {
        if (user.getCash() < 0.0) {
            user.setBorrowed(user.getBorrowed() + Math.abs(user.getCash()));
            user.setCash(0.0);
        } else if (user.getBorrowed() > 0.0 && user.getCash() > 0.0) {
            double repay = Math.min(user.getCash(), user.getBorrowed());
            user.setCash(user.getCash() - repay);
            user.setBorrowed(user.getBorrowed() - repay);
        }
        users.put(userId, user);
    }

    private boolean checkRisk(SandboxUser user, String userId) {
        double eq = equity(userId, user);
        double gross = grossPositionValue(userId);
        if (eq <= 0.0) {
            liquidate(userId, user);
            return false;
        }
        double lev = gross <= 0.0 ? 0.0 : gross / eq;
        if (lev > maxLeverage) {
            return false;
        }
        if (user.getBorrowed() > 0.0) {
            double marginLevel = eq / user.getBorrowed();
            if (marginLevel < 0.2) {
                liquidate(userId, user);
                // Notify DM about margin call
                String msg = "🚨 Margin call / Ликвидация! Ваши позиции по " + userId + " принудительно закрыты.";
                sendDm(userId, msg);
            }
        }
        return true;
    }

    private void liquidate(String userId, SandboxUser user) {
        List<Position> ps = userPositions(userId);
        for (Position p : ps) {
            double price = loadPriceSafe(p.getInstrumentId());
            if (price <= 0.0) price = p.getAvgPrice(); // fallback to avg
            double turnover = price * p.getQuantity();
            double fee = Math.max(1.0, turnover * commissionRate);
            user.setCash(user.getCash() + turnover - fee);
            positions.remove(posKey(userId, p.getTicker()));
            // Remove any stop orders for this ticker
            List<String> soKeys = new ArrayList<>();
            for (Cache.Entry<String, StopOrder> e : stopOrders) {
                if (userId.equals(e.getValue().getUserId()) && p.getTicker().equals(e.getValue().getTicker())) {
                    soKeys.add(e.getKey());
                }
            }
            soKeys.forEach(stopOrders::remove);
        }
        rebalanceDebt(user, userId);
        users.put(userId, user);
    }

    /**
     * Records period baselines for a single user if a new period has started.
     * Must be called BEFORE executing a trade so that the captured equity
     * reflects the start-of-period value, not the post-trade value.
     */
    private void recordBaseline(SandboxUser u) {
        LocalDate now = LocalDate.now(ZONE);
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        double eq = equity(u.getUserId(), u);
        if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
            u.setDailyBaselineDate(now);
            u.setDailyBaselineEquity(eq);
        }
        if (u.getWeeklyBaselineDate() == null ||
                u.getWeeklyBaselineDate().get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
            u.setWeeklyBaselineDate(now);
            u.setWeeklyBaselineEquity(eq);
        }
        if (u.getMonthlyBaselineDate() == null ||
                u.getMonthlyBaselineDate().getMonthValue() != now.getMonthValue()) {
            u.setMonthlyBaselineDate(now);
            u.setMonthlyBaselineEquity(eq);
        }
        users.put(u.getUserId(), u);
    }

    private double metric(SandboxUser u, String period) {
        double eq = equity(u.getUserId(), u);
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "день" -> safeRoi(eq, u.getDailyBaselineEquity());
            case "неделя" -> safeRoi(eq, u.getWeeklyBaselineEquity());
            case "месяц" -> safeRoi(eq, u.getMonthlyBaselineEquity());
            default -> safeRoi(eq, startBalance);
        };
    }

    private double safeRoi(double now, double base) {
        if (base <= 0.0) return 0.0;
        return (now - base) / base;
    }

    private List<Position> userPositions(String userId) {
        List<Position> ps = new ArrayList<>();
        for (Cache.Entry<String, Position> e : positions) {
            if (userId.equals(e.getValue().getUserId())) {
                ps.add(e.getValue());
            }
        }
        return ps;
    }

    private double grossPositionValue(String userId) {
        return userPositions(userId).stream()
                .mapToDouble(p -> loadPriceSafe(p.getInstrumentId()) * p.getQuantity())
                .sum();
    }

    private double equity(String userId, SandboxUser user) {
        return user.getCash() + grossPositionValue(userId) - user.getBorrowed();
    }

    /** Load price and throw if API call fails (for interactive trade commands) */
    private double loadPrice(String instrumentId) {
        List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
        if (prices == null || prices.isEmpty()) {
            return 0.0;
        }
        return quotationToDouble(prices.get(0).getPrice());
    }

    /** Load price safely — returns 0.0 on any error (for portfolio display, scheduler) */
    private double loadPriceSafe(String instrumentId) {
        try {
            return loadPrice(instrumentId);
        } catch (Exception e) {
            log.warn("loadPriceSafe failed for {}: {}", instrumentId, e.getMessage());
            return 0.0;
        }
    }

    private double quotationToDouble(Quotation q) {
        return (double) q.getUnits() + (double) q.getNano() / 1_000_000_000.0;
    }

    private String posKey(String userId, String ticker) {
        return userId + "::" + ticker;
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%,.2f", value).replace(',', ' ');
    }
}
