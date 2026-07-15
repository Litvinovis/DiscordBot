package events;

import com.discord.stonks.config.DiscordProperties;
import commands.BotCommand;
import lombok.Generated;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Обработчик входящих сообщений Discord.
 * Фильтрует по разрешённым каналам и делегирует подходящей команде.
 */
@Component
public class MessageHandler extends ListenerAdapter {

	@Generated
	private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
	private final Logger logger = LoggerFactory.getLogger("default-logger");
	private static final String OPENCLAW_ROVER_BOT_ID = "1481319611533365483";

	private final Set<String> allowedChannelIds;
	private final List<BotCommand> commands;
	// Команды ходят в Tinkoff API/ЦБ — выносим с event-потока JDA, чтобы не блокировать gateway
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public MessageHandler(DiscordProperties discordProperties, List<BotCommand> commands) {
		this.allowedChannelIds = Set.copyOf(discordProperties.allowedChannelIds());
		this.commands = commands;
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (!isBotAsking(event)) return;
		executor.submit(() -> process(event));
	}

	private void process(MessageReceivedEvent event) {
		String msg = event.getMessage().getContentDisplay().trim();
		try {
			String response = handle(msg, event);
			if (response == null || response.isBlank()) {
				// Команда вернула пустой ответ — пользователь остался без реакции
				logger.warn("Команда '{}' вернула пустой ответ (пользователь {})",
						msg, event.getAuthor().getId());
				return;
			}
			event.getChannel().sendMessage(response).submit();
		} catch (Exception e) {
			// Раньше терялся стектрейс главного обработчика команд
			logger.error("Ошибка обработки команды '{}' (пользователь {})",
					msg, event.getAuthor().getId(), e);
		}
	}

	private String handle(String msg, MessageReceivedEvent event) {
		String lower = msg.toLowerCase(Locale.ROOT);
		String[] parts = msg.split("\\s+");
		for (BotCommand command : commands) {
			if (command.matches(lower, parts)) {
				return command.execute(event, msg, parts);
			}
		}
		return "неизвестная команда, напишите '+помощь'";
	}

	private boolean isBotAsking(MessageReceivedEvent event) {
		// Сообщения ботов игнорируем, кроме доверенного OpenClaw Rover
		if (event.getAuthor().isBot() && !OPENCLAW_ROVER_BOT_ID.equals(event.getAuthor().getId())) {
			return false;
		}
		return event.getMessage().getContentDisplay().startsWith("+")
				&& allowedChannelIds.contains(event.getChannel().getId());
	}
}
