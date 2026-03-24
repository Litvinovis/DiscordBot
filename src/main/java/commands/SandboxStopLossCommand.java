package commands;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+стоп-лосс TICKER PRICE" command.
 */
public class SandboxStopLossCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду установки стоп-лосс ордера.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxStopLossCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 3 && input.startsWith("+стоп-лосс ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        BigDecimal price = parseBigDecimal(parts[2]);
        if (price == null || price.signum() <= 0) {
            return "Укажите корректную цену: +стоп-лосс ТИКЕР ЦЕНА";
        }
        return sandboxTradingService.setStopLoss(event.getAuthor().getId(), parts[1], price);
    }

    private BigDecimal parseBigDecimal(String s) {
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
}
