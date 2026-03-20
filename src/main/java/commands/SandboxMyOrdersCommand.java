package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+мои-заявки" command.
 */
public class SandboxMyOrdersCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxMyOrdersCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+мои-заявки");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.myOrders(event.getAuthor().getId());
    }
}
