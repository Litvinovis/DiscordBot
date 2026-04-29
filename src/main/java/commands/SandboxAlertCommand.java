package commands;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+алерт TICKER PRICE" command.
 */
@Component
public class SandboxAlertCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду установки ценового алерта.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxAlertCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 3 && input.startsWith("+алерт ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        BigDecimal price = parseBigDecimal(parts[2]);
        if (price == null || price.signum() <= 0) {
            return "Использование: +алерт ТИКЕР ЦЕНА";
        }
        return sandboxTradingService.setAlert(event.getAuthor().getId(), parts[1], price);
    }
}
