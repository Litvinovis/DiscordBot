package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Interface for all bot commands.
 * Each command encapsulates a single bot action triggered by user input.
 */
public interface BotCommand {

    /**
     * Returns true if this command should handle the given user input.
     *
     * @param input the trimmed message content in lowercase
     * @param parts the message split by whitespace
     * @return true if this command matches
     */
    boolean matches(String input, String[] parts);

    /**
     * Execute this command and return the response string.
     *
     * @param event the original Discord message event
     * @param msg   the original (not lowercased) message content, trimmed
     * @param parts the message split by whitespace (original case)
     * @return the response string to send back to the channel
     */
    String execute(MessageReceivedEvent event, String msg, String[] parts);
}
