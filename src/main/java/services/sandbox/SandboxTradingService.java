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
import javax.cache.Cache;
import net.dv8tion.jda.api.JDA;
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

/**
 * Основной сервис торговой песочницы Stonks Bot.
 *
 * <p>Реализует интерфейсы {@link services.sandbox.api.ISandboxOrderService},
 * {@link services.sandbox.api.ISandboxPortfolioService} и
 * {@link services.sandbox.api.ISandboxRatingService}, предоставляя полный
 * цикл симуляции биржевой торговли: регистрацию, покупку/продажу,
 * лимитные заявки, стоп-ордера, ценовые алерты, рейтинг и статистику.
 *
 * <p>Все данные хранятся в Apache Ignite через {@link services.sandbox.ignite.SandboxIgniteManager}.
 * Для каждого пользователя используется персональная блокировка, исключающая
 * гонки при параллельных торговых операциях.
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
    private final IgniteCache<String, SandboxUser> users;
    private final IgniteCache<String, Position> positions;
    private final IgniteCache<String, TradeRecord> trades;
    private final IgniteCache<String, LimitOrder> limitOrders;
    private final IgniteCache<String, StopOrder> stopOrders;
    private final IgniteCache<String, PriceAlert> priceAlerts;
    private final Map<String, Share> shareByTicker;
    /** instrumentId -> ticker reverse map (for scheduler lookups) */
    private final Map<String, String> tickerByUid;
    private final BigDecimal startBalance = ConfigLoader.getSandboxStartBalance();
    private final BigDecimal commissionRate = ConfigLoader.getSandboxCommissionRate();
    private final BigDecimal maxLeverage = ConfigLoader.getSandboxMaxLeverage();
    private final BigDecimal maintenanceMargin = ConfigLoader.getSandboxMaintenanceMargin();

    /** Per-user locks to prevent race conditions on concurrent trades */
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    /** Currency service (lazy-initialised via createCurrencyService) */
    private volatile SandboxCurrencyService currencyService;

    /** JDA reference for DM notifications (set after bot is ready) */
    private volatile JDA jda;

    /**
     * Создаёт сервис торговли в песочнице: инициализирует Ignite, загружает
     * список разрешённых инструментов из T-Invest API.
     *
     * @param api клиент T-Invest API
     */
    public SandboxTradingService(TInvestApi api) {
        this.api = api;
        SandboxIgniteManager manager = new SandboxIgniteManager();
        this.igniteManager = manager;
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
                .filter(s -> allowed.contains(s.getTicker().toUpperCase()))
                .collect(Collectors.toMap(s -> s.getTicker().toUpperCase(), s -> s, (a, b) -> a));
        this.tickerByUid = shareByTicker.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().getUid(), Map.Entry::getKey));
    }

    /**
     * Возвращает менеджер Ignite для использования в health-check сервисах.
     *
     * @return менеджер Ignite-кэшей песочницы
     */
    public SandboxIgniteManager getIgniteManager() {
        return igniteManager;
    }

    /**
     * Создаёт (или возвращает ранее созданный) {@link SandboxCurrencyService},
     * разделяющий кэш пользователей и карту блокировок с данным сервисом.
     *
     * @return сервис валютных операций песочницы
     */
    public SandboxCurrencyService createCurrencyService() {
        if (currencyService == null) {
            currencyService = new SandboxCurrencyService(users, new CbrRateService(), userLocks);
        }
        return currencyService;
    }

    /**
     * Внедряет экземпляр JDA после готовности бота для отправки DM-уведомлений.
     *
     * @param jda инициализированный экземпляр JDA
     */
    public void setJda(JDA jda) {
        this.jda = jda;
    }

    // -----------------------------------------------------------------------
    // Per-user locking (race condition protection)
    // -----------------------------------------------------------------------

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Регистрирует нового участника песочницы и зачисляет стартовый баланс.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @return строка с результатом регистрации
     */
    public String register(String userId, String userName) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            SandboxUser existing = users.get(userId);
            if (existing != null) {
                return "Вы уже зарегистрированы в песочнице.";
            }
            SandboxUser user = new SandboxUser(userId, userName, startBalance.doubleValue());
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

    /**
     * Возвращает отсортированный список всех доступных для торговли тикеров.
     *
     * @return строка с перечнем тикеров через запятую
     */
    public synchronized String assets() {
        if (shareByTicker.isEmpty()) {
            return "Список активов пуст.";
        }
        return "Доступные тикеры: " + String.join(", ", new TreeSet<>(shareByTicker.keySet()));
    }

    // -----------------------------------------------------------------------
    // Buy / Sell
    // -----------------------------------------------------------------------

    /**
     * Выполняет рыночную покупку указанного количества лотов по текущей цене.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @param ticker   тикер инструмента
     * @param qty      количество лотов
     * @return строка с результатом операции
     */
    public String buy(String userId, String userName, String ticker, int qty) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            return trade(userId, userName, ticker, qty, true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Выполняет рыночную продажу указанного количества лотов по текущей цене.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @param ticker   тикер инструмента
     * @param qty      количество лотов
     * @return строка с результатом операции
     */
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
        Position posInCache = positions.get(pKey);
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

        // Read current model state as BigDecimal for precise calculation
        BigDecimal origCash = BigDecimal.valueOf(user.getCash());
        BigDecimal origBorrowed = BigDecimal.valueOf(user.getBorrowed());
        BigDecimal origTotalFees = BigDecimal.valueOf(user.getTotalFees());
        int origQty = pos.getQuantity();
        BigDecimal origAvgPrice = BigDecimal.valueOf(pos.getAvgPrice());

        if (buy) {
            BigDecimal newCash = origCash.subtract(turnover).subtract(fee);
            user.setCash(newCash.doubleValue());
            int newQty = pos.getQuantity() + qty;
            // newAvg = (avgPrice * oldQty + turnover) / newQty
            BigDecimal newAvg = origAvgPrice
                    .multiply(BigDecimal.valueOf(pos.getQuantity()))
                    .add(turnover)
                    .divide(BigDecimal.valueOf(newQty), SCALE, RoundingMode.HALF_UP);
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvg.doubleValue());
            positions.put(pKey, pos);
        } else {
            BigDecimal newCash = origCash.add(turnover).subtract(fee);
            user.setCash(newCash.doubleValue());
            pos.setQuantity(pos.getQuantity() - qty);
            if (pos.getQuantity() == 0) {
                positions.remove(pKey);
            } else {
                positions.put(pKey, pos);
            }
        }
        user.setTotalFees(origTotalFees.add(fee).doubleValue());
        rebalanceDebt(user, userId);

        if (!checkRisk(user, userId)) {
            // Rollback
            if (posInCache == null) {
                positions.remove(pKey);
            } else {
                pos.setQuantity(origQty);
                pos.setAvgPrice(origAvgPrice.doubleValue());
                positions.put(pKey, pos);
            }
            user.setCash(origCash.doubleValue());
            user.setBorrowed(origBorrowed.doubleValue());
            user.setTotalFees(origTotalFees.doubleValue());
            users.put(userId, user);
            return "❌ Сделка отклонена: превышен риск/плечо.";
        }

        users.put(userId, user);
        String tradeId = UUID.randomUUID().toString();
        trades.put(tradeId, new TradeRecord(tradeId, userId, ticker, buy ? "BUY" : "SELL", qty,
                price.doubleValue(), fee.doubleValue(), Instant.now()));
        String cur = currencySymbol(share.getCurrency());
        return (buy ? "🟢 Куплено " : "🔴 Продано ") + qty + " " + ticker + " по " + fmt(price) + " " + cur + ". Комиссия " + fmt(fee) + " " + cur;
    }

    // -----------------------------------------------------------------------
    // Portfolio
    // -----------------------------------------------------------------------

    /**
     * Возвращает содержимое портфеля с P&amp;L по каждой позиции и валютными холдингами.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированное описание портфеля
     */
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

        // Append currency holdings section
        SandboxCurrencyService ccs = createCurrencyService();
        String ccyPortfolio = ccs.currencyPortfolio(userId);
        if (ccyPortfolio != null && !ccyPortfolio.equals("Валютных позиций нет.")) {
            sb.append("\n\n").append(ccyPortfolio);
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Balance
    // -----------------------------------------------------------------------

    /**
     * Возвращает сводку баланса: рублёвый счёт, стоимость акций, заём, equity, ROI и плечо.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная строка с показателями баланса
     */
    public synchronized String balance(String userId) {
        SandboxUser user = users.get(userId);
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

        // Currency holdings line
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

    // -----------------------------------------------------------------------
    // Margin
    // -----------------------------------------------------------------------

    /**
     * Возвращает маржинальные показатели пользователя: уровень маржи, пороги и плечо.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная строка маржинальных показателей
     */
    public synchronized String margin(String userId) {
        SandboxUser user = users.get(userId);
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
        return "Margin level: " + level.setScale(2, RoundingMode.HALF_UP).toPlainString() + "\n"
                + "Порог margin call: " + maintenanceMargin.toPlainString() + "\n"
                + "Порог ликвидации: 0.20\n"
                + "Плечо: x" + lev.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + leverageStatus;
    }

    // -----------------------------------------------------------------------
    // Price
    // -----------------------------------------------------------------------

    /**
     * Возвращает текущую рыночную цену указанного тикера.
     *
     * @param ticker тикер инструмента
     * @return строка с ценой или сообщение об ошибке
     */
    public synchronized String price(String ticker) {
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

    // -----------------------------------------------------------------------
    // Top
    // -----------------------------------------------------------------------

    /**
     * Возвращает таблицу лидеров топ-5 участников за указанный период.
     *
     * @param period временной период: «день», «неделя», «месяц» или «all»
     * @return отформатированная таблица лидеров
     */
    public synchronized String top(String period) {
        List<SandboxUser> all = new ArrayList<>();
        for (Cache.Entry<String, SandboxUser> e : users) {
            all.add(e.getValue());
        }
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

    // -----------------------------------------------------------------------
    // My rank
    // -----------------------------------------------------------------------

    /**
     * Возвращает персональный рейтинг пользователя среди всех участников.
     *
     * @param userId идентификатор пользователя Discord
     * @return строка с позицией, equity и ROI пользователя
     */
    public synchronized String myRank(String userId) {
        SandboxUser target = users.get(userId);
        if (target == null) {
            return "Сначала выполните +регистрация";
        }
        List<SandboxUser> all = new ArrayList<>();
        for (Cache.Entry<String, SandboxUser> e : users) {
            all.add(e.getValue());
        }
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
        return "📊 Ваш рейтинг: #" + rank + " из " + all.size() + "\n"
                + "Equity: " + fmt(eq) + " ₽\n"
                + "ROI: " + roiSign + roi.toPlainString() + "%";
    }

    // -----------------------------------------------------------------------
    // Trade history
    // -----------------------------------------------------------------------

    /**
     * Возвращает историю последних сделок пользователя (до 20 записей), отсортированных
     * по убыванию времени.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная история сделок
     */
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

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    /**
     * Возвращает торговую статистику пользователя: win rate, средний P&amp;L, лучшая и худшая сделка.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная торговая статистика
     */
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

        return "📊 Статистика трейдинга:\n"
                + "Всего сделок: " + totalTrades + "\n"
                + "Закрытых позиций: " + realizedPnlList.size() + "\n"
                + "Win rate: " + winRate.toPlainString() + "%\n"
                + "Средний P&L: " + fmt(avgPnl) + " ₽\n"
                + "Лучшая сделка: +" + fmt(bestPnl) + " ₽\n"
                + "Худшая сделка: " + fmt(worstPnl) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Stop Loss / Take Profit
    // -----------------------------------------------------------------------

    /**
     * Устанавливает стоп-лосс ордер для открытой позиции по тикеру.
     *
     * @param userId       идентификатор пользователя Discord
     * @param ticker       тикер инструмента
     * @param triggerPrice цена срабатывания стоп-лосса
     * @return строка с подтверждением или ошибкой
     */
    public String setStopLoss(String userId, String ticker, BigDecimal triggerPrice) {
        return setStopOrder(userId, ticker, "SL", triggerPrice);
    }

    /**
     * Устанавливает тейк-профит ордер для открытой позиции по тикеру.
     *
     * @param userId       идентификатор пользователя Discord
     * @param ticker       тикер инструмента
     * @param triggerPrice цена срабатывания тейк-профита
     * @return строка с подтверждением или ошибкой
     */
    public String setTakeProfit(String userId, String ticker, BigDecimal triggerPrice) {
        return setStopOrder(userId, ticker, "TP", triggerPrice);
    }

    private String setStopOrder(String userId, String ticker, String type, BigDecimal triggerPrice) {
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
        if (triggerPrice.compareTo(ZERO) <= 0) {
            return "Цена триггера должна быть > 0";
        }
        List<String> toRemove = new ArrayList<>();
        for (Cache.Entry<String, StopOrder> e : stopOrders) {
            StopOrder so = e.getValue();
            if (userId.equals(so.getUserId()) && ticker.equals(so.getTicker()) && type.equals(so.getType())) {
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(stopOrders::remove);

        String id = UUID.randomUUID().toString();
        stopOrders.put(id, new StopOrder(id, userId, ticker, type, triggerPrice.doubleValue(), Instant.now()));
        String typeName = "SL".equals(type) ? "Стоп-лосс" : "Тейк-профит";
        return "✅ " + typeName + " на " + ticker + " установлен: " + fmt(triggerPrice) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Limit orders
    // -----------------------------------------------------------------------

    /**
     * Размещает лимитную заявку на покупку.
     *
     * @param userId     идентификатор пользователя Discord
     * @param userName   имя пользователя Discord
     * @param ticker     тикер инструмента
     * @param qty        количество лотов
     * @param limitPrice целевая цена исполнения
     * @return строка с подтверждением или ошибкой
     */
    public String placeLimitBuy(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "BUY");
    }

    /**
     * Размещает лимитную заявку на продажу.
     *
     * @param userId     идентификатор пользователя Discord
     * @param userName   имя пользователя Discord
     * @param ticker     тикер инструмента
     * @param qty        количество лотов
     * @param limitPrice целевая цена исполнения
     * @return строка с подтверждением или ошибкой
     */
    public String placeLimitSell(String userId, String userName, String ticker, int qty, BigDecimal limitPrice) {
        return placeLimitOrder(userId, userName, ticker, qty, limitPrice, "SELL");
    }

    private String placeLimitOrder(String userId, String userName, String ticker, int qty, BigDecimal limitPrice, String side) {
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
        if (limitPrice.compareTo(ZERO) <= 0) {
            return "Цена должна быть > 0";
        }
        String id = UUID.randomUUID().toString();
        limitOrders.put(id, new LimitOrder(id, userId, userName, ticker, side, qty, limitPrice.doubleValue(), Instant.now()));
        String sideLabel = "BUY".equals(side) ? "покупку" : "продажу";
        return "✅ Лимитная заявка на " + sideLabel + " " + qty + " " + ticker
                + " @ " + fmt(limitPrice) + " ₽ принята (ID: " + id.substring(0, 8) + "...)";
    }

    /**
     * Возвращает список активных лимитных заявок пользователя.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированный список заявок
     */
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
                    .append(" @ ").append(fmt(BigDecimal.valueOf(o.getLimitPrice()))).append(" ₽\n");
        }
        return sb.toString().trim();
    }

    /**
     * Отменяет лимитную заявку по идентификатору (полному или сокращённому UUID-префиксу).
     *
     * @param userId  идентификатор пользователя Discord
     * @param orderId идентификатор заявки или его префикс
     * @return строка с результатом отмены
     */
    public String cancelOrder(String userId, String orderId) {
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
                + " @ " + fmt(BigDecimal.valueOf(o.getLimitPrice())) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Price alerts
    // -----------------------------------------------------------------------

    /**
     * Устанавливает ценовой алерт: бот отправит DM, когда цена тикера достигнет цели.
     *
     * @param userId      идентификатор пользователя Discord
     * @param ticker      тикер инструмента
     * @param targetPrice целевая цена для уведомления
     * @return строка с подтверждением или ошибкой
     */
    public String setAlert(String userId, String ticker, BigDecimal targetPrice) {
        SandboxUser user = users.get(userId);
        if (user == null) {
            return "Сначала выполните +регистрация";
        }
        ticker = ticker.toUpperCase(Locale.ROOT);
        if (!shareByTicker.containsKey(ticker)) {
            return "Тикер не доступен в песочнице.";
        }
        if (targetPrice.compareTo(ZERO) <= 0) {
            return "Целевая цена должна быть > 0";
        }
        Share share = shareByTicker.get(ticker);
        BigDecimal currentPrice = loadPriceSafe(share.getUid());
        boolean above = currentPrice.compareTo(ZERO) <= 0 || targetPrice.compareTo(currentPrice) > 0;

        String id = UUID.randomUUID().toString();
        priceAlerts.put(id, new PriceAlert(id, userId, ticker, targetPrice.doubleValue(), above, Instant.now()));
        String direction = above ? "достигнет или превысит" : "упадёт до";
        return "🔔 Алерт установлен: уведомлю когда " + ticker + " " + direction + " " + fmt(targetPrice) + " ₽";
    }

    // -----------------------------------------------------------------------
    // Scheduler callbacks
    // -----------------------------------------------------------------------

    /**
     * Проверяет и исполняет стоп-ордера (SL/TP), у которых достигнута цена триггера.
     * Вызывается планировщиком {@link SandboxOrderScheduler}.
     *
     * @return список пар [userId, сообщение] для отправки DM-уведомлений
     */
    public List<String[]> checkStopOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, StopOrder> e : stopOrders) {
            StopOrder so = e.getValue();
            Share share = shareByTicker.get(so.getTicker());
            if (share == null) continue;

            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;

            BigDecimal triggerPrice = BigDecimal.valueOf(so.getTriggerPrice());
            boolean triggered;
            if ("SL".equals(so.getType())) {
                triggered = price.compareTo(triggerPrice) <= 0;
            } else { // TP
                triggered = price.compareTo(triggerPrice) >= 0;
            }

            if (triggered) {
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
     * Проверяет и исполняет лимитные заявки, у которых рыночная цена достигла лимита.
     * Вызывается планировщиком {@link SandboxOrderScheduler}.
     *
     * @return список пар [userId, сообщение] для отправки DM-уведомлений
     */
    public List<String[]> checkLimitOrders() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, LimitOrder> e : limitOrders) {
            LimitOrder lo = e.getValue();
            Share share = shareByTicker.get(lo.getTicker());
            if (share == null) continue;

            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;

            BigDecimal loLimitPrice = BigDecimal.valueOf(lo.getLimitPrice());
            boolean triggered;
            if ("BUY".equals(lo.getSide())) {
                triggered = price.compareTo(loLimitPrice) <= 0;
            } else {
                triggered = price.compareTo(loLimitPrice) >= 0;
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
     * Проверяет ценовые алерты и возвращает список сработавших уведомлений.
     * Вызывается планировщиком {@link SandboxOrderScheduler}.
     *
     * @return список пар [userId, сообщение] для отправки DM-уведомлений
     */
    public List<String[]> checkPriceAlerts() {
        List<String[]> notifications = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Cache.Entry<String, PriceAlert> e : priceAlerts) {
            PriceAlert alert = e.getValue();
            Share share = shareByTicker.get(alert.getTicker());
            if (share == null) continue;

            BigDecimal price = loadPriceSafe(share.getUid());
            if (price.compareTo(ZERO) <= 0) continue;

            BigDecimal alertTarget = BigDecimal.valueOf(alert.getTargetPrice());
            boolean triggered;
            if (alert.isAbove()) {
                triggered = price.compareTo(alertTarget) >= 0;
            } else {
                triggered = price.compareTo(alertTarget) <= 0;
            }

            if (triggered) {
                String msg = "🔔 Алерт! " + alert.getTicker() + " = " + fmt(price)
                        + " ₽ (целевая: " + fmt(alertTarget) + " ₽)";
                notifications.add(new String[]{alert.getUserId(), msg});
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(priceAlerts::remove);
        return notifications;
    }

    /**
     * Отправляет личное сообщение (DM) указанному пользователю Discord.
     *
     * @param userId  идентификатор пользователя Discord
     * @param message текст сообщения
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
        BigDecimal cash = BigDecimal.valueOf(user.getCash());
        BigDecimal borrowed = BigDecimal.valueOf(user.getBorrowed());
        if (cash.compareTo(ZERO) < 0) {
            BigDecimal abs = cash.abs();
            user.setBorrowed(borrowed.add(abs).doubleValue());
            user.setCash(0.0);
        } else if (borrowed.compareTo(ZERO) > 0 && cash.compareTo(ZERO) > 0) {
            BigDecimal repay = cash.min(borrowed);
            user.setCash(cash.subtract(repay).doubleValue());
            user.setBorrowed(borrowed.subtract(repay).doubleValue());
        }
        users.put(userId, user);
    }

    private boolean checkRisk(SandboxUser user, String userId) {
        BigDecimal eq = equity(userId, user);
        BigDecimal gross = grossPositionValue(userId);
        if (eq.compareTo(ZERO) <= 0) {
            liquidate(userId, user);
            return false;
        }
        BigDecimal lev = gross.compareTo(ZERO) <= 0
                ? ZERO
                : gross.divide(eq, SCALE, RoundingMode.HALF_UP);
        if (lev.compareTo(maxLeverage) > 0) {
            return false;
        }
        BigDecimal borrowed = BigDecimal.valueOf(user.getBorrowed());
        if (borrowed.compareTo(ZERO) > 0) {
            BigDecimal marginLevel = eq.divide(borrowed, SCALE, RoundingMode.HALF_UP);
            if (marginLevel.compareTo(new BigDecimal("0.2")) < 0) {
                liquidate(userId, user);
                String msg = "🚨 Margin call / Ликвидация! Ваши позиции по " + userId + " принудительно закрыты.";
                sendDm(userId, msg);
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
            if (price.compareTo(ZERO) <= 0) price = avgPrice; // fallback
            BigDecimal qtyBD = BigDecimal.valueOf(p.getQuantity());
            BigDecimal turnover = price.multiply(qtyBD);
            BigDecimal fee = turnover.multiply(commissionRate).setScale(SCALE, RoundingMode.HALF_UP);
            if (fee.compareTo(ONE) < 0) fee = ONE;
            cash = cash.add(turnover).subtract(fee);
            positions.remove(posKey(userId, p.getTicker()));
            List<String> soKeys = new ArrayList<>();
            for (Cache.Entry<String, StopOrder> e : stopOrders) {
                if (userId.equals(e.getValue().getUserId()) && p.getTicker().equals(e.getValue().getTicker())) {
                    soKeys.add(e.getKey());
                }
            }
            soKeys.forEach(stopOrders::remove);
        }
        user.setCash(cash.doubleValue());
        rebalanceDebt(user, userId);
        users.put(userId, user);
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
        users.put(u.getUserId(), u);
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
        List<Position> ps = new ArrayList<>();
        for (Cache.Entry<String, Position> e : positions) {
            if (userId.equals(e.getValue().getUserId())) {
                ps.add(e.getValue());
            }
        }
        return ps;
    }

    private BigDecimal grossPositionValue(String userId) {
        return userPositions(userId).stream()
                .map(p -> loadPriceSafe(p.getInstrumentId())
                        .multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal equity(String userId, SandboxUser user) {
        return BigDecimal.valueOf(user.getCash())
                .add(grossPositionValue(userId))
                .subtract(BigDecimal.valueOf(user.getBorrowed()));
    }

    /** Load price and throw if API call fails */
    private BigDecimal loadPrice(String instrumentId) {
        List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
        if (prices == null || prices.isEmpty()) {
            return ZERO;
        }
        return quotationToBigDecimal(prices.get(0).getPrice());
    }

    /** Load price safely — returns ZERO on any error */
    private BigDecimal loadPriceSafe(String instrumentId) {
        try {
            return loadPrice(instrumentId);
        } catch (Exception e) {
            log.warn("loadPriceSafe failed for {}: {}", instrumentId, e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal quotationToBigDecimal(Quotation q) {
        return BigDecimal.valueOf(q.getUnits())
                .add(BigDecimal.valueOf(q.getNano(), 9));
    }

    private String posKey(String userId, String ticker) {
        return userId + "::" + ticker;
    }

    /**
     * Format a monetary value with thousands separators and 2 decimal places.
     * Example: 1000000.5 → "1 000 000.50"
     * Uses a space as the thousands separator (Russian convention).
     */
    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new Locale("ru", "RU"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        // NumberFormat with ru_RU uses non-breaking space (\u00A0) as group separator;
        // replace with a regular space for consistent display in Discord.
        return nf.format(value).replace('\u00A0', ' ');
    }

    /**
     * Returns a human-readable currency symbol for the given ISO currency code.
     * SPB Exchange foreign stocks are denominated in USD; MOEX stocks in RUB.
     */
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
