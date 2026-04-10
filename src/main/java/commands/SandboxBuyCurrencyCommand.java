package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxCurrencyService;

import java.math.BigDecimal;

/**
 * Handles "+купить-валюту CURRENCY RUB_AMOUNT" command.
 *
 * <p>Example: {@code +купить-валюту USD 1000} — buys USD for 1000 RUB at the current CBR rate.
 */
public class SandboxBuyCurrencyCommand extends AbstractCommand {

    private final SandboxCurrencyService sandboxCurrencyService;

    /**
     * Создаёт команду покупки иностранной валюты.
     *
     * @param sandboxCurrencyService сервис валютных операций песочницы
     */
    public SandboxBuyCurrencyCommand(SandboxCurrencyService sandboxCurrencyService) {
        this.sandboxCurrencyService = sandboxCurrencyService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 3 && input.startsWith("+купить-валюту ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        BigDecimal amount = parseBigDecimal(parts[2]);
        if (amount == null) {
            return "Неверная сумма. Пример: +купить-валюту USD 1000";
        }
        return sandboxCurrencyService.buyCurrency(
                event.getAuthor().getId(),
                parts[1],
                amount
        );
    }
}
