package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+активы" command.
 */
public class SandboxAssetsCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду просмотра списка доступных активов.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxAssetsCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+активы");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.assets();
    }
}
