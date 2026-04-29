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

/**
 * Обработчик входящих сообщений Discord.
 * Фильтрует по разрешённым каналам и делегирует подходящей команде.
 */
@Component
public class MessageHandler extends ListenerAdapter {

    @Generated
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final Logger logger = LoggerFactory.getLogger("default-logger");

    private final Set<String> allowedChannelIds;
    private final List<BotCommand> commands;

    public MessageHandler(DiscordProperties discordProperties, List<BotCommand> commands) {
        this.allowedChannelIds = Set.copyOf(discordProperties.allowedChannelIds());
        this.commands = commands;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            if (!isBotAsking(event)) return;
            String msg = event.getMessage().getContentDisplay().trim();
            String response = handle(msg, event);
            event.getChannel().sendMessage(response).submit();
        } catch (Exception e) {
            logger.error("Ошибка: {}", e.getMessage());
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
        return event.getMessage().getContentDisplay().startsWith("+")
                && allowedChannelIds.contains(event.getChannel().getId());
    }
}
