package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SandboxOrderScheduler {

	private static final Logger log = LoggerFactory.getLogger(SandboxOrderScheduler.class);

	private final SandboxOrderProcessor orderProcessor;
	private final SandboxTradingService tradingService;

	public SandboxOrderScheduler(SandboxOrderProcessor orderProcessor,
								  SandboxTradingService tradingService) {
		this.orderProcessor = orderProcessor;
		this.tradingService = tradingService;
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
		List<Notification> notifications = orderProcessor.checkStopOrders();
		for (Notification n : notifications) {
			log.info("SL/TP triggered for user {}: {}", n.userId(), n.message());
			tradingService.sendDm(n.userId(), n.message());
		}
	}

	private void processLimitOrders() {
		List<Notification> notifications = orderProcessor.checkLimitOrders();
		for (Notification n : notifications) {
			log.info("Limit order executed for user {}: {}", n.userId(), n.message());
			tradingService.sendDm(n.userId(), n.message());
		}
	}

	private void processPriceAlerts() {
		List<Notification> notifications = orderProcessor.checkPriceAlerts();
		for (Notification n : notifications) {
			log.info("Price alert triggered for user {}: {}", n.userId(), n.message());
			tradingService.sendDm(n.userId(), n.message());
		}
	}
}
