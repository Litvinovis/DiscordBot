package EventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.List;

@Slf4j
public class MessageHandler extends ListenerAdapter {
  private final InvestApi api;

  public MessageHandler(InvestApi api) {
    this.api = api;
  }

  @Override
  public void onMessageReceived(@NotNull MessageReceivedEvent event) {
    try {
      if (event.getChannel().getName().equals("ботный")) {
        if (event.getAuthor().getName().equals("l4rover") && event.getMessage().getContentDisplay().contains("+валюта")) {
          event.getChannel().sendMessage(getCurrencyInfo(event.getMessage().getContentDisplay().substring(8)))
                  .submit();
        }
      }
    } catch (Exception e) {
      log.error(Constants.LOG_MESSAGE, e.getMessage());
    }
  }

  public String getCurrencyInfo(String currency) {
    StringBuilder builder = new StringBuilder("Информация о валюте ").append(currency).append(":\n");
    try {
      List<Currency> currencies = api.getInstrumentsService().getAllCurrencies().get();
      List<String> filtered = currencies.stream()
              .filter(currency1 -> currency1.getIsoCurrencyName().equalsIgnoreCase(currency))
              .map(Currency::getFigi)
              .toList();
      var lastPrices = api.getMarketDataService().getLastPricesSync(filtered);
      builder.append("Курс = ")
              .append(lastPrices.get(0).getPrice().getUnits())
              .append(",")
              .append(String.valueOf(lastPrices.get(0).getPrice().getNano()), 0, 2)
              .append(" рублей за 1 ")
              .append(currency);
    } catch (Exception e) {
      log.trace("Ошибка {}", e.getMessage());
      return "Сорян я глючу";
    }
    return builder.toString();
  }
}
