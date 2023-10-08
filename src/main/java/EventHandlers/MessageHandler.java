package EventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MessageHandler extends ListenerAdapter {
  private final InvestApi api;

  public MessageHandler(InvestApi api) {
    this.api = api;
  }

  @Override
  public void onMessageReceived(@NotNull MessageReceivedEvent event) {
    try {
      if (isBotAsking(event)) {
        if (event.getMessage().getContentDisplay().contains("+валюта")) {
          event.getChannel().sendMessage(getCurrencyInfo(event.getMessage().getContentDisplay().substring(8)))
                  .submit();
        } else if (event.getMessage().getContentDisplay().contains("+акция")) {
          event.getChannel().sendMessage(getSharesInfo(event.getMessage().getContentDisplay().substring(7)))
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

  public String getSharesInfo(String sharesName) {
    StringBuilder builder = new StringBuilder("Информация о подходящих акциях: \n");
    try {
      List<Share> shares = api.getInstrumentsService().getAllSharesSync().stream()
              .filter(Share::getBuyAvailableFlag)
              .filter(share -> share.getName().toLowerCase().contains(sharesName.toLowerCase()))
              .toList();
      List<LastPrice> lastPrices = api.getMarketDataService().getLastPricesSync(shares.stream().map(Share::getFigi).collect(Collectors.toList()));

      for (int i = 0; i < shares.size(); i++) {
        builder.append("\n").append("Название: ").append(shares.get(i).getName()).append("\n")
                .append("Стоимость = ").append(lastPrices.get(i).getPrice().getUnits()).append(",")
                .append(lastPrices.get(i).getPrice().getNano() > 100 ? String.valueOf(lastPrices.get(i).getPrice().getNano()).substring(0, 2) : lastPrices.get(i).getPrice().getNano())
                .append(" ").append(shares.get(i).getCurrency().toUpperCase()).append("\n");
      }
    } catch (Exception e) {
      log.trace("Ошибка {}", e.getMessage());
      return "Сорян я глючу " + e.getMessage();
    }
    return builder.toString();
  }

  private boolean isBotAsking(MessageReceivedEvent event) {
    return event.getMessage().getContentDisplay().startsWith("+");
  }
}
