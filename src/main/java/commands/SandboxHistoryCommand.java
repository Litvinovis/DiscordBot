package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+история [PAGE]" command.
 * Without a page number shows page 1; "+история 2" shows page 2, etc.
 */
@Component
public class SandboxHistoryCommand extends AbstractCommand {

	private final SandboxTradingService sandboxTradingService;

	/**
	 * Создаёт команду просмотра истории сделок с поддержкой постраничного вывода.
	 *
	 * @param sandboxTradingService сервис торговли в песочнице
	 */
	public SandboxHistoryCommand(SandboxTradingService sandboxTradingService) {
		this.sandboxTradingService = sandboxTradingService;
	}

	@Override
	public boolean matches(String input, String[] parts) {
		if (input.equals("+история")) return true;
		return parts.length == 2 && input.startsWith("+история ");
	}

	@Override
	public String execute(MessageReceivedEvent event, String msg, String[] parts) {
		int page = 1;
		if (parts.length == 2) {
			int parsed = parseInt(parts[1]);
			if (parsed > 0) page = parsed;
		}
		return sandboxTradingService.history(event.getAuthor().getId(), page);
	}
}
