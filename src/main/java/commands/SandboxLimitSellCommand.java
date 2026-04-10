package commands;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+лимит-продам TICKER QTY PRICE" command.
 */
public class SandboxLimitSellCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду размещения лимитной заявки на продажу.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
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
}
