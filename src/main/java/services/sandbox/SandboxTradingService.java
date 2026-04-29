package services.sandbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.db.SandboxIgniteManager;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.Position;
import services.sandbox.model.PriceAlert;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.StopOrder;
import services.sandbox.model.TradeRecord;
import services.sandbox.repository.LimitOrderRepository;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.PriceAlertRepository;
import services.sandbox.repository.SandboxUserRepository;
import services.sandbox.repository.StopOrderRepository;
import services.sandbox.repository.TradeRepository;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

/**
 * Основной сервис торговой песочницы Stonks Bot.
 *
 * Все данные хранятся в PostgreSQL через репозитории, управляемые
 * {@link services.sandbox.db.SandboxIgniteManager}.
 */
public class SandboxTradingService implements
        services.sandbox.api.ISandboxOrderService,
        services.sandbox.api.ISandboxPortfolioService,
        services.sandbox.api.ISandboxRatingService {
    private static final Logger log = LoggerFactory.getLogger(SandboxTradingService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.ROOT);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int SCALE = 8;

    private final TInvestApi api;
    private final SandboxIgniteManager igniteManager;
    private final SandboxUserRepository users;
    private final PositionRepository positions;
    private final TradeRepository trades;
    private final LimitOrderRepository limitOrders;
    private final StopOrderRepository stopOrders;
    private final PriceAlertRepository priceAlerts;
    private final Map<String, Share> shareByTicker;
    private final Map<String, String> tickerByUid;
    private final BigDecimal startBalance = ConfigLoader.getSandboxStartBalance();
    private final BigDecimal commissionRate = ConfigLoader.getSandboxCommissionRate();
    private final BigDecimal maxLeverage = ConfigLoader.getSandboxMaxLeverage();
    private final BigDecimal maintenanceMargin = ConfigLoader.getSandboxMaintenanceMargin();

    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private volatile SandboxCurrencyService currencyService;
    private volatile JDA jda;

    public SandboxTradingService(TInvestApi api) {
        this.api = api;
        SandboxIgniteManager manager = new SandboxIgniteManager();
        this.igniteManager = manager;
        this.users = manager.usersRepo();
        this.positions = manager.positionsRepo();
        this.trades = manager.tradesRepo();
        this.limitOrders = manager.limitOrdersRepo();
        this.stopOrders = manager.stopOrdersRepo();
        this.priceAlerts = manager.priceAlertsRepo();
        Set<String> allowed = ConfigLoader.getSandboxAllowedTickers().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        this.shareByTicker = api.getInstrumentsService().getAllSharesSync().stream()
                .filter(s -> allowed.contains(s.getTicker().toUpperCase()))
                .collect(Collectors.toMap(s -> s.getTicker().toUpperCase(), s -> s, (a, b) -> a));
        this.tickerByUid = shareByTicker.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().getUid(), Map.Entry::getKey));
    }

    public SandboxCurrencyService createCurrencyService() {
        if (currencyService == null) {
            synchronized (this) {
                if (currencyService == null) {
                    currencyService = new SandboxCurrencyService(users, new CbrRateService(), userLocks);
                }
            }
        }
        return currencyService;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
    }

    public String register(String userId, String userName) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            SandboxUser existing = users.findById(userId);
            if (existing != null) {
                return "Вы уже зарегистрированы в песочнице.";
            }
            SandboxUser user = new SandboxUser(userId, userName, startBalance.doubleValue());
            recordBaseline(user);
            users.save(userId, user);
            return "✅ Регистрация успешна. Стартовый баланс: " + fmt(startBalance) + " ₽";
        } finally {
            lock.unlock();
        }
    }

    public String assets() {
        if (shareByTicker.isEmpty()) {
            return "Список активов пуст.";
        }
        return "Доступные тикеры: " + String.join(", ", new TreeSet<>(shareByTicker.keySet()));
    }

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

    private String trade(String userId, String userName, String ticker, int qty, boolean buy) {
        if (qty <= 0) {
            return "Количество должно быть > 0";
        }
        SandboxUser user = users.findById(userId);
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

        BigDecimal price;
        try {
            price = loadPrice(share.getUid());
        } catch (Exception e) {
            log.warn("Не удалось загрузить цену для {} : {}", ticker, e.getMessage());
            return "⚠️ Не удалось получить текущую цену для " + ticker + ". Торговля временно недоступна.";
        }
        if (price.compareTo(ZERO) <= 0) {
            return "⚠️ Цена для " + ticker + " недоступна (получено 0.0). Торговля заблокирована до восстановления котировок.";
        }

        String pKey = posKey(userId, ticker);
        Position posInCache = positions.findById(pKey);
        Position pos = posInCache != null
                ? posInCache
                : new Position(userId, ticker, share.getUid(), 0, 0.0);

        if (!buy && pos.getQuantity() < qty) {
            return "Недостаточно бумаг в портфеле.";
        }

        BigDecimal qtyBD = BigDecimal.valueOf(qty);
        BigDecimal turnover = price.multiply(qtyBD);
        BigDecimal fee = turnover.multiply(commissionRate).setScale(SCALE, RoundingMode.HALF_UP);
        if (fee.compareTo(ONE) < 0) {
            fee = ONE;
        }

        BigDecimal origCash = BigDecimal.valueOf(user.getCash());
        BigDecimal origBorrowed = BigDecimal.valueOf(user.getBorrowed());
        BigDecimal origTotalFees = BigDecimal.valueOf(user.getTotalFees());
        int origQty = pos.getQuantity();
        BigDecimal origAvgPrice = BigDecimal.valueOf(pos.getAvgPrice());

        if (buy) {
            BigDecimal newCash = origCash.subtract(turnover).subtract(fee);
            user.setCash(newCash.doubleValue());
            int newQty = pos.getQuantity() + qty;
            BigDecimal newAvg = origAvgPrice
                    .multiply(BigDecimal.valueOf(pos.getQuantity()))
                    .add(turnover)
                    .divide(BigDecimal.valueOf(newQty), SCALE, RoundingMode.HALF_UP);
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvg.doubleValue());
            positions.save(pKey, pos);
        } else {
            BigDecimal newCash = origCash.add(turnover).subtract(fee);
            user.setCash(newCash.doubleValue());
            pos.setQuantity(pos.getQuantity() - qty);
            if (pos.getQuantity() == 0) {
                positions.delete(pKey);
            } else {
                positions.save(pKey, pos);
            }
        }
        user.setTotalFees(origTotalFees.add(fee).doubleValue());
        rebalanceDebt(user, userId);

        if (!checkRisk(user, userId)) {
            if (posInCache == null) {
                positions.delete(pKey);
            } else {
                pos.setQuantity(origQty);
                pos.setAvgPrice(origAvgPrice.doubleValue());
                positions.save(pKey, pos);
            }
            user.setCash(origCash.doubleValue());
            user.setBorrowed(origBorrowed.doubleValue());
            user.setTotalFees(origTotalFees.doubleValue());
            users.save(userId, user);
            return "❌ Сделка отклонена: превышен риск/плечо.";
        }

        users.save(userId, user);
        String tradeId = UUID.randomUUID().toString();
        trades.save(tradeId, new TradeRecord(tradeId, userId, ticker, buy ? "BUY" : "SELL", qty,
                price.doubleValue(), fee.doubleValue(), Instant.now()));
        String cur = currencySymbol(share.getCurrency());
        return (buy ? "🟢 Куплено " : "🔴 Продано ") + qty + " " + ticker + " по " + fmt(price) + " " + cur + ". Комиссия " + fmt(fee) + " " + cur;
    }

    public String portfolio(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<Position> ps = userPositions(userId);
        if (ps.isEmpty()) {
            return "Портфель пуст.";
        }
        StringBuilder sb = new StringBuilder("Портфель:\n");
        BigDecimal totalPnl = ZERO;
        for (Position p : ps) {
            BigDecimal price = loadPriceSafe(p.getInstrumentId());
            BigDecimal avgPrice = BigDecimal.valueOf(p.getAvgPrice());
            BigDecimal pnl = price.subtract(avgPrice)
                    .multiply(BigDecimal.valueOf(p.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal pnlPct;
            if (avgPrice.compareTo(ZERO) > 0) {
                pnlPct = price.subtract(avgPrice)
                        .divide(avgPrice, SCALE, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                pnlPct = ZERO;
            }
            totalPnl = totalPnl.add(pnl);
            String pnlSign = pnl.compareTo(ZERO) >= 0 ? "+" : "";
            String pnlPctSign = pnlPct.compareTo(ZERO) >= 0 ? "+" : "";
            sb.append(p.getTicker()).append(": ").append(p.getQuantity())
                    .append(" шт, ср. ").append(fmt(avgPrice))
                    .append(" ₽, текущ. ").append(price.compareTo(ZERO) > 0 ? fmt(price) : "N/A")
                    .append(" ₽, P&L: ").append(pnlSign).append(fmt(pnl))
                    .append(" ₽ (").append(pnlPctSign)
                    .append(pnlPct.toPlainString()).append("%)\n");
        }
        String totalSign = totalPnl.compareTo(ZERO) >= 0 ? "+" : "";
        sb.append("Итого P&L акции: ").append(totalSign).append(fmt(totalPnl)).append(" ₽");
        SandboxCurrencyService ccs = createCurrencyService();
        String ccyPortfolio = ccs.currencyPortfolio(userId);
        if (ccyPortfolio != null && !ccyPortfolio.equals("Валютных позиций нет.")) {
            sb.append("\n\n").append(ccyPortfolio);
        }
        return sb.toString();
    }

    public String balance(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        BigDecimal eq = equity(userId, user);
        BigDecimal gross = grossPositionValue(userId);
        BigDecimal lev = eq.compareTo(ZERO) <= 0
                ? ZERO
                : gross.divide(eq, SCALE, RoundingMode.HALF_UP);
        BigDecimal roi = safeRoi(eq, startBalance)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        String roiSign = roi.compareTo(ZERO) >= 0 ? "+" : "";
        String leverageStatus;
        if (lev.compareTo(new BigDecimal("2.0")) < 0) {
            leverageStatus = "✅ БЕЗОПАСНО";
        } else if (lev.compareTo(new BigDecimal("4.0")) <= 0) {
            leverageStatus = "⚠️ ВНИМАНИЕ";
        } else {
            leverageStatus = "🚨 КРИТИЧНО (ликвидация скоро)";
        }
        StringBuilder result = new StringBuilder();
        result.append("💰 Рублёвый счёт: ").append(fmt(BigDecimal.valueOf(user.getCash()))).append(" ₽\n");
        SandboxCurrencyService ccs = createCurrencyService();
        String ccyLine = ccs.currencyBalanceLine(userId);
        if (ccyLine != null && !ccyLine.isBlank()) {
            result.append(ccyLine).append("\n");
        }
        result.append("📈 Стоимость акций: ").append(fmt(gross)).append(" ₽\n")
                .append("💳 Заём: ").append(fmt(BigDecimal.valueOf(user.getBorrowed()))).append(" ₽\n")
                .append("📊 Equity (итого): ").append(fmt(eq)).append(" ₽\n")
                .append("📉 ROI от старта: ").append(roiSign).append(roi.toPlainString()).append("%\n")
                .append("⚖️ Плечо: x").append(lev.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append(" ").append(leverageStatus);
        return result.toString();
    }

    public String margin(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        BigDecimal eq = equity(userId, user);
        BigDecimal borrowed = BigDecimal.valueOf(user.getBorrowed());
        if (borrowed.compareTo(ZERO) <= 0) {
            return "Маржи нет. Заём = 0.";
        }
        BigDecimal level = eq.divide(borrowed, SCALE, RoundingMode.HALF_UP);
        BigDecimal gross = grossPositionValue(userId);
        BigDecimal lev = eq.compareTo(ZERO) <= 0
                ? ZERO
                : gross.divide(eq, SCALE, RoundingMode.HALF_UP);
        String leverageStatus;
        if (lev.compareTo(new BigDecimal("2.0")) < 0) {
            leverageStatus = "✅ БЕЗОПАСНО";
        } else if (lev.compareTo(new BigDecimal("4.0")) <= 0) {
            leverageStatus = "⚠️ ВНИМАНИЕ";
        } else {
            leverageStatus = "🚨 КРИТИЧНО";
        }
        return """
                Margin level: %s
                Порог margin call: %s
                Порог ликвидации: 0.20
                Плечо: x%s %s""".formatted(
                level.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                maintenanceMargin.toPlainString(),
                lev.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                leverageStatus);
    }

    public String price(String ticker) {
        Share s = shareByTicker.get(ticker.toUpperCase(Locale.ROOT));
        if (s == null) {
            return "Тикер не найден.";
        }
        BigDecimal p = loadPriceSafe(s.getUid());
        if (p.compareTo(ZERO) <= 0) {
            return ticker.toUpperCase() + " — цена временно недоступна";
        }
        return ticker.toUpperCase() + " = " + fmt(p) + " " + currencySymbol(s.getCurrency());
    }

    public String top(String period) {
        List<SandboxUser> all = users.findAll();
        if (all.isEmpty()) {
            return "Нет зарегистрированных пользователей.";
        }
        all.sort((a, b) -> metric(b, period).compareTo(metric(a, period)));
        StringBuilder sb = new StringBuilder("🏆 Топ-5 (" + period + ")\n");
        int n = Math.min(5, all.size());
        for (int i = 0; i < n; i++) {
            SandboxUser u = all.get(i);
            BigDecimal pct = metric(u, period)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            sb.append(i + 1).append(") ").append(u.getUserName())
                    .append(" — ").append(pct.toPlainString()).append("%\n");
        }
        return sb.toString();
    }

    public String myRank(String userId) {
        SandboxUser target = users.findById(userId);
        if (target == null) {
            return "Сначала выполните +регистрация";
        }
        List<SandboxUser> all = users.findAll();
        all.sort((a, b) -> equity(b.getUserId(), b).compareTo(equity(a.getUserId(), a)));
        int rank = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getUserId().equals(userId)) {
                rank = i + 1;
                break;
            }
        }
        BigDecimal eq = equity(userId, target);
        BigDecimal roi = safeRoi(eq, startBalance)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        String roiSign = roi.compareTo(ZERO) >= 0 ? "+" : "";
        return """
                📊 Ваш рейтинг: #%d из %d
                Equity: %s ₽
                ROI: %s%s%%""".formatted(rank, all.size(), fmt(eq), roiSign, roi.toPlainString());
    }

    public String history(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<TradeRecord> userTrades = trades.findByUserId(userId);
        if (userTrades.isEmpty()) {
            return "История сделок пуста.";
        }
        userTrades.sort(Comparator.comparing(TradeRecord::getTimestamp).reversed());
        int limit = Math.min(20, userTrades.size());
        StringBuilder sb = new StringBuilder("📋 История сделок (последние " + limit + "):\n");
        for (int i = 0; i < limit; i++) {
            TradeRecord r = userTrades.get(i);
            String dt = ZonedDateTime.ofInstant(r.getTimestamp(), ZONE).format(DT_FMT);
            String side = "BUY".equals(r.getSide()) ? "🟢 Покупка" : "🔴 Продажа";
            sb.append(dt).append(" | ").append(side).append(" ").append(r.getQty())
                    .append(" ").append(r.getTicker())
                    .append(" @ ").append(fmt(BigDecimal.valueOf(r.getPrice()))).append(" ₽")
                    .append(" (комиссия ").append(fmt(BigDecimal.valueOf(r.getFee()))).append(" ₽)\n");
        }
        return sb.toString().trim();
    }

    public String stats(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        List<TradeRecord> userTrades = trades.findByUserId(userId);
        if (userTrades.isEmpty()) {
            return "Статистика пуста — нет совершённых сделок.";
        }
        Map<String, BigDecimal> avgCostByTicker = new HashMap<>();
        Map<String, Integer> qtyByTicker = new HashMap<>();
        List<TradeRecord> sorted = new ArrayList<>(userTrades);
        sorted.sort(Comparator.comparing(TradeRecord::getTimestamp));
        List<BigDecimal> realizedPnlList = new ArrayList<>();
        int totalTrades = userTrades.size();
        for (TradeRecord r : sorted) {
            BigDecimal rPrice = BigDecimal.valueOf(r.getPrice());
            BigDecimal rFee = BigDecimal.valueOf(r.getFee());
            if ("BUY".equals(r.getSide())) {
                BigDecimal prevAvg = avgCostByTicker.getOrDefault(r.getTicker(), ZERO);
                int prevQty = qtyByTicker.getOrDefault(r.getTicker(), 0);
                int newQty = prevQty + r.getQty();
                BigDecimal newAvg = prevAvg.multiply(BigDecimal.valueOf(prevQty))
                        .add(rPrice.multiply(BigDecimal.valueOf(r.getQty())))
                        .divide(BigDecimal.valueOf(newQty), SCALE, RoundingMode.HALF_UP);
                avgCostByTicker.put(r.getTicker(), newAvg);
                qtyByTicker.put(r.getTicker(), newQty);
            } else {
                BigDecimal avgCost = avgCostByTicker.getOrDefault(r.getTicker(), rPrice);
                BigDecimal pnl = rPrice.subtract(avgCost)
                        .multiply(BigDecimal.valueOf(r.getQty()))
                        .subtract(rFee)
                        .setScale(2, RoundingMode.HALF_UP);
                realizedPnlList.add(pnl);
                int prevQty = qtyByTicker.getOrDefault(r.getTicker(), r.getQty());
                int newQty = Math.max(0, prevQty - r.getQty());
                qtyByTicker.put(r.getTicker(), newQty);
            }
        }
        if (realizedPnlList.isEmpty()) {
            return "Статистика: " + totalTrades + " сделок, закрытых позиций пока нет.";
        }
        long wins = realizedPnlList.stream().filter(p -> p.compareTo(ZERO) > 0).count();
        BigDecimal winRate = BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(realizedPnlList.size()), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal sum = realizedPnlList.stream().reduce(ZERO, BigDecimal::add);
        BigDecimal avgPnl = sum.divide(BigDecimal.valueOf(realizedPnlList.size()), SCALE, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal bestPnl = realizedPnlList.stream().max(BigDecimal::compareTo).orElse(ZERO);
        BigDecimal worstPnl = realizedPnlList.stream().min(BigDecimal::compareTo).orElse(ZERO);

        return """
                📊 Статистика трейдинга:
                Всего сделок: %d
                Закрытых позиций: %d
                Win rate: %s%%
                Средний P&L: %s ₽
                Лучшая сделка: +%s ₽
                Худшая сделка: %s ₽""".formatted(
                totalTrades,
                realizedPnlList.size(),
                winRate.toPlainString(),
                fmt(avgPnl),
                fmt(bestPnl),
                fmt(worstPnl));
    }

    public String setStopLoss(String userId, String ticker, BigDecimal triggerPrice) {
        return setStopOrder(userId, ticker, "SL", triggerPrice);
    }

    public String setTakeProfit(String userId, String ticker, BigDecimal triggerPrice) {
        return setStopOrder(userId, ticker, "TP", triggerPrice);
    }

    private String setStopOrder(String userId, String ticker, String type, BigDecimal triggerPrice) {
        SandboxUser user = users.findById(userId);
        if (user == null) return "Сначала выполните +регистрация";
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
        Position pos = positions.findById(posKey(userId, ticker));
        if (pos == null || pos.getQuantity() <= 0) return "У вас нет открытой позиции по " + ticker;
        if (triggerPrice.compareTo(ZERO) <= 0) return "Цена триггера должна быть > 0";
        for (StopOrder so : stopOrders.findAll()) {
            if (userId.equals(so.getUserId()) && ticker.equals(so.getTicker()) && type.equals(so.getType())) {
                stopOrders.delete(so.getId());
            }
        }
        String id = UUID.randomUUID().toString();
        stopOrders.save(id, new StopOrder(id, userId, ticker, type, triggerPrice.doubleValue(), Instant.now()));
        String typeName = "SL".equals(type) ? "Стоп-лосс" : "Тейк-профит";
        return "✅ " + typeName + " на " + ticker + " установлен: " + fmt(triggerPrice) + " ₽";
    }

    public String placeLimitBuy(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "BUY");
    }

    public String placeLimitSell(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "SELL");
    }

    private String placeLimitOrder(String userId, String userName, String ticker, int qty, BigDecimal limitPrice, String side) {
        SandboxUser user = users.findById(userId);
        if (user == null) return "Сначала выполните +регистрация";
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
        if (qty <= 0) return "Количество должно быть > 0";
        if (limitPrice.compareTo(ZERO) <= 0) return "Цена должна быть > 0";
        String id = UUID.randomUUID().toString();
        limitOrders.save(id, new LimitOrder(id, userId, userName, ticker, side, qty, limitPrice.doubleValue(), Instant.now()));
        String sideLabel = "BUY".equals(side) ? "покупку" : "продажу";
        return "✅ Лимитная заявка на " + sideLabel + " " + qty + " " + ticker
                + " @ " + fmt(limitPrice) + " ₽ принята (ID: " + id.substring(0, 8) + "...)";
    }

    public String myOrders(String userId) {
        SandboxUser user = users.findById(userId);
        if (user == null) return "Сначала выполните +регистрация";
        List<LimitOrder> orders = limitOrders.findAll().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .collect(Collectors.toList());
        if (orders.isEmpty()) return "Нет активных лимитных заявок.";
        orders.sort(Comparator.comparing(LimitOrder::getCreatedAt));
        StringBuilder sb = new StringBuilder("📋 Активные заявки:\n");
        for (LimitOrder o : orders) {
            String sideLabel = "BUY".equals(o.getSide()) ? "Покупка" : "Продажа";
            String dt = ZonedDateTime.ofInstant(o.getCreatedAt(), ZONE).format(DT_FMT);
            sb.append("[").append(o.getId().substring(0, 8)).append("] ")
                    .append(dt).append(" | ").append(sideLabel).append(" ")
                    .append(o.getQty()).append(" ").append(o.getTicker())
                    .append(" @ ").append(fmt(BigDecimal.valueOf(o.getLimitPrice()))).append(" ₽\n");
        }
        return sb.toString().trim();
    }

    public String cancelOrder(String userId, String orderId) {
        String fullKey = null;
        LimitOrder found = null;
        for (LimitOrder o : limitOrders.findAll()) {
            if (userId.equals(o.getUserId())
                    && (o.getId().equals(orderId) || o.getId().startsWith(orderId))) {
                fullKey = o.getId();
                found = o;
                break;
            }
        }
        if (fullKey == null || found == null) return "Заявка не найдена или уже исполнена.";
        limitOrders.delete(fullKey);
        return "✅ Заявка [" + fullKey.substring(0, 8) + "] отменена: "
                + found.getSide() + " " + found.getQty() + " " + found.getTicker()
                + " @ " + fmt(BigDecimal.valueOf(found.getLimitPrice())) + " ₽";
    }

    public String setAlert(String userId, String ticker, BigDecimal targetPrice) {
        SandboxUser user = users.findById(userId);
        if (user == null) return "Сначала выполните +регистрация";
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) return "Тикер не доступен в песочнице.";
        if (targetPrice.compareTo(ZERO) <= 0) return "Целевая цена должна быть > 0";
        Share share = shareByTicker.get(ticker);
        BigDecimal currentPrice = loadPriceSafe(share.getUid());
        boolean above = currentPrice.compareTo(ZERO) <= 0 || targetPrice.compareTo(currentPrice) > 0;
        String id = UUID.randomUUID().toString();
        priceAlerts.save(id, new PriceAlert(id, userId, ticker, targetPrice.doubleValue(), above, Instant.now()));
        String direction = above ? "достигнет или превысит" : "упадёт до";
        return "🔔 Алерт установлен: уведомлю когда " + ticker + " " + direction + " " + fmt(targetPrice) + " ₽";
    }

    public List<String[]> checkStopOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (StopOrder so : stopOrders.findAll()) {
            Share share = shareByTicker.get(so.getTicker());
            if (share == null) continue;
            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;
            BigDecimal triggerPrice = BigDecimal.valueOf(so.getTriggerPrice());
            boolean triggered = "SL".equals(so.getType())
                    ? price.compareTo(triggerPrice) <= 0
                    : price.compareTo(triggerPrice) >= 0;
            if (triggered) {
                Position pos = positions.findById(posKey(so.getUserId(), so.getTicker()));
                if (pos == null || pos.getQuantity() <= 0) { toRemove.add(so.getId()); continue; }
                SandboxUser user = users.findById(so.getUserId());
                if (user == null) { toRemove.add(so.getId()); continue; }
                ReentrantLock lock = lockFor(so.getUserId());
                lock.lock();
                try {
                    String result = trade(so.getUserId(), user.getUserName(), so.getTicker(), pos.getQuantity(), false);
                    String typeName = "SL".equals(so.getType()) ? "Стоп-лосс" : "Тейк-профит";
                    notifications.add(new String[]{so.getUserId(), "⚡ " + typeName + " сработал! " + so.getTicker() + " @ " + fmt(price) + " ₽ → " + result});
                    toRemove.add(so.getId());
                } finally {
                    lock.unlock();
                }
            }
        }
        toRemove.forEach(stopOrders::delete);
        return notifications;
    }

    public List<String[]> checkLimitOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (LimitOrder lo : limitOrders.findAll()) {
            Share share = shareByTicker.get(lo.getTicker());
            if (share == null) continue;
            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;
            BigDecimal loLimitPrice = BigDecimal.valueOf(lo.getLimitPrice());
            boolean triggered = "BUY".equals(lo.getSide())
                    ? price.compareTo(loLimitPrice) <= 0
                    : price.compareTo(loLimitPrice) >= 0;
            if (triggered) {
                ReentrantLock lock = lockFor(lo.getUserId());
                lock.lock();
                try {
                    String result = trade(lo.getUserId(), lo.getUserName(), lo.getTicker(), lo.getQty(), "BUY".equals(lo.getSide()));
                    String sideLabel = "BUY".equals(lo.getSide()) ? "покупка" : "продажа";
                    notifications.add(new String[]{lo.getUserId(), "✅ Лимитная заявка исполнена: " + sideLabel + " " + lo.getQty() + " " + lo.getTicker() + " @ " + fmt(price) + " ₽\n" + result});
                    toRemove.add(lo.getId());
                } finally {
                    lock.unlock();
                }
            }
        }
        toRemove.forEach(limitOrders::delete);
        return notifications;
    }

    public List<String[]> checkPriceAlerts() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (PriceAlert alert : priceAlerts.findAll()) {
            Share share = shareByTicker.get(alert.getTicker());
            if (share == null) continue;
            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;
            BigDecimal alertTarget = BigDecimal.valueOf(alert.getTargetPrice());
            boolean triggered = alert.isAbove()
                    ? price.compareTo(alertTarget) >= 0
                    : price.compareTo(alertTarget) <= 0;
            if (triggered) {
                notifications.add(new String[]{alert.getUserId(), "🔔 Алерт! " + alert.getTicker() + " = " + fmt(price) + " ₽ (целевая: " + fmt(alertTarget) + " ₽)"});
                toRemove.add(alert.getId());
            }
        }
        toRemove.forEach(priceAlerts::delete);
        return notifications;
    }

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

    private void rebalanceDebt(SandboxUser user, String userId) {
        BigDecimal cash = BigDecimal.valueOf(user.getCash());
        BigDecimal borrowed = BigDecimal.valueOf(user.getBorrowed());
        if (cash.compareTo(ZERO) < 0) {
            user.setBorrowed(borrowed.add(cash.abs()).doubleValue());
            user.setCash(0.0);
        } else if (borrowed.compareTo(ZERO) > 0 && cash.compareTo(ZERO) > 0) {
            BigDecimal repay = cash.min(borrowed);
            user.setCash(cash.subtract(repay).doubleValue());
            user.setBorrowed(borrowed.subtract(repay).doubleValue());
        }
        users.save(userId, user);
    }

    private boolean checkRisk(SandboxUser user, String userId) {
        BigDecimal eq = equity(userId, user);
        BigDecimal gross = grossPositionValue(userId);
        if (eq.compareTo(ZERO) <= 0) { liquidate(userId, user); return false; }
        BigDecimal lev = gross.compareTo(ZERO) <= 0 ? ZERO : gross.divide(eq, SCALE, RoundingMode.HALF_UP);
        if (lev.compareTo(maxLeverage) > 0) return false;
        BigDecimal borrowed = BigDecimal.valueOf(user.getBorrowed());
        if (borrowed.compareTo(ZERO) > 0) {
            BigDecimal marginLevel = eq.divide(borrowed, SCALE, RoundingMode.HALF_UP);
            if (marginLevel.compareTo(new BigDecimal("0.2")) < 0) {
                liquidate(userId, user);
                sendDm(userId, "🚨 Margin call / Ликвидация! Ваши позиции по " + userId + " принудительно закрыты.");
            }
        }
        return true;
    }

    private void liquidate(String userId, SandboxUser user) {
        List<Position> ps = userPositions(userId);
        BigDecimal cash = BigDecimal.valueOf(user.getCash());
        for (Position p : ps) {
            BigDecimal price = loadPriceSafe(p.getInstrumentId());
            BigDecimal avgPrice = BigDecimal.valueOf(p.getAvgPrice());
            if (price.compareTo(ZERO) <= 0) price = avgPrice;
            BigDecimal turnover = price.multiply(BigDecimal.valueOf(p.getQuantity()));
            BigDecimal fee = turnover.multiply(commissionRate).setScale(SCALE, RoundingMode.HALF_UP);
            if (fee.compareTo(ONE) < 0) fee = ONE;
            cash = cash.add(turnover).subtract(fee);
            positions.delete(posKey(userId, p.getTicker()));
            for (StopOrder so : stopOrders.findAll()) {
                if (userId.equals(so.getUserId()) && p.getTicker().equals(so.getTicker())) {
                    stopOrders.delete(so.getId());
                }
            }
        }
        user.setCash(cash.doubleValue());
        rebalanceDebt(user, userId);
        users.save(userId, user);
    }

    private void recordBaseline(SandboxUser u) {
        LocalDate now = LocalDate.now(ZONE);
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        BigDecimal eq = equity(u.getUserId(), u);
        if (u.getDailyBaselineDate() == null || !now.equals(u.getDailyBaselineDate())) {
            u.setDailyBaselineDate(now);
            u.setDailyBaselineEquity(eq.doubleValue());
        }
        if (u.getWeeklyBaselineDate() == null ||
                u.getWeeklyBaselineDate().get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
            u.setWeeklyBaselineDate(now);
            u.setWeeklyBaselineEquity(eq.doubleValue());
        }
        if (u.getMonthlyBaselineDate() == null ||
                u.getMonthlyBaselineDate().getMonthValue() != now.getMonthValue()) {
            u.setMonthlyBaselineDate(now);
            u.setMonthlyBaselineEquity(eq.doubleValue());
        }
        users.save(u.getUserId(), u);
    }

    private BigDecimal metric(SandboxUser u, String period) {
        BigDecimal eq = equity(u.getUserId(), u);
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "день" -> safeRoi(eq, BigDecimal.valueOf(u.getDailyBaselineEquity()));
            case "неделя" -> safeRoi(eq, BigDecimal.valueOf(u.getWeeklyBaselineEquity()));
            case "месяц" -> safeRoi(eq, BigDecimal.valueOf(u.getMonthlyBaselineEquity()));
            default -> safeRoi(eq, startBalance);
        };
    }

    private BigDecimal safeRoi(BigDecimal now, BigDecimal base) {
        if (base == null || base.compareTo(ZERO) <= 0) return ZERO;
        return now.subtract(base).divide(base, SCALE, RoundingMode.HALF_UP);
    }

    private List<Position> userPositions(String userId) {
        return positions.findByUserId(userId);
    }

    private BigDecimal grossPositionValue(String userId) {
        return userPositions(userId).stream()
                .map(p -> loadPriceSafe(p.getInstrumentId()).multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal equity(String userId, SandboxUser user) {
        return BigDecimal.valueOf(user.getCash())
                .add(grossPositionValue(userId))
                .subtract(BigDecimal.valueOf(user.getBorrowed()));
    }

    private BigDecimal loadPrice(String instrumentId) {
        List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
        if (prices == null || prices.isEmpty()) {
            return ZERO;
        }
        return quotationToBigDecimal(prices.getFirst().getPrice());
    }

    private BigDecimal loadPriceSafe(String instrumentId) {
        try {
            return loadPrice(instrumentId);
        } catch (Exception e) {
            log.warn("loadPriceSafe failed for {}: {}", instrumentId, e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal quotationToBigDecimal(Quotation q) {
        return BigDecimal.valueOf(q.getUnits()).add(BigDecimal.valueOf(q.getNano(), 9));
    }

    private String posKey(String userId, String ticker) {
        return userId + "::" + ticker;
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new Locale("ru", "RU"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return nf.format(value).replace('\u00A0', ' ');
    }

    private String currencySymbol(String currency) {
        if (currency == null) return "₽";
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "CNY" -> "¥";
            case "GBP" -> "£";
            default    -> "₽";
        };
    }
}
