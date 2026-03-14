/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Strings
 *  net.dv8tion.jda.api.JDA
 *  net.dv8tion.jda.api.entities.Guild
 *  net.dv8tion.jda.api.entities.channel.concrete.TextChannel
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  ru.tinkoff.piapi.contract.v1.LastPrice
 *  ru.tinkoff.piapi.contract.v1.Share
 */
package services.statTask;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class SharesStatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SharesStatTask.class);
    private final TInvestApi api;
    private final JDA jda;
    private final StringBuilder builder = new StringBuilder();
    private final Map<String, Double> oldData = new HashMap<String, Double>();
    private final Map<String, Double> newData = new HashMap<String, Double>();
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
            String message = this.createMessage();
            if (!Strings.isNullOrEmpty(message)) {
                this.sendReport(message);
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
            Guild guild = this.jda.getGuildById(guildId);
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
        this.builder.setLength(0);
        try {
            List<Share> allShares = this.api.getInstrumentsService().getAllSharesSync().stream()
                .filter(share -> this.checkClassCode(share.getClassCode()))
                .limit(1000L)
                .toList();
            List<Share> russianShares = allShares.stream().filter(this::isRussianShare).toList();
            logger.info("Найдено {} акций для обработки, из них {} российских", allShares.size(), russianShares.size());
            List<List<String>> figiChunks = this.partitionList(allShares.stream().map(Share::getFigi).toList(), 3000);
            ArrayList<LastPrice> allLastPrices = new ArrayList<LastPrice>();
            for (int i = 0; i < figiChunks.size(); ++i) {
                List<String> chunk = figiChunks.get(i);
                logger.debug("Запрашиваем цены для части {}/{} ({} инструментов)", i + 1, figiChunks.size(), chunk.size());
                try {
                    List<LastPrice> chunkPrices = this.api.getMarketDataService().getLastPricesSync(chunk);
                    allLastPrices.addAll(chunkPrices);
                } catch (Exception e) {
                    logger.warn("Ошибка при получении цен для части {}/{}: {}", i + 1, figiChunks.size(), e.getMessage());
                }
            }
            logger.info("Получено {} цен из {} запрошенных акций", allLastPrices.size(), allShares.size());
            HashMap<String, LastPrice> priceMap = new HashMap<String, LastPrice>();
            for (LastPrice price : allLastPrices) {
                priceMap.put(price.getFigi(), price);
            }
            int skippedCount = 0;
            HashMap<String, Double> allSharesData = new HashMap<String, Double>();
            HashMap<String, Double> russianSharesData = new HashMap<String, Double>();
            for (Share share2 : allShares) {
                LastPrice price = priceMap.get(share2.getFigi());
                if (price == null || price.getPrice().getUnits() == 0L && price.getPrice().getNano() == 0) {
                    ++skippedCount;
                    continue;
                }
                double priceValue = (double) price.getPrice().getUnits() + (double) price.getPrice().getNano() / 1.0E9;
                allSharesData.put(share2.getName(), priceValue);
                if (!this.isRussianShare(share2)) continue;
                russianSharesData.put(share2.getName(), priceValue);
            }
            this.newData.putAll(allSharesData);
            logger.info("Обработано {} акций, пропущено {} из-за отсутствия данных", this.newData.size(), skippedCount);
            if (this.oldData.isEmpty()) {
                this.oldData.putAll(this.newData);
                return null;
            }
            Map<String, Double> allChanges = this.calculateChanges(allSharesData);
            Map<String, Double> russianChanges = this.calculateChanges(russianSharesData);
            Map<String, Double> sortedAll = this.sortChanges(allChanges);
            Map<String, Double> sortedRussian = this.sortChanges(russianChanges);
            this.builder.append("**Общий зачет - Топ 5 лучших акций:**\n");
            this.appendTopPerformers(sortedAll, 5, true);
            this.builder.append("\n**Общий зачет - Топ 5 худших акций:**\n");
            this.appendTopPerformers(sortedAll, 5, false);
            if (!sortedRussian.isEmpty()) {
                this.builder.append("\n\n**Российские акции - Топ 5 лучших:**\n");
                this.appendTopPerformers(sortedRussian, 5, true);
                this.builder.append("\n**Российские акции - Топ 5 худших:**\n");
                this.appendTopPerformers(sortedRussian, 5, false);
            } else {
                this.builder.append("\n\n**Российские акции:**\nНет данных для российских акций\n");
            }
            this.oldData.clear();
            this.oldData.putAll(this.newData);
            this.newData.clear();
        } catch (Exception e) {
            logger.error("Ошибка при формировании отчета по акциям", e);
            return "Ошибка при формировании отчета по акциям: " + e.getMessage();
        }
        return this.builder.toString();
    }

    private boolean checkClassCode(String classCode) {
        return !this.badCode.contains(classCode);
    }

    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        ArrayList<List<T>> chunks = new ArrayList<List<T>>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(list.size(), i + chunkSize);
            chunks.add(list.subList(i, end));
        }
        return chunks;
    }

    private boolean isRussianShare(Share share) {
        boolean isRussianExchange = RUSSIAN_EXCHANGES.contains(share.getExchange());
        boolean isRubCurrency = "RUB".equals(share.getCurrency());
        boolean isRuCountry = "RU".equals(share.getCountryOfRisk());
        return isRussianExchange || isRubCurrency || isRuCountry;
    }

    private Map<String, Double> calculateChanges(Map<String, Double> currentData) {
        HashMap<String, Double> changes = new HashMap<String, Double>();
        for (Map.Entry<String, Double> entry : currentData.entrySet()) {
            String shareName = entry.getKey();
            if (!this.oldData.containsKey(shareName)) continue;
            double oldValue = this.oldData.get(shareName);
            double newValue = entry.getValue();
            if (oldValue == 0.0) continue;
            double change = (newValue - oldValue) / oldValue * 100.0;
            changes.put(shareName, change);
        }
        return changes;
    }

    private Map<String, Double> sortChanges(Map<String, Double> changes) {
        return changes.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }

    private void appendTopPerformers(Map<String, Double> sortedMap, int count, boolean bestFirst) {
        ArrayList<Map.Entry<String, Double>> entryList = new ArrayList<Map.Entry<String, Double>>(sortedMap.entrySet());
        if (bestFirst) {
            for (int i = 0; i < Math.min(count, entryList.size()); ++i) {
                Map.Entry<String, Double> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
        } else {
            int startIndex = Math.max(0, entryList.size() - count);
            for (int i = entryList.size() - 1; i >= startIndex; --i) {
                Map.Entry<String, Double> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
        }
        if (entryList.isEmpty()) {
            this.builder.append("Нет данных\n");
        }
    }
}