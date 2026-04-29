package services.sandbox;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SandboxOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SandboxOrderScheduler.class);

    private final SandboxTradingService service;
    private final JDA jda;

    public SandboxOrderScheduler(SandboxTradingService service, JDA jda) {
        this.service = service;
        this.jda = jda;
    }

    @Scheduled(fixedRate = 60_000)
    public void processOrders() {
        try {
            processStopOrders();
            processLimitOrders();
            processPriceAlerts();
        } catch (Exception e) {
            log.error("Ошибка в планировщике заявок: {}", e.getMessage(), e);
        }
    }

    private void processStopOrders() {
        List<String[]> notifications = service.checkStopOrders();
        for (String[] n : notifications) {
            log.info("SL/TP triggered for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }

    private void processLimitOrders() {
        List<String[]> notifications = service.checkLimitOrders();
        for (String[] n : notifications) {
            log.info("Limit order executed for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }

    private void processPriceAlerts() {
        List<String[]> notifications = service.checkPriceAlerts();
        for (String[] n : notifications) {
            log.info("Price alert triggered for user {}: {}", n[0], n[1]);
            service.sendDm(n[0], n[1]);
        }
    }
}
