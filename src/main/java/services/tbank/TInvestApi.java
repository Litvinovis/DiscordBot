package services.tbank;

import ru.tinkoff.piapi.contract.v1.*;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.util.List;
import java.util.Properties;

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
        return instrumentsService;
    }

    public MarketDataService getMarketDataService() {
        return marketDataService;
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
            return instrumentsStub.callSyncMethod(stub -> stub.currencies(request)).getInstrumentsList();
        }

        public List<Share> getAllSharesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();
            return instrumentsStub.callSyncMethod(stub -> stub.shares(request)).getInstrumentsList();
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
            return marketDataStub.callSyncMethod(stub -> stub.getLastPrices(request)).getLastPricesList();
        }
    }
}
