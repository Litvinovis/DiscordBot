package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+мои-заявки" command.
 */
@Component
public class SandboxMyOrdersCommand implements BotCommand {

    private final SandboxTradingService sandboxTradingService;

    /**
     * Создаёт команду просмотра активных заявок пользователя.
     *
     * @param sandboxTradingService сервис торговли в песочнице
     */
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
