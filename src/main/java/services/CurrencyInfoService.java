/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ru.tinkoff.piapi.contract.v1.Currency
 *  ru.tinkoff.piapi.contract.v1.LastPrice
 */
package services;

import java.util.List;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import services.tbank.TInvestApi;

public class CurrencyInfoService {
    private final TInvestApi api;
    private final StringBuilder builder = new StringBuilder();

    public CurrencyInfoService(TInvestApi api) {
        this.api = api;
    }

    public String getCurrencyInfo(String currency) {
        try {
            this.builder.setLength(0);
            this.builder.append("\u0418\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f \u043e \u0432\u0430\u043b\u044e\u0442\u0435 ").append(currency).append(":\n");
            List<Currency> currencies = this.api.getInstrumentsService().getAllCurrenciesSync();
            List<String> filtered = currencies.stream().filter(currency1 -> currency1.getIsoCurrencyName().equalsIgnoreCase(currency)).map(Currency::getFigi).toList();
            List<LastPrice> lastPrices = this.api.getMarketDataService().getLastPricesSync(filtered);
            if (lastPrices.isEmpty()) {
                this.builder.setLength(0);
                return "\u041f\u043e \u0437\u0430\u043f\u0440\u043e\u0441\u0443 ".concat(currency).concat(" \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0448\u043b\u043e\u0441\u044c");
            }
            this.builder.append("\u041a\u0443\u0440\u0441 = ").append(String.format("%.2f", (double)lastPrices.get(0).getPrice().getUnits() + (double)lastPrices.get(0).getPrice().getNano() / 1.0E9)).append(" \u0440\u0443\u0431\u043b\u0435\u0439 \u0437\u0430 1 ").append(currency);
            return this.builder.toString();
        }
        catch (Throwable t) {
            return "\u0421\u0435\u0440\u0432\u0438\u0441 \u043a\u043e\u0442\u0438\u0440\u043e\u0432\u043e\u043a \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d, \u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u043f\u043e\u0437\u0436\u0435";
        }
    }
}

