package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+регистрация" command.
 */
public class SandboxRegisterCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    public SandboxRegisterCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+регистрация");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.register(event.getAuthor().getId(), event.getAuthor().getName());
    }
}
