package commands;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+лимит-продам TICKER QTY PRICE" command.
 */
public class SandboxLimitSellCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxLimitSellCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 4 && input.startsWith("+лимит-продам ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        int qty = parseInt(parts[2]);
        BigDecimal price = parseBigDecimal(parts[3]);
        if (qty <= 0 || price == null || price.signum() <= 0) {
            return "Использование: +лимит-продам ТИКЕР КОЛ-ВО ЦЕНА";
        }
        return sandboxTradingService.placeLimitSell(
                event.getAuthor().getId(), event.getAuthor().getName(), parts[1], qty, price);
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
}
