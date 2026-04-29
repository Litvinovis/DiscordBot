package services.statTask;

import com.discord.stonks.config.DiscordProperties;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import services.tbank.TInvestApi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ежедневный отчёт по изменению котировок акций.
 */
@Component
public class SharesStatTask {
    private static final Logger logger = LoggerFactory.getLogger(SharesStatTask.class);
    private static final int SCALE = 8;
    private static final int MAX_INSTRUMENTS_PER_REQUEST = 3000;
    private static final int MAX_SHARES_TO_PROCESS = 1000;
    private static final List<String> RUSSIAN_EXCHANGES = List.of("MOEX", "RTS");
    private static final List<String> SPB_CLASS_CODES = List.of("FQBR");

    private final TInvestApi api;
    private final JDA jda;
    private final DiscordProperties discordProperties;
    private final StringBuilder builder = new StringBuilder();
    private final Map<String, BigDecimal> oldData = new HashMap<>();
    private final Map<String, BigDecimal> newData = new HashMap<>();
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "A29", "A30");

    public SharesStatTask(TInvestApi api, JDA jda, DiscordProperties discordProperties) {
        this.api = api;
        this.jda = jda;
        this.discordProperties = discordProperties;
    }

    @Scheduled(cron = "${reports.shares-cron}")
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
        String guildId = discordProperties.reportGuildId();
        String channelName = discordProperties.reportChannelName();
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
                .limit(MAX_SHARES_TO_PROCESS)
                .toList();
            List<Share> russianShares = allShares.stream().filter(this::isRussianShare).toList();
            List<Share> spbShares = allShares.stream().filter(this::isSpbShare).toList();
            logger.info("Найдено {} акций для обработки, из них {} российских, {} СПБ Биржи", allShares.size(), russianShares.size(), spbShares.size());
            List<List<String>> figiChunks = this.partitionList(allShares.stream().map(Share::getFigi).toList(), MAX_INSTRUMENTS_PER_REQUEST);
            ArrayList<LastPrice> allLastPrices = new ArrayList<>();
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
            HashMap<String, LastPrice> priceMap = new HashMap<>();
            for (LastPrice price : allLastPrices) {
                priceMap.put(price.getFigi(), price);
            }
            int skippedCount = 0;
            HashMap<String, BigDecimal> allSharesData = new HashMap<>();
            HashMap<String, BigDecimal> russianSharesData = new HashMap<>();
            HashMap<String, BigDecimal> spbSharesData = new HashMap<>();
            for (Share share : allShares) {
                LastPrice price = priceMap.get(share.getFigi());
                if (price == null || price.getPrice().getUnits() == 0L && price.getPrice().getNano() == 0) {
                    ++skippedCount;
                    continue;
                }
                BigDecimal priceValue = BigDecimal.valueOf(price.getPrice().getUnits())
                        .add(BigDecimal.valueOf(price.getPrice().getNano(), 9));
                allSharesData.put(share.getName(), priceValue);
                if (this.isRussianShare(share)) {
                    russianSharesData.put(share.getName(), priceValue);
                }
                if (this.isSpbShare(share)) {
                    spbSharesData.put(share.getName(), priceValue);
                }
            }
            this.newData.putAll(allSharesData);
            logger.info("Обработано {} акций, пропущено {} из-за отсутствия данных", this.newData.size(), skippedCount);
            if (this.oldData.isEmpty()) {
                this.oldData.putAll(this.newData);
                return null;
            }
            Map<String, BigDecimal> allChanges = this.calculateChanges(allSharesData);
            Map<String, BigDecimal> russianChanges = this.calculateChanges(russianSharesData);
            Map<String, BigDecimal> spbChanges = this.calculateChanges(spbSharesData);
            Map<String, BigDecimal> sortedAll = this.sortChanges(allChanges);
            Map<String, BigDecimal> sortedRussian = this.sortChanges(russianChanges);
            Map<String, BigDecimal> sortedSpb = this.sortChanges(spbChanges);
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
            if (!sortedSpb.isEmpty()) {
                this.builder.append("\n\n**СПБ Биржа (иностранные акции) - Топ 5 лучших:**\n");
                this.appendTopPerformers(sortedSpb, 5, true);
                this.builder.append("\n**СПБ Биржа (иностранные акции) - Топ 5 худших:**\n");
                this.appendTopPerformers(sortedSpb, 5, false);
            } else {
                this.builder.append("\n\n**СПБ Биржа:**\nНет данных по иностранным акциям\n");
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
        ArrayList<List<T>> chunks = new ArrayList<>();
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

    /**
     * Returns true for foreign stocks traded on the Saint Petersburg Exchange (СПБ Биржа).
     * In the T-Bank Invest API these carry the class code "FQBR".
     */
    private boolean isSpbShare(Share share) {
        return SPB_CLASS_CODES.contains(share.getClassCode());
    }

    private Map<String, BigDecimal> calculateChanges(Map<String, BigDecimal> currentData) {
        HashMap<String, BigDecimal> changes = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : currentData.entrySet()) {
            String shareName = entry.getKey();
            if (!this.oldData.containsKey(shareName)) continue;
            BigDecimal oldValue = this.oldData.get(shareName);
            BigDecimal newValue = entry.getValue();
            if (oldValue.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal change = newValue.subtract(oldValue)
                    .divide(oldValue, SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            changes.put(shareName, change);
        }
        return changes;
    }

    private Map<String, BigDecimal> sortChanges(Map<String, BigDecimal> changes) {
        return changes.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (ov, nv) -> ov, LinkedHashMap::new));
    }

    private void appendTopPerformers(Map<String, BigDecimal> sortedMap, int count, boolean bestFirst) {
        ArrayList<Map.Entry<String, BigDecimal>> entryList = new ArrayList<>(sortedMap.entrySet());
        if (bestFirst) {
            for (int i = 0; i < Math.min(count, entryList.size()); ++i) {
                Map.Entry<String, BigDecimal> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(entry.getValue().toPlainString()).append("%\n");
            }
        } else {
            int startIndex = Math.max(0, entryList.size() - count);
            for (int i = entryList.size() - 1; i >= startIndex; --i) {
                Map.Entry<String, BigDecimal> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(entry.getValue().toPlainString()).append("%\n");
            }
        }
        if (entryList.isEmpty()) {
            this.builder.append("Нет данных\n");
        }
    }
}
