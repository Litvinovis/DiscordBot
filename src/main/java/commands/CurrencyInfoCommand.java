package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.CurrencyInfoService;

/**
 * Handles "+валюта TICKER" command.
 */
@Component
public class CurrencyInfoCommand implements BotCommand {

	private final CurrencyInfoService currencyInfoService;

	/**
	 * Создаёт команду с указанным сервисом получения информации о валютах.
	 *
	 * @param currencyInfoService сервис для получения данных о валютах
	 */
	public CurrencyInfoCommand(CurrencyInfoService currencyInfoService) {
		this.currencyInfoService = currencyInfoService;
	}

	@Override
	public boolean matches(String input, String[] parts) {
		return input.startsWith("+валюта ");
	}

	@Override
	public String execute(MessageReceivedEvent event, String msg, String[] parts) {
		// "+валюта " is 8 chars
		return currencyInfoService.getCurrencyInfo(msg.substring(8));
	}
}
