package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+купить TICKER QTY" command.
 */
@Component
public class SandboxBuyCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду рыночной покупки акций.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxBuyCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 3 && input.startsWith("+купить ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.buy(
                event.getAuthor().getId(),
                event.getAuthor().getName(),
                parts[1],
                parseInt(parts[2])
        );
    }
}
