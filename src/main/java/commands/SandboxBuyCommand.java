package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+купить TICKER QTY" command.
 */
public class SandboxBuyCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

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

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }
}
