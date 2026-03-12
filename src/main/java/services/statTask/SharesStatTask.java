package services.statTask;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

import java.util.*;
import java.util.stream.Collectors;

public class SharesStatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SharesStatTask.class);
    private final TInvestApi api;
    private final JDA jda;
    private final StringBuilder builder = new StringBuilder();
    private final Map<String, Double> oldData = new HashMap<>();
    private final Map<String, Double> newData = new HashMap<>();
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "FQBR", "A29", "A30");
    private static final int MAX_INSTRUMENTS_PER_REQUEST = 3000;
    private static final int MAX_SHARES_TO_PROCESS = 1000;
    private static final List<String> RUSSIAN_EXCHANGES = List.of("MOEX", "RTS");

    public SharesStatTask(TInvestApi api, JDA jda) {
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
            // Получаем все акции и фильтруем ненужные классы
            // getAllSharesSync() уже возвращает только акции, дополнительно фильтруем по классам
            List<Share> allShares = api.getInstrumentsService().getAllSharesSync().stream()
                    .filter(share -> checkClassCode(share.getClassCode()))
                    .limit(MAX_SHARES_TO_PROCESS) // Ограничиваем количество для производительности
                    .toList();
            
            // Разделяем акции на российские и иностранные
            List<Share> russianShares = allShares.stream()
                    .filter(this::isRussianShare)
                    .toList();
            
            logger.info("Найдено {} акций для обработки, из них {} российских", allShares.size(), russianShares.size());
            
            // Разбиваем список FIGI на части по MAX_INSTRUMENTS_PER_REQUEST
            List<List<String>> figiChunks = partitionList(
                allShares.stream().map(Share::getFigi).toList(), 
                MAX_INSTRUMENTS_PER_REQUEST
            );
            
            // Собираем цены по всем частям
            List<LastPrice> allLastPrices = new ArrayList<>();
            for (int i = 0; i < figiChunks.size(); i++) {
                List<String> chunk = figiChunks.get(i);
                logger.debug("Запрашиваем цены для части {}/{} ({} инструментов)", 
                           i + 1, figiChunks.size(), chunk.size());
                
                try {
                    List<LastPrice> chunkPrices = api.getMarketDataService().getLastPricesSync(chunk);
                    allLastPrices.addAll(chunkPrices);
                } catch (Exception e) {
                    logger.warn("Ошибка при получении цен для части {}/{}: {}", i + 1, figiChunks.size(), e.getMessage());
                    // Продолжаем с другими частями
                }
            }
            
            logger.info("Получено {} цен из {} запрошенных акций", allLastPrices.size(), allShares.size());
            
            // Заполняем новые данные для всех акций
            Map<String, LastPrice> priceMap = new HashMap<>();
            for (LastPrice price : allLastPrices) {
                priceMap.put(price.getFigi(), price);
            }
            
            int skippedCount = 0;
            Map<String, Double> allSharesData = new HashMap<>();
            Map<String, Double> russianSharesData = new HashMap<>();
            
            // Обрабатываем все акции
            for (Share share : allShares) {
                LastPrice price = priceMap.get(share.getFigi());
                
                // Пропускаем акции без цены или с нулевой ценой
                if (price == null || (price.getPrice().getUnits() == 0 && price.getPrice().getNano() == 0)) {
                    skippedCount++;
                    continue;
                }
                
                double priceValue = price.getPrice().getUnits() + 
                                   (double) price.getPrice().getNano() / 1_000_000_000;
                
                allSharesData.put(share.getName(), priceValue);
                
                // Если это российская акция, добавляем в отдельную коллекцию
                if (isRussianShare(share)) {
                    russianSharesData.put(share.getName(), priceValue);
                }
            }
            
            // Обновляем основные данные
            newData.putAll(allSharesData);
            
            logger.info("Обработано {} акций, пропущено {} из-за отсутствия данных", 
                       newData.size(), skippedCount);
            
            if (oldData.isEmpty()) {
                // First run, just store the data
                oldData.putAll(newData);
                return null;
            } else {
                // Calculate percentage changes for all shares
                Map<String, Double> allChanges = calculateChanges(allSharesData);
                Map<String, Double> russianChanges = calculateChanges(russianSharesData);
                
                // Create sorted maps
                Map<String, Double> sortedAll = sortChanges(allChanges);
                Map<String, Double> sortedRussian = sortChanges(russianChanges);
                
                // Общий зачет (топ 5 лучших и худших среди всех акций)
                builder.append("**Общий зачет - Топ 5 лучших акций:**\n");
                appendTopPerformers(sortedAll, 5, true);
                
                builder.append("\n**Общий зачет - Топ 5 худших акций:**\n");
                appendTopPerformers(sortedAll, 5, false);
                
                // Российские акции (топ 5 лучших и худших)
                if (!sortedRussian.isEmpty()) {
                    builder.append("\n\n**российские акции - Топ 5 лучших:**\n");
                    appendTopPerformers(sortedRussian, 5, true);
                    
                    builder.append("\n**российские акции - Топ 5 худших:**\n");
                    appendTopPerformers(sortedRussian, 5, false);
                } else {
                    builder.append("\n\n**российские акции:**\nНет данных для российских акций\n");
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
    
    /**
     * Разбивает список на части заданного размера
     */
    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(list.size(), i + chunkSize);
            chunks.add(list.subList(i, end));
        }
        return chunks;
    }
    
    /**
     * Проверяет, является ли акция российской
     */
    private boolean isRussianShare(Share share) {
        // Проверяем по бирже
        boolean isRussianExchange = RUSSIAN_EXCHANGES.contains(share.getExchange());
        
        // Проверяем по валюте (обычно RUB для российских акций)
        boolean isRubCurrency = "RUB".equals(share.getCurrency());
        
        // Проверяем по стране
        boolean isRuCountry = "RU".equals(share.getCountryOfRisk());
        
        return isRussianExchange || isRubCurrency || isRuCountry;
    }
    
    /**
     * Вычисляет процентные изменения цен
     */
    private Map<String, Double> calculateChanges(Map<String, Double> currentData) {
        Map<String, Double> changes = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : currentData.entrySet()) {
            String shareName = entry.getKey();
            
            if (oldData.containsKey(shareName)) {
                double oldValue = oldData.get(shareName);
                double newValue = entry.getValue();
                
                // Избегаем деления на ноль
                if (oldValue != 0) {
                    double change = ((newValue - oldValue) / oldValue) * 100;
                    changes.put(shareName, change);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Сортирует изменения по значению
     */
    private Map<String, Double> sortChanges(Map<String, Double> changes) {
        return changes.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }
    
    /**
     * Добавляет топовые результаты в билдер
     */
    private void appendTopPerformers(Map<String, Double> sortedMap, int count, boolean bestFirst) {
        List<Map.Entry<String, Double>> entryList = new ArrayList<>(sortedMap.entrySet());
        
        if (bestFirst) {
            // Лучшие первые (с начала списка)
            for (int i = 0; i < Math.min(count, entryList.size()); i++) {
                Map.Entry<String, Double> entry = entryList.get(i);
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
        } else {
            // Худшие первые (с конца списка)
            int startIndex = Math.max(0, entryList.size() - count);
            for (int i = entryList.size() - 1; i >= startIndex; i--) {
                Map.Entry<String, Double> entry = entryList.get(i);
                builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
        }
        
        if (entryList.isEmpty()) {
            builder.append("Нет данных\n");
        }
    }
}