package services.tbank;

import java.util.List;
import java.util.Properties;
import ru.tinkoff.piapi.contract.v1.CurrenciesResponse;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.SharesResponse;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

public class TInvestApi {
    private final InstrumentsService instrumentsService;
    private final MarketDataService marketDataService;

    private TInvestApi(ServiceStubFactory factory) {
        this.instrumentsService = new InstrumentsService(factory);
        this.marketDataService = new MarketDataService(factory);
    }

    public static TInvestApi create(String token, boolean sandboxEnabled, String targetUrl) {
        Properties properties = new Properties();
        properties.setProperty("token", token);
        properties.setProperty("sandbox.enabled", Boolean.toString(sandboxEnabled));
        if (targetUrl != null && !targetUrl.isBlank()) {
            properties.setProperty("target", targetUrl);
        }
        ConnectorConfiguration configuration = ConnectorConfiguration.loadFromProperties(properties);
        ServiceStubFactory factory = ServiceStubFactory.create(configuration);
        return new TInvestApi(factory);
    }

    public InstrumentsService getInstrumentsService() {
        return this.instrumentsService;
    }

    public MarketDataService getMarketDataService() {
        return this.marketDataService;
    }

    public static class InstrumentsService {
        private final SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsStub;

        private InstrumentsService(ServiceStubFactory factory) {
            this.instrumentsStub = factory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);
        }

        public List<Currency> getAllCurrenciesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();
            return ExponentialBackoffRetry.execute(() ->
                    ((CurrenciesResponse) this.instrumentsStub.callSyncMethod(stub -> stub.currencies(request)))
                            .getInstrumentsList()
            );
        }

        public List<Share> getAllSharesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();
            return ExponentialBackoffRetry.execute(() ->
                    ((SharesResponse) this.instrumentsStub.callSyncMethod(stub -> stub.shares(request)))
                            .getInstrumentsList()
            );
        }
    }

    public static class MarketDataService {
        private final SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataStub;

        private MarketDataService(ServiceStubFactory factory) {
            this.marketDataStub = factory.newSyncService(MarketDataServiceGrpc::newBlockingStub);
        }

        public List<LastPrice> getLastPricesSync(List<String> instrumentIds) {
            GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                    .addAllInstrumentId(instrumentIds)
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();
            return ExponentialBackoffRetry.execute(() ->
                    ((GetLastPricesResponse) this.marketDataStub.callSyncMethod(stub -> stub.getLastPrices(request)))
                            .getLastPricesList()
            );
        }
    }
}
