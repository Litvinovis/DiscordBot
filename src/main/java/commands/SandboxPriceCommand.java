package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+цена TICKER" command.
 */
@Component
public class SandboxPriceCommand implements BotCommand {

	private final SandboxTradingService sandboxTradingService;

	/**
	 * Создаёт команду запроса текущей цены тикера.
	 *
	 * @param sandboxTradingService сервис торговли в песочнице
	 */
	public SandboxPriceCommand(SandboxTradingService sandboxTradingService) {
		this.sandboxTradingService = sandboxTradingService;
	}

	@Override
	public boolean matches(String input, String[] parts) {
		return parts.length == 2 && input.startsWith("+цена ");
	}

	@Override
	public String execute(MessageReceivedEvent event, String msg, String[] parts) {
		return sandboxTradingService.price(parts[1]);
	}
}
