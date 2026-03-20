package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+баланс" command.
 */
public class SandboxBalanceCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxBalanceCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+баланс");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.balance(event.getAuthor().getId());
    }
}
