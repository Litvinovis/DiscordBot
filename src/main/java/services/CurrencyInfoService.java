package services;

import ru.tinkoff.piapi.contract.v1.Currency;
import services.tbank.TInvestApi;

import java.util.List;

public class CurrencyInfoService {
    private final TInvestApi api;
    private final StringBuilder builder = new StringBuilder();

    public CurrencyInfoService(TInvestApi api) {
        this.api = api;
    }

    public String getCurrencyInfo(String currency) {
        try {
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
        } catch (Throwable t) {
            return "Сервис котировок временно недоступен, попробуйте позже";
        }
    }
}