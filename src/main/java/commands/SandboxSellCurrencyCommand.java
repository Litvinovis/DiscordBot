package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxCurrencyService;

import java.math.BigDecimal;

/**
 * Handles "+продать-валюту CURRENCY AMOUNT" command.
 *
 * <p>Example: {@code +продать-валюту USD 500} — sells 500 USD back to RUB at the current CBR rate.
 */
@Component
public class SandboxSellCurrencyCommand extends AbstractCommand {

	private final SandboxCurrencyService sandboxCurrencyService;

	/**
	 * Создаёт команду продажи иностранной валюты за рубли.
	 *
	 * @param sandboxCurrencyService сервис валютных операций песочницы
	 */
	public SandboxSellCurrencyCommand(SandboxCurrencyService sandboxCurrencyService) {
		this.sandboxCurrencyService = sandboxCurrencyService;
	}

	@Override
	public boolean matches(String input, String[] parts) {
		return parts.length == 3 && input.startsWith("+продать-валюту ");
	}

	@Override
	public String execute(MessageReceivedEvent event, String msg, String[] parts) {
		BigDecimal amount = parseBigDecimal(parts[2]);
		if (amount == null) {
			return "Неверная сумма. Пример: +продать-валюту USD 500";
		}
		return sandboxCurrencyService.sellCurrency(
				event.getAuthor().getId(),
				parts[1],
				amount
		);
	}
}
