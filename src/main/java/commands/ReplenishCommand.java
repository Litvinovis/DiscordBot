package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+пополнить AMOUNT" command — adds virtual cash once per 30 days (max 200 000 ₽).
 */
@Component
public class ReplenishCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    public ReplenishCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.startsWith("+пополнить ") && parts.length == 2;
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        double amount;
        try {
            amount = Double.parseDouble(parts[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            return "❌ Укажите сумму числом. Пример: `+пополнить 50000`";
        }
        return sandboxTradingService.replenish(
            event.getAuthor().getId(),
            event.getAuthor().getName(),
            amount
        );
    }
}
