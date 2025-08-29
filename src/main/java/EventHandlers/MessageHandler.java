package EventHandlers;

import com.codahale.metrics.Counter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.core.InvestApi;
import services.CurrencyInfoService;
import services.HelpInfoService;
import services.SharesInfoService;
import services.StatisticsSenderService;
import utils.Constants;
import games.GameService;

import java.util.List;

@Slf4j
public class MessageHandler extends ListenerAdapter {
  private final InvestApi api;
  private final CurrencyInfoService currencyInfoService;
  private final SharesInfoService sharesInfoService;
  private final HelpInfoService helpInfoService;
  private final GameService gameService;
  private final Logger logger = LoggerFactory.getLogger("default-logger");
  private static final List<String> ALLOW_CHANNELS = List.of("основной", "криптоканал", "ботный");
  public static final Counter JOB_COPY_SUCCESS = new Counter();

  public MessageHandler(InvestApi api) {
    this.api = api;
    this.currencyInfoService = new CurrencyInfoService(api);
    this.sharesInfoService = new SharesInfoService(api);
    this.helpInfoService = new HelpInfoService(api);
    this.gameService = new GameService();
  }

  @Override
  public void onMessageReceived(@NotNull MessageReceivedEvent event) {
    runTasks(event);
    try {
      if (isBotAsking(event)) {
        String messageContent = event.getMessage().getContentDisplay();
        if (messageContent.contains("+валюта")) {
          event.getChannel().sendMessage(currencyInfoService.getCurrencyInfo(messageContent.substring(8)))
                  .submit();
        } else if (messageContent.contains("+акция")) {
          event.getChannel().sendMessage(sharesInfoService.getSharesInfo(messageContent.substring(7)))
                  .submit();
        } else if (messageContent.contains("+помощь")) {
          event.getChannel().sendMessage(helpInfoService.getHelpInfo()).submit();
        } else if (messageContent.contains("+таверна")) {
          handleTavernCommand(event, messageContent);
        } else {
          event.getChannel().sendMessage("неизвестная команда, напишите \"+помощь\" для вывода списка доступных команд").submit();
        }
      }
    } catch (Exception e) {
      logger.error(Constants.LOG_MESSAGE, e.getMessage());
    }
  }

  private void handleTavernCommand(MessageReceivedEvent event, String messageContent) {
    String[] parts = messageContent.split(" ");
    if (parts.length < 2) {
      event.getChannel().sendMessage("Использование: +таверна [команда] [параметры]\nДоступные команды: список, [название игры]").submit();
      return;
    }
    
    String subCommand = parts[1].toLowerCase();
    
    if (subCommand.equals("список")) {
      event.getChannel().sendMessage(gameService.listGames()).submit();
    } else {
      // Assume it's a game name
      String[] gameArgs = new String[parts.length - 2];
      System.arraycopy(parts, 2, gameArgs, 0, gameArgs.length);
      event.getChannel().sendMessage(gameService.playGame(subCommand, gameArgs)).submit();
    }
  }

  private boolean isBotAsking(MessageReceivedEvent event) {
    return event.getMessage().getContentDisplay().startsWith("+") && ALLOW_CHANNELS.contains(event.getChannel().asTextChannel().getName());
  }

  private void runTasks(MessageReceivedEvent event) {
      new StatisticsSenderService(api, event);
  }
}