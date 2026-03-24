package commands;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+лимит-куплю TICKER QTY PRICE" command.
 */
public class SandboxLimitBuyCommand implements BotCommand {

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
