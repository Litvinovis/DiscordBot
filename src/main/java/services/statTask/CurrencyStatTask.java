package services.statTask;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.*;
import java.util.stream.Collectors;

public class CurrencyStatTask implements Runnable {
    private final InvestApi api;
    private final MessageReceivedEvent event;
    private final StringBuilder builder = new StringBuilder();
    private final StringBuilder price = new StringBuilder();
    private Map<String, Double> oldData = new HashMap<>();
    private final Map<String, Double> newData = new HashMap<>();


    public CurrencyStatTask(InvestApi api, MessageReceivedEvent event) {
        this.api = api;
        this.event = event;
    }

    @Override
    public void run() {
        String message = createMessage();
        if (!Strings.isNullOrEmpty(message)) {
            event.getChannel().sendMessage(message).submit();
        }
    }

    private String createMessage() {
        builder.setLength(0);
        List<Currency> currencies = api.getInstrumentsService().getAllCurrenciesSync();
        List<String> figiList = currencies.stream().map(Currency::getFigi).toList();
        List<LastPrice> lastPrices = api.getMarketDataService().getLastPricesSync(figiList);
        for (int i = 0; i < lastPrices.size(); i++) {
            price.setLength(0);
            price.append(lastPrices.get(i).getPrice().getUnits()).append(",")
                    .append(lastPrices.get(i).getPrice().getNano());
            newData.put(currencies.get(i).getName(), Double.valueOf(price.toString()));
        }
        if (oldData.isEmpty()) {
            oldData = newData;
            return null;
        } else {
            Map<String, Double> changes = new HashMap<>();
            for (Map.Entry<String, Double> e : newData.entrySet()) {
                changes.put(e.getKey(), e.getValue() / oldData.get(e.getKey()) * 100);
            }
            Map<String, Double> sortedMap = changes.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, HashMap::new));
            builder.append("Топ 5 лучших валют к рублю сегодня:\n");
            String[] keys = (String[]) sortedMap.keySet().toArray();
            Double[] values = (Double[]) sortedMap.values().toArray();
            for (int i = 0; i < 5; i++) {
                builder.append(keys[i]).append(" : ").append(values[i]).append("%\n");
            }
            builder.append("\n\nТоп 5 худших валют к рублю сегодня:\n");
            for (int i = (keys.length - 1); i > (keys.length - 5); i--) {
                builder.append(keys[i]).append(" : ").append(values[i]).append("%\n");
            }
        }
        newData.clear();
        return builder.toString();
    }
}
