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
 *  ru.tinkoff.piapi.contract.v1.Currency
 *  ru.tinkoff.piapi.contract.v1.LastPrice
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
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class CurrencyStatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyStatTask.class);
    private final TInvestApi api;
    private final JDA jda;
    private final StringBuilder builder = new StringBuilder();
    private final StringBuilder price = new StringBuilder();
    private Map<String, Double> oldData = new HashMap<String, Double>();
    private final Map<String, Double> newData = new HashMap<String, Double>();

    public CurrencyStatTask(TInvestApi api, JDA jda) {
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
                this.price.setLength(0);
                this.price.append(lastPrices.get(i).getPrice().getUnits()).append(".").append(String.format("%09d", lastPrices.get(i).getPrice().getNano()));
                this.newData.put(currencies.get(i).getName(), Double.parseDouble(this.price.toString()));
            }
            if (this.oldData.isEmpty()) {
                this.oldData = new HashMap<String, Double>(this.newData);
                return null;
            }
            HashMap<String, Double> changes = new HashMap<String, Double>();
            for (Map.Entry<String, Double> e : this.newData.entrySet()) {
                String currencyName = e.getKey();
                if ("\u0420\u043e\u0441\u0441\u0438\u0439\u0441\u043a\u0438\u0439 \u0440\u0443\u0431\u043b\u044c".equals(currencyName) || !this.oldData.containsKey(currencyName)) continue;
                double oldValue2 = this.oldData.get(currencyName);
                double newValue2 = e.getValue();
                if (oldValue2 == 0.0) continue;
                double change = (newValue2 - oldValue2) / oldValue2 * 100.0;
                changes.put(currencyName, change);
            }
            Map<String, Double> sortedMap = changes.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            this.builder.append("**\u0422\u043e\u043f 5 \u043b\u0443\u0447\u0448\u0438\u0445 \u0432\u0430\u043b\u044e\u0442 \u043a \u0440\u0443\u0431\u043b\u044e \u0441\u0435\u0433\u043e\u0434\u043d\u044f:**\n");
            List<Map.Entry<String, Double>> entryList = new ArrayList<>(sortedMap.entrySet());
            int count = 0;
            for (Map.Entry<String, Double> entry : entryList) {
                if (count >= 5) break;
                this.builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
                ++count;
            }
            this.builder.append("\n**\u0422\u043e\u043f 5 \u0445\u0443\u0434\u0448\u0438\u0445 \u0432\u0430\u043b\u044e\u0442 \u043a \u0440\u0443\u0431\u043b\u044e \u0441\u0435\u0433\u043e\u0434\u043d\u044f:**\n");
            count = 0;
            for (int i = entryList.size() - 1; i >= 0 && count < 5; ++count, --i) {
                Map.Entry<String, Double> entry = entryList.get(i);
                this.builder.append(entry.getKey()).append(" : ").append(String.format("%.2f", entry.getValue())).append("%\n");
            }
            this.oldData = new HashMap<String, Double>(this.newData);
            this.newData.clear();
        } catch (Exception e) {
            logger.error("Ошибка при формировании отчета по валютам", e);
            return "Ошибка при формировании отчета по валютам: " + e.getMessage();
        }
        return this.builder.toString();
    }
}