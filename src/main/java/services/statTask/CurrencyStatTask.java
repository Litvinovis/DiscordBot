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
                    .append(String.format("%09d", Math.abs(lastPrices.get(i).getPrice().getNano())));
            // Remove trailing zeros after decimal point
            String priceStr = price.toString();
            if (priceStr.contains(".")) {
                priceStr = priceStr.replaceAll("0*$", "").replaceAll("\\.$", "");
            }
            newData.put(currencies.get(i).getName(), Double.parseDouble(priceStr));
        }
        if (oldData.isEmpty()) {
            oldData = new HashMap<>(newData);
            return null;
        } else {
            Map<String, Double> changes = new HashMap<>();
            for (Map.Entry<String, Double> e : newData.entrySet()) {
                Double oldValue = oldData.get(e.getKey());
                if (oldValue != null && oldValue != 0) {
                    // Calculate percentage change correctly
                    double change = ((e.getValue() - oldValue) / oldValue) * 100;
                    changes.put(e.getKey(), change);
                } else {
                    changes.put(e.getKey(), 0.0);
                }
            }
            Map<String, Double> sortedMap = changes.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            
            builder.append("Топ 5 лучших валют к рублю сегодня:\n");
            List<Map.Entry<String, Double>> entries = new ArrayList<>(sortedMap.entrySet());
            
            // Top 5 best performing currencies (highest positive changes)
            for (int i = entries.size() - 1; i >= Math.max(entries.size() - 5, 0); i--) {
                Map.Entry<String, Double> entry = entries.get(i);
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
            
            builder.append("\n\nТоп 5 худших валют к рублю сегодня:\n");
            // Top 5 worst performing currencies (lowest negative changes)
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                Map.Entry<String, Double> entry = entries.get(i);
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
        }
        oldData = new HashMap<>(newData);
        newData.clear();
        return builder.toString();
    }
}