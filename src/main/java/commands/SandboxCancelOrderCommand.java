package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+отмена-заявки ORDER_ID" command.
 */
@Component
public class SandboxCancelOrderCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду отмены лимитной заявки.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxCancelOrderCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 2 && input.startsWith("+отмена-заявки ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.cancelOrder(event.getAuthor().getId(), parts[1]);
    }
}
