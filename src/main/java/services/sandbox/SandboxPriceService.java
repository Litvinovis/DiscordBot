package services.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import services.tbank.TInvestApi;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SandboxPriceService {

    private static final Logger log = LoggerFactory.getLogger(SandboxPriceService.class);

    private final TInvestApi api;

    public SandboxPriceService(TInvestApi api) {
        this.api = api;
    }

    public BigDecimal loadPrice(String instrumentId) {
        List<LastPrice> prices = api.getMarketDataService().getLastPricesSync(List.of(instrumentId));
        if (prices == null || prices.isEmpty()) return BigDecimal.ZERO;
        return quotationToBigDecimal(prices.getFirst().getPrice());
    }

    public BigDecimal loadPriceSafe(String instrumentId) {
        try {
            return loadPrice(instrumentId);
        } catch (Exception e) {
            log.warn("loadPriceSafe failed for {}: {}", instrumentId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal quotationToBigDecimal(Quotation q) {
        return BigDecimal.valueOf(q.getUnits()).add(BigDecimal.valueOf(q.getNano(), 9));
    }
}
