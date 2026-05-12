package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.Share;
import services.sandbox.model.DcaOrder;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.DcaOrderRepository;
import services.sandbox.repository.SandboxUserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Runs periodically to execute due DCA orders.
 * For each due order: gets current price, computes qty = amount / price,
 * executes a market buy, then schedules the next run.
 */
@Component
public class DcaScheduler {

    private static final Logger log = LoggerFactory.getLogger(DcaScheduler.class);

    private final DcaOrderRepository dcaOrderRepository;
    private final SandboxTradingService tradingService;
    private final SandboxPriceService priceService;
    private final SandboxUserRepository userRepository;

    public DcaScheduler(DcaOrderRepository dcaOrderRepository,
                        SandboxTradingService tradingService,
                        SandboxPriceService priceService,
                        SandboxUserRepository userRepository) {
        this.dcaOrderRepository = dcaOrderRepository;
        this.tradingService = tradingService;
        this.priceService = priceService;
        this.userRepository = userRepository;
    }

    @Scheduled(fixedRate = 3_600_000)
    public void processDcaOrders() {
        try {
            List<DcaOrder> dueOrders = dcaOrderRepository.findDueOrders();
            for (DcaOrder order : dueOrders) {
                processSingle(order);
            }
        } catch (Exception e) {
            log.error("Ошибка в DCA-планировщике: {}", e.getMessage(), e);
        }
    }

    private void processSingle(DcaOrder order) {
        try {
            Map<String, Share> shareByTicker = tradingService.getShareByTicker();
            Share share = shareByTicker.get(order.getTicker());
            if (share == null) {
                log.warn("DCA: тикер {} не найден, пропускаем ордер id={}", order.getTicker(), order.getId());
                return;
            }

            BigDecimal price = priceService.loadPriceSafe(share.getUid());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("DCA: цена {} = 0, пропускаем ордер id={}", order.getTicker(), order.getId());
                return;
            }

            BigDecimal amount = BigDecimal.valueOf(order.getAmountRub());
            int qty = amount.divide(price, 0, RoundingMode.DOWN).intValue();
            if (qty <= 0) {
                log.warn("DCA: недостаточно суммы {} для покупки {} (цена {}), пропускаем id={}",
                        amount, order.getTicker(), price, order.getId());
                advanceNextExecution(order);
                return;
            }

            SandboxUser user = userRepository.findById(order.getUserId());
            if (user == null) {
                log.warn("DCA: пользователь {} не зарегистрирован, пропускаем id={}", order.getUserId(), order.getId());
                return;
            }

            String result = tradingService.buy(order.getUserId(), user.getUserName(), order.getTicker(), qty);
            log.info("DCA исполнен: user={} ticker={} qty={} result={}", order.getUserId(), order.getTicker(), qty, result);

            advanceNextExecution(order);

            tradingService.sendDm(order.getUserId(),
                    "🤖 DCA: куплено " + qty + " " + order.getTicker() + " на " + amount.toPlainString() + " ₽\n" + result);

        } catch (Exception e) {
            log.error("DCA: ошибка исполнения ордера id={}: {}", order.getId(), e.getMessage(), e);
        }
    }

    private void advanceNextExecution(DcaOrder order) {
        Instant next = "WEEKLY".equals(order.getFrequency())
                ? order.getNextExecution().plus(7, ChronoUnit.DAYS)
                : order.getNextExecution().plus(30, ChronoUnit.DAYS);
        dcaOrderRepository.updateNextExecution(order.getId(), next);
    }
}
