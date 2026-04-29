package commands;

import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.SharesInfoService;

/**
 * Handles "+акция TICKER" command.
 */
@Component
public class SharesInfoCommand implements BotCommand {

    private final SharesInfoService sharesInfoService;

    /**
     * Создаёт команду запроса информации об акции.
     *
     * @param sharesInfoService сервис для получения данных об акциях
     */
    public SharesInfoCommand(SharesInfoService sharesInfoService) {
        this.sharesInfoService = sharesInfoService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.startsWith("+акция ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        // "+акция " is 7 chars
        return sharesInfoService.getSharesInfo(msg.substring(7));
    }
}
