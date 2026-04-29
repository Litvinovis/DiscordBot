/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  ru.tinkoff.piapi.contract.v1.LastPrice
 *  ru.tinkoff.piapi.contract.v1.Share
 */
package services;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import services.tbank.TInvestApi;

/**
 * Сервис получения информации об акциях через T-Invest API.
 *
 * <p>Ищет акции по точному тикеру или по вхождению строки в название.
 * Возвращает цену каждого найденного инструмента.
 */
@Service
public class SharesInfoService {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(SharesInfoService.class);
    private final TInvestApi api;
    private final Logger logger = LoggerFactory.getLogger((String)"default-logger");
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "A29", "A30");

    /**
     * Создаёт сервис информации об акциях.
     *
     * @param api клиент T-Invest API
     */
    public SharesInfoService(TInvestApi api) {
        this.api = api;
    }

    /**
     * Возвращает информацию об акциях, соответствующих запросу.
     *
     * <p>Сначала ищет по точному совпадению тикера, затем по вхождению строки в название.
     *
     * @param sharesName тикер или часть названия акции
     * @return отформатированная строка с ценами найденных акций или сообщение об отсутствии результатов
     */
    public String getSharesInfo(String sharesName) {
        StringBuilder builder = new StringBuilder();
        builder.append("\u0418\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f \u043e \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0445 \u0430\u043a\u0446\u0438\u044f\u0445: \n");
        try {
            String query = sharesName == null ? "" : sharesName.trim();
            String qLower = query.toLowerCase();
            List<Share> allShares = this.api.getInstrumentsService().getAllSharesSync().stream().filter(share -> this.checkClassCode(share.getClassCode())).toList();
            List<Share> shares = allShares.stream().filter(share -> share.getTicker() != null && share.getTicker().equalsIgnoreCase(query)).toList();
            if (shares.isEmpty()) {
                shares = allShares.stream().filter(share -> share.getName() != null && share.getName().toLowerCase().contains(qLower)).limit(50L).toList();
            }
            if (shares.isEmpty()) {
                return "\u041f\u043e \u0437\u0430\u043f\u0440\u043e\u0441\u0443 ".concat(sharesName).concat(" \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0448\u043b\u043e\u0441\u044c");
            }
            List<LastPrice> lastPrices = this.api.getMarketDataService().getLastPricesSync(shares.stream().map(Share::getFigi).collect(Collectors.toList()));
            if (lastPrices.isEmpty()) {
                return "\u041f\u043e \u0437\u0430\u043f\u0440\u043e\u0441\u0443 ".concat(sharesName).concat(" \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0448\u043b\u043e\u0441\u044c");
            }
            HashMap<Share, LastPrice> sharesMap = new HashMap<Share, LastPrice>(shares.size());
            for (int i = 0; i < shares.size() && i < lastPrices.size(); ++i) {
                if (lastPrices.get(i).getPrice().getUnits() == 0L && lastPrices.get(i).getPrice().getNano() == 0) continue;
                sharesMap.put(shares.get(i), lastPrices.get(i));
            }
            this.createShareInfo(sharesMap, builder);
        }
        catch (Throwable e) {
            this.logger.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u0438 \u0434\u0430\u043d\u043d\u044b\u0445 \u043f\u043e \u0430\u043a\u0446\u0438\u044f\u043c '{}': {}", new Object[]{sharesName, e.getMessage(), e});
            return "\u0421\u0435\u0440\u0432\u0438\u0441 \u043a\u043e\u0442\u0438\u0440\u043e\u0432\u043e\u043a \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d, \u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u043f\u043e\u0437\u0436\u0435";
        }
        return builder.toString();
    }

    private boolean checkClassCode(String classCode) {
        return !this.badCode.contains(classCode);
    }

    private void createShareInfo(Map<Share, LastPrice> shares, StringBuilder builder) {
        for (Map.Entry<Share, LastPrice> entry : shares.entrySet()) {
            double price = (double)entry.getValue().getPrice().getUnits() + (double)entry.getValue().getPrice().getNano() / 1.0E9;
            builder.append("\n").append("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435: ").append(entry.getKey().getName()).append("\n").append("\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c = ").append(String.format("%.2f", price)).append(" ").append(entry.getKey().getCurrency().toUpperCase()).append("\n");
        }
    }
}

