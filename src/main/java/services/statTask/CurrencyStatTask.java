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
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
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
 * Ежедневный отчёт по изменению курсов валют.
 */
@Component
public class CurrencyStatTask {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyStatTask.class);
    private static final int SCALE = 8;
    private final TInvestApi api;
    private final JDA jda;
    private final DiscordProperties discordProperties;
    private final StringBuilder builder = new StringBuilder();
    private Map<String, BigDecimal> oldData = new HashMap<>();
    private final Map<String, BigDecimal> newData = new HashMap<>();

    public CurrencyStatTask(TInvestApi api, JDA jda, DiscordProperties discordProperties) {
        this.api = api;
        this.jda = jda;
        this.discordProperties = discordProperties;
    }

    @Scheduled(cron = "${reports.currency-cron}")
    public void run() {
        try {
            String message = this.createMessage();
            if (!Strings.isNullOrEmpty(message)) {
                this.sendReport(message);
            }
        } catch (Exception e) {
            logger.error("Ошибка при выполнении задачи валютного отчета", e);
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
            logger.info("Отчет по валютам успешно отправлен в канал {} на сервере {}", channelName, guild.getName());
        } catch (Exception e) {
            logger.error("Ошибка при отправке отчета по валютам", e);
        }
    }

    private String createMessage() {
        this.builder.setLength(0);
        try {
            List<Currency> currencies = this.api.getInstrumentsService().getAllCurrenciesSync();
            List<String> figiList = currencies.stream().map(Currency::getFigi).toList();
            List<LastPrice> lastPrices = this.api.getMarketDataService().getLastPricesSync(figiList);
            for (int i = 0; i < lastPrices.size(); ++i) {
                BigDecimal priceValue = BigDecimal.valueOf(lastPrices.get(i).getPrice().getUnits())
                        .add(BigDecimal.valueOf(lastPrices.get(i).getPrice().getNano(), 9));
                this.newData.put(currencies.get(i).getName(), priceValue);
            }
            if (this.oldData.isEmpty()) {
                this.oldData = new HashMap<>(this.newData);
                return null;
            }
            HashMap<String, BigDecimal> changes = new HashMap<>();
            for (Map.Entry<String, BigDecimal> e : this.newData.entrySet()) {
                String currencyName = e.getKey();
                if ("Российский рубль".equals(currencyName) || !this.oldData.containsKey(currencyName)) continue;
                BigDecimal oldValue = this.oldData.get(currencyName);
                BigDecimal newValue = e.getValue();
                if (oldValue.compareTo(BigDecimal.ZERO) == 0) continue;
                // change% = (new - old) / old * 100
                BigDecimal change = newValue.subtract(oldValue)
                        .divide(oldValue, SCALE, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                changes.put(currencyName, change);
            }
            Map<String, BigDecimal> sortedMap = changes.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (ov, nv) -> ov, LinkedHashMap::new));
            this.builder.append("**Топ 5 лучших валют к рублю сегодня:**\n");
            List<Map.Entry<String, BigDecimal>> entryList = new ArrayList<>(sortedMap.entrySet());
            int count = 0;
            for (Map.Entry<String, BigDecimal> entry : entryList) {
                if (count >= 5) break;
                this.builder.append(entry.getKey()).append(" : ").append(entry.getValue().toPlainString()).append("%\n");
                ++count;
            }
            this.builder.append("\n**Топ 5 худших валют к рублю сегодня:**\n");
            count = 0;
            for (int i = entryList.size() - 1; i >= 0 && count < 5; ++count, --i) {
                Map.Entry<String, BigDecimal> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(entry.getValue().toPlainString()).append("%\n");
            }
            this.oldData = new HashMap<>(this.newData);
            this.newData.clear();
        } catch (Exception e) {
            logger.error("Ошибка при формировании отчета по валютам", e);
            return "Ошибка при формировании отчета по валютам: " + e.getMessage();
        }
        return this.builder.toString();
    }
}
