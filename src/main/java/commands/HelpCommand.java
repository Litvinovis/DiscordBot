package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.HelpInfoService;

/**
 * Handles "+помощь" command.
 */
public class HelpCommand implements BotCommand {

    private final HelpInfoService helpInfoService;

    public HelpCommand(HelpInfoService helpInfoService) {
        this.helpInfoService = helpInfoService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+помощь");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        return helpInfoService.getHelpInfo();
    }
}
