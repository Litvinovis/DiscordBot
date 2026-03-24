package services.sandbox;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background scheduler that periodically:
 *  - Checks and executes Stop Loss / Take Profit orders
 *  - Checks and executes Limit orders
 *  - Checks and fires Price alerts (via DM)
 *
 * Runs every 60 seconds.
 */
public class SandboxOrderScheduler {
    private static final Logger log = LoggerFactory.getLogger(SandboxOrderScheduler.class);
    private static final long PERIOD_SECONDS = 60L;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sandbox-order-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * Запускает планировщик, который каждые 60 секунд проверяет и исполняет
     * стоп-ордера, лимитные заявки и ценовые алерты.
     *
     * @param service сервис торговли в песочнице
     * @param jda     экземпляр JDA для отправки DM-уведомлений
     */
    public SandboxOrderScheduler(SandboxTradingService service, JDA jda) {
        // Inject JDA for DM support
        service.setJda(jda);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                processStopOrders(service);
                processLimitOrders(service);
                processPriceAlerts(service);
            } catch (Exception e) {
                log.error("Error in sandbox order scheduler: {}", e.getMessage(), e);
            }
        }, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);

        log.info("SandboxOrderScheduler started (period={}s)", PERIOD_SECONDS);
    }

    private void processStopOrders(SandboxTradingService service) {
        List<String[]> notifications = service.checkStopOrders();
        for (String[] n : notifications) {
            log.info("SL/TP triggered for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }

    private void processLimitOrders(SandboxTradingService service) {
        List<String[]> notifications = service.checkLimitOrders();
        for (String[] n : notifications) {
            log.info("Limit order executed for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }

    private void processPriceAlerts(SandboxTradingService service) {
        List<String[]> notifications = service.checkPriceAlerts();
        for (String[] n : notifications) {
            log.info("Price alert triggered for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }
}
