package services.statTask;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.core.InvestApi;
import utils.ConfigLoader;

import java.util.*;
import java.util.stream.Collectors;

public class SharesStatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SharesStatTask.class);
    private final InvestApi api;
    private final JDA jda;
    private final StringBuilder builder = new StringBuilder();
    private final Map<String, Double> oldData = new HashMap<>();
    private final Map<String, Double> newData = new HashMap<>();
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "FQBR", "A29", "A30");

    public SharesStatTask(InvestApi api, JDA jda) {
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
            logger.error("Ошибка при выполнении задачи отчета по акциям", e);
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
            logger.info("Отчет по акциям успешно отправлен в канал {} на сервере {}", channelName, guild.getName());
        } catch (Exception e) {
            logger.error("Ошибка при отправке отчета по акциям", e);
        }
    }

    private String createMessage() {
        builder.setLength(0);
        try {
            // Get all shares
            List<Share> shares = api.getInstrumentsService().getAllSharesSync().stream()
                    .filter(share -> checkClassCode(share.getClassCode()))
                    .toList();
            
            List<String> figiList = shares.stream().map(Share::getFigi).toList();
            List<LastPrice> lastPrices = api.getMarketDataService().getLastPricesSync(figiList);
            
            // Populate new data
            for (int i = 0; i < shares.size() && i < lastPrices.size(); i++) {
                Share share = shares.get(i);
                LastPrice price = lastPrices.get(i);
                
                // Skip shares with zero price
                if (price.getPrice().getUnits() == 0 && price.getPrice().getNano() == 0) {
                    continue;
                }
                
                double priceValue = price.getPrice().getUnits() + 
                                   (double) price.getPrice().getNano() / 1_000_000_000;
                newData.put(share.getName(), priceValue);
            }
            
            if (oldData.isEmpty()) {
                // First run, just store the data
                oldData.putAll(newData);
                return null;
            } else {
                // Calculate percentage changes
                Map<String, Double> changes = new HashMap<>();
                for (Map.Entry<String, Double> entry : newData.entrySet()) {
                    String shareName = entry.getKey();
                    
                    if (oldData.containsKey(shareName)) {
                        double oldValue = oldData.get(shareName);
                        double newValue = entry.getValue();
                        
                        // Avoid division by zero
                        if (oldValue != 0) {
                            double change = ((newValue - oldValue) / oldValue) * 100;
                            changes.put(shareName, change);
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
                
                builder.append("Топ 5 лучших акций сегодня:\n");
                List<Map.Entry<String, Double>> entryList = new ArrayList<>(sortedMap.entrySet());
                
                // Top 5 best performers
                int count = 0;
                for (Map.Entry<String, Double> entry : entryList) {
                    if (count >= 5) break;
                    builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
                    count++;
                }
                
                builder.append("\nТоп 5 худших акций сегодня:\n");
                
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
            oldData.clear();
            oldData.putAll(newData);
            newData.clear();
        } catch (Exception e) {
            logger.error("Ошибка при создании сообщения с отчетом по акциям", e);
            return "Ошибка при формировании отчета по акциям: " + e.getMessage();
        }
        
        return builder.toString();
    }
    
    private boolean checkClassCode(String classCode) {
        return !badCode.contains(classCode);
    }
}