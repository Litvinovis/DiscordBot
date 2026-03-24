package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxCurrencyService;

/**
 * Handles "+валюта-портфель" command — shows the user's currency holdings.
 */
public class SandboxCurrencyPortfolioCommand implements BotCommand {

    private final SandboxCurrencyService sandboxCurrencyService;

    /**
     * Создаёт команду просмотра валютного портфеля.
     *
     * @param sandboxCurrencyService сервис валютных операций песочницы
     */
    public SandboxCurrencyPortfolioCommand(SandboxCurrencyService sandboxCurrencyService) {
        this.sandboxCurrencyService = sandboxCurrencyService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+валюта-портфель");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return sandboxCurrencyService.currencyPortfolio(event.getAuthor().getId());
    }
}
