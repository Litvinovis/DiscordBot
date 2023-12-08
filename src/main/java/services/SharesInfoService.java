package services;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SharesInfoService {
    private final InvestApi api;
    private final StringBuilder builder = new StringBuilder();
    private final Logger logger = LoggerFactory.getLogger("default-logger");
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "FQBR");

    public SharesInfoService(InvestApi api) {
        this.api = api;
    }

    public String getSharesInfo(String sharesName) {
        builder.setLength(0);
        builder.append("Информация о подходящих акциях: \n");
        try {
            List<Share> shares = api.getInstrumentsService().getAllSharesSync().stream()
                    .filter(share -> checkClassCode(share.getClassCode()))
                    .filter(share -> share.getName().toLowerCase().contains(sharesName.toLowerCase()))
                    .toList();
            List<LastPrice> lastPrices = api.getMarketDataService().getLastPricesSync(
                    shares.stream().map(Share::getFigi).collect(Collectors.toList()));
            if (lastPrices.isEmpty()) {
                builder.setLength(0);
                return "По запросу ".concat(sharesName).concat(" ничего не нашлось");
            }
            for (int i = 0; i < shares.size(); i++) {
                builder.append("\n").append("Название: ").append(shares.get(i).getName()).append("\n")
                        .append("Стоимость = ").append(lastPrices.get(i).getPrice().getUnits()).append(",")
                        .append(lastPrices.get(i).getPrice().getNano() > 100 ?
                                String.valueOf(lastPrices.get(i).getPrice().getNano()).substring(0, 2) :
                                lastPrices.get(i).getPrice().getNano())
                        .append(" ").append(shares.get(i).getCurrency().toUpperCase()).append("\n");
            }
        } catch (Exception e) {
            logger.trace(e.getMessage());
            return "Сорян я глючу " + e.getMessage();
        }
        return builder.toString();
    }

    private boolean checkClassCode(String classCode) {
        return !badCode.contains(classCode);
    }
}
