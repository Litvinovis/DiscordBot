package services.statTask;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

import java.util.*;
import java.util.stream.Collectors;

public class CurrencyStatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyStatTask.class);
    private final TInvestApi api;
    private final JDA jda;
    private final StringBuilder builder = new StringBuilder();
    private final StringBuilder price = new StringBuilder();
    private Map<String, Double> oldData = new HashMap<>();
    private final Map<String, Double> newData = new HashMap<>();

    public CurrencyStatTask(TInvestApi api, JDA jda) {
        this.api = api;
        this.jda = jda;
    }

    @Override
    public void run() {
        try {
            String message = createMessage();
            if (!Strings.isNullOrEmpty(message)) {
                sendReport(message);
            }
        } catch (Exception e) {
            logger.error("Ошибка при выполнении задачи валютного отчета", e);
        }
    }

    private void sendReport(String message) {
        String guildId = ConfigLoader.getReportGuildId();
        String channelName = ConfigLoader.getReportChannelName();
        
        if (Strings.isNullOrEmpty(guildId) || Strings.isNullOrEmpty(channelName)) {
            logger.error("Не заданы параметры guildId или channelName для отправки отчета");
            return;
        }
        
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                logger.error("Не найден сервер с ID: {}", guildId);
                return;
            }
            
            TextChannel channel = guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
            if (channel == null) {
                logger.error("Не найден канал с именем: {} на сервере {}", channelName, guild.getName());
                return;
            }
            
            channel.sendMessage(message).submit();
            logger.info("Отчет по валютам успешно отправлен в канал {} на сервере {}", channelName, guild.getName());
        } catch (Exception e) {
            logger.error("Ошибка при отправке отчета по валютам", e);
        }
    }

    private String createMessage() {
        builder.setLength(0);
        try {
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
                return null; // First run, no data to compare
            } else {
                Map<String, Double> changes = new HashMap<>();
                for (Map.Entry<String, Double> e : newData.entrySet()) {
                    String currencyName = e.getKey();
                    // Skip comparing ruble with itself
                    if ("Российский рубль".equals(currencyName)) {
                        continue;
                    }
                    
                    // Calculate percentage change correctly
                    if (oldData.containsKey(currencyName)) {
                        double oldValue = oldData.get(currencyName);
                        double newValue = e.getValue();
                        // Avoid division by zero
                        if (oldValue != 0) {
                            double change = ((newValue - oldValue) / oldValue) * 100;
                            changes.put(currencyName, change);
                        }
                    }
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
                
                builder.append("\nТоп 5 худших валют к рублю сегодня:\n");
                
                // Bottom 5 worst performers (reverse order)
                count = 0;
                for (int i = entryList.size() - 1; i >= 0; i--) {
                    if (count >= 5) break;
                    Map.Entry<String, Double> entry = entryList.get(i);
                    builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
                    count++;
                }
            }
            
            // Update oldData for next comparison
            oldData = new HashMap<>(newData);
            newData.clear();
        } catch (Exception e) {
            logger.error("Ошибка при создании сообщения с отчетом по валютам", e);
            return "Ошибка при формировании отчета по валютам: " + e.getMessage();
        }
        
        return builder.toString();
    }
}