package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+маржа" command.
 */
public class SandboxMarginCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxMarginCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+маржа");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.margin(event.getAuthor().getId());
    }
}
