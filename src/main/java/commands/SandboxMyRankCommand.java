package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+мой-рейтинг" command.
 */
public class SandboxMyRankCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxMyRankCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+мой-рейтинг");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.myRank(event.getAuthor().getId());
    }
}
