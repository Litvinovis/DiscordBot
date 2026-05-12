package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import services.sandbox.SandboxTradingService;
import services.sandbox.model.DcaOrder;
import services.sandbox.repository.DcaOrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Handles "+дка TICKER СУММА [еженедельно|ежемесячно]" — create or update a DCA order.
 */
@Component
public class DcaCommand extends AbstractCommand {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("100");

    private final DcaOrderRepository dcaOrderRepository;
    private final SandboxTradingService tradingService;

    public DcaCommand(DcaOrderRepository dcaOrderRepository, SandboxTradingService tradingService) {
        this.dcaOrderRepository = dcaOrderRepository;
        this.tradingService = tradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length >= 3 && input.startsWith("+дка ")
                && !input.startsWith("+дка-");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        String ticker = parts[1].toUpperCase(Locale.ROOT);
        BigDecimal amount = parseBigDecimal(parts[2]);

        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
            return "❌ Минимальная сумма DCA: 100 ₽.";
        }

        if (!tradingService.getShareByTicker().containsKey(ticker)) {
            return "❌ Тикер " + ticker + " не доступен в песочнице.";
        }

        String frequency = "WEEKLY";
        if (parts.length >= 4) {
            String freq = parts[3].toLowerCase(Locale.ROOT);
            if (freq.equals("ежемесячно")) {
                frequency = "MONTHLY";
            } else if (!freq.equals("еженедельно")) {
                return "❌ Частота должна быть 'еженедельно' или 'ежемесячно'.";
            }
        }

        Instant now = Instant.now();
        Instant nextExecution = frequency.equals("WEEKLY")
                ? now.plus(7, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);

        DcaOrder order = new DcaOrder(
                event.getAuthor().getId(),
                ticker,
                amount.doubleValue(),
                frequency,
                nextExecution,
                now,
                true
        );
        dcaOrderRepository.upsert(order);

        String freqLabel = frequency.equals("WEEKLY") ? "еженедельно" : "ежемесячно";
        return "✅ DCA для " + ticker + " настроен: " + amount.toPlainString() + " ₽ " + freqLabel + ".";
    }
}
