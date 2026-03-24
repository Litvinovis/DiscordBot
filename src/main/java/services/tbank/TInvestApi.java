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

/**
 * Фасад для работы с T-Invest gRPC API.
 *
 * <p>Предоставляет два вложенных сервиса:
 * <ul>
 *   <li>{@link InstrumentsService} — получение списка инструментов (акции, валюты);</li>
 *   <li>{@link MarketDataService} — получение последних цен.</li>
 * </ul>
 * Все методы выполняются синхронно с автоматическим повтором при ошибке 429
 * через {@link ExponentialBackoffRetry}.
 */
public class TInvestApi {
    private final InstrumentsService instrumentsService;
    private final MarketDataService marketDataService;

    private TInvestApi(ServiceStubFactory factory) {
        this.instrumentsService = new InstrumentsService(factory);
        this.marketDataService = new MarketDataService(factory);
    }

    /**
     * Создаёт экземпляр {@code TInvestApi} с указанными параметрами подключения.
     *
     * @param token          токен авторизации T-Invest API
     * @param sandboxEnabled {@code true} — подключение к sandbox-эндпоинту
     * @param targetUrl      адрес gRPC-сервера в формате {@code dns:///host:port}
     * @return инициализированный экземпляр API
     */
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

    /**
     * Возвращает сервис работы с инструментами (акции, валюты).
     *
     * @return {@link InstrumentsService}
     */
    public InstrumentsService getInstrumentsService() {
        return this.instrumentsService;
    }

    /**
     * Возвращает сервис рыночных данных (последние цены).
     *
     * @return {@link MarketDataService}
     */
    public MarketDataService getMarketDataService() {
        return this.marketDataService;
    }

    /**
     * Синхронный клиент gRPC-сервиса инструментов T-Invest.
     * Предоставляет методы для получения списков акций и валют.
     */
    public static class InstrumentsService {
        private final SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsStub;

        private InstrumentsService(ServiceStubFactory factory) {
            this.instrumentsStub = factory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);
        }

        /**
         * Возвращает список всех базовых валютных инструментов.
         *
         * @return список объектов {@code Currency}
         */
        public List<Currency> getAllCurrenciesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();
            return ExponentialBackoffRetry.execute(() ->
                    ((CurrenciesResponse) this.instrumentsStub.callSyncMethod(stub -> stub.currencies(request)))
                            .getInstrumentsList()
            );
        }

        /**
         * Возвращает список всех базовых акций.
         *
         * @return список объектов {@code Share}
         */
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

    /**
     * Синхронный клиент gRPC-сервиса рыночных данных T-Invest.
     * Предоставляет метод получения последних цен по списку инструментов.
     */
    public static class MarketDataService {
        private final SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataStub;

        private MarketDataService(ServiceStubFactory factory) {
            this.marketDataStub = factory.newSyncService(MarketDataServiceGrpc::newBlockingStub);
        }

        /**
         * Возвращает последние цены для заданных инструментов.
         *
         * @param instrumentIds список FIGI или UID инструментов
         * @return список объектов {@code LastPrice}
         */
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
