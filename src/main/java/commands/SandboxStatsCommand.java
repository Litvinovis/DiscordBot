package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+стата" / "+статистика" commands.
 */
public class SandboxStatsCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду просмотра торговой статистики пользователя.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxStatsCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+стата") || input.equals("+статистика");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxTradingService.stats(event.getAuthor().getId());
    }
}
