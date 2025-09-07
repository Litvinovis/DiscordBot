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
            price.append(lastPrices.get(i).getPrice().getUnits()).append(".")
                    .append(String.format("%09d", lastPrices.get(i).getPrice().getNano()));
            newData.put(currencies.get(i).getName(), Double.parseDouble(price.toString()));
        }
        if (oldData.isEmpty()) {
            oldData = new HashMap<>(newData);
            return null;
        } else {
            Map<String, Double> changes = new HashMap<>();
            for (Map.Entry<String, Double> e : newData.entrySet()) {
                // Calculate percentage change correctly
                double oldValue = oldData.get(e.getKey());
                double newValue = e.getValue();
                double change = ((newValue - oldValue) / oldValue) * 100;
                changes.put(e.getKey(), change);
            }
            // Sort by value (change percentage)
            Map<String, Double> sortedMap = changes.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            
            builder.append("Топ 5 лучших валют к рублю сегодня:\n");
            List<Map.Entry<String, Double>> entryList = new ArrayList<>(sortedMap.entrySet());
            
            // Top 5 best performers
            int count = 0;
            for (Map.Entry<String, Double> entry : entryList) {
                if (count >= 5) break;
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
                count++;
            }
            
            builder.append("\n\nТоп 5 худших валют к рублю сегодня:\n");
            
            // Bottom 5 worst performers (reverse order)
            count = 0;
            for (int i = entryList.size() - 1; i >= 0; i--) {
                if (count >= 5) break;
                Map.Entry<String, Double> entry = entryList.get(i);
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
                count++;
            }
        }
        newData.clear();
        return builder.toString();
    }
}