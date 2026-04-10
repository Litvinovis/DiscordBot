package commands;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+тейк-профит TICKER PRICE" command.
 */
public class SandboxTakeProfitCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду установки тейк-профит ордера.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxTakeProfitCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 3 && input.startsWith("+тейк-профит ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        BigDecimal price = parseBigDecimal(parts[2]);
        if (price == null || price.signum() <= 0) {
            return "Укажите корректную цену: +тейк-профит ТИКЕР ЦЕНА";
        }
        return sandboxTradingService.setTakeProfit(event.getAuthor().getId(), parts[1], price);
    }
}
