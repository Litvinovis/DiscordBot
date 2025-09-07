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

import java.util.List;

@Slf4j
public class MessageHandler extends ListenerAdapter {
  private final InvestApi api;
  private final CurrencyInfoService currencyInfoService;
  private final SharesInfoService sharesInfoService;
  private final HelpInfoService helpInfoService;
  private final Logger logger = LoggerFactory.getLogger("default-logger");
  private static final List<String> ALLOW_CHANNELS = List.of("основной", "криптоканал", "ботный");
  public static final Counter JOB_COPY_SUCCESS = new Counter();

  public MessageHandler(InvestApi api) {
    this.api = api;
    this.currencyInfoService = new CurrencyInfoService(api);
    this.sharesInfoService = new SharesInfoService(api);
    this.helpInfoService = new HelpInfoService(api);
  }

  @Override
  public void onMessageReceived(@NotNull MessageReceivedEvent event) {
    runTasks(event);
    try {
      if (isBotAsking(event)) {
        if (event.getMessage().getContentDisplay().contains("+валюта")) {
          event.getChannel().sendMessage(currencyInfoService.getCurrencyInfo(event.getMessage().getContentDisplay().substring(8)))
                  .submit();
        } else if (event.getMessage().getContentDisplay().contains("+акция")) {
          event.getChannel().sendMessage(sharesInfoService.getSharesInfo(event.getMessage().getContentDisplay().substring(7)))
                  .submit();
        } else if (event.getMessage().getContentDisplay().contains("+помощь")) {
          event.getChannel().sendMessage(helpInfoService.getHelpInfo()).submit();
        } else {
          event.getChannel().sendMessage("неизвестная команда, напишите \"+помощь\" для вывода списка доступных команд").submit();
        }
      }
    } catch (Exception e) {
      logger.error(Constants.LOG_MESSAGE, e.getMessage());
    }
  }

  private boolean isBotAsking(MessageReceivedEvent event) {
    return event.getMessage().getContentDisplay().startsWith("+") && ALLOW_CHANNELS.contains(event.getChannel().asTextChannel().getName());
  }

  private void runTasks(MessageReceivedEvent event) {
      new StatisticsSenderService(api, event);
  }
}
