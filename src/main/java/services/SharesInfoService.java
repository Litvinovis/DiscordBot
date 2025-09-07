package services;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SharesInfoService {
    private final InvestApi api;
    private final StringBuilder builder = new StringBuilder();
    private final Logger logger = LoggerFactory.getLogger("default-logger");
    private final List<String> badCode = List.of("SPEQ", "SMAL", "SPBXM_OTC", "FQBR", "A29", "A30");

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
                return "По запросу ".concat(sharesName).concat(" ничего не нашлось");
            } else {
                Map<Share, LastPrice> sharesMap = new HashMap<>(shares.size());
                for (int i = 0; i < shares.size() && i < lastPrices.size(); i++) {
                    if (lastPrices.get(i).getPrice().getUnits() != 0 || lastPrices.get(i).getPrice().getNano() != 0) {
                        sharesMap.put(shares.get(i), lastPrices.get(i));
                    }
                }
                createShareInfo(sharesMap);
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

    private void createShareInfo(Map<Share, LastPrice> shares) {
        for (Map.Entry<Share, LastPrice> entry : shares.entrySet()) {
            builder.append("\n").append("Название: ").append(entry.getKey().getName()).append("\n")
                    .append("Стоимость = ").append(entry.getValue().getPrice().getUnits()).append(".")
                    .append(String.format("%09d", entry.getValue().getPrice().getNano()))
                    .append(" ").append(entry.getKey().getCurrency().toUpperCase()).append("\n");
        }
    }
}