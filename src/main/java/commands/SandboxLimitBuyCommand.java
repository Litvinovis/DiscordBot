package commands;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+лимит-куплю TICKER QTY PRICE" command.
 */
@Component
public class SandboxLimitBuyCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду размещения лимитной заявки на покупку.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxLimitBuyCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 4 && input.startsWith("+лимит-куплю ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        int qty = parseInt(parts[2]);
        BigDecimal price = parseBigDecimal(parts[3]);
        if (qty <= 0 || price == null || price.signum() <= 0) {
            return "Использование: +лимит-куплю ТИКЕР КОЛ-ВО ЦЕНА";
        }
        return sandboxTradingService.placeLimitBuy(
                event.getAuthor().getId(), event.getAuthor().getName(), parts[1], qty, price);
    }
}
