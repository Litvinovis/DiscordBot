package commands;

import java.util.Locale;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+топ PERIOD" command.
 * Valid periods: день, неделя, месяц, все, всё
 */
public class SandboxTopCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду просмотра рейтинга участников за выбранный период.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
    public SandboxTopCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        if (parts.length < 2 || !input.startsWith("+топ ")) {
            return false;
        }
        String period = parts[1].toLowerCase(Locale.ROOT);
        return period.equals("день") || period.equals("неделя") || period.equals("месяц")
                || period.equals("все") || period.equals("всё");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        String period = parts[1].toLowerCase(Locale.ROOT);
        if (period.equals("все") || period.equals("всё")) {
            return sandboxTradingService.top("all");
        }
        return sandboxTradingService.top(period);
    }
}
