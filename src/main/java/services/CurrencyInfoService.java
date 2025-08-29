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
        // Format nano value properly
        String nanoStr = String.valueOf(Math.abs(lastPrices.get(0).getPrice().getNano()));
        // Pad with leading zeros if necessary
        while (nanoStr.length() < 9) {
            nanoStr = "0" + nanoStr;
        }
        // Take only first 2 digits
        nanoStr = nanoStr.substring(0, 2);
        
        builder.append("Курс = ")
                .append(lastPrices.get(0).getPrice().getUnits())
                .append(",")
                .append(nanoStr)
                .append(" рублей за 1 ")
                .append(currency);
        return builder.toString();
    }
}