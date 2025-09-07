package services;

import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.List;

public class CurrencyInfoService {
    private final InvestApi api;
    private final StringBuilder builder = new StringBuilder();

    public CurrencyInfoService(InvestApi api) {
        this.api = api;
    }

    public String getCurrencyInfo(String currency) {
        builder.setLength(0);
        builder.append("Информация о валюте ").append(currency).append(":\n");
        List<Currency> currencies = api.getInstrumentsService().getAllCurrenciesSync();
        List<String> filtered = currencies.stream()
                .filter(currency1 -> currency1.getIsoCurrencyName().equalsIgnoreCase(currency))
                .map(Currency::getFigi)
                .toList();
        var lastPrices = api.getMarketDataService().getLastPricesSync(filtered);
        if (lastPrices.isEmpty()) {
            builder.setLength(0);
            return "По запросу ".concat(currency).concat(" ничего не нашлось");
        }
        builder.append("Курс = ")
                .append(String.format("%.2f", 
                    lastPrices.get(0).getPrice().getUnits() + 
                    (double) lastPrices.get(0).getPrice().getNano() / 1_000_000_000))
                .append(" рублей за 1 ")
                .append(currency);
        return builder.toString();
    }
}