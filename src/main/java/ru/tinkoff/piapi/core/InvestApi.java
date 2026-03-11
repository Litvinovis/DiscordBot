package ru.tinkoff.piapi.core;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import ru.tinkoff.piapi.contract.v1.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compatibility facade for SDK 1.48 where legacy ru.tinkoff.piapi.core.InvestApi is no longer published.
 * Implements only methods actually used by this project.
 */
public final class InvestApi implements AutoCloseable {

    private static final String DEFAULT_TARGET = "invest-public-api.tinkoff.ru:443";

    private final ManagedChannel channel;
    private final InstrumentsService instrumentsService;
    private final MarketDataService marketDataService;

    private InvestApi(String token) {
        String configured = System.getProperty("invest.api.target", "dns:///" + DEFAULT_TARGET);
        String target = configured.startsWith("dns:///") ? configured.substring("dns:///".length()) : configured;

        this.channel = ManagedChannelBuilder.forTarget(target)
                .useTransportSecurity()
                .build();

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);

        var instrumentsStub = InstrumentsServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        var marketDataStub = MarketDataServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        this.instrumentsService = new InstrumentsService(instrumentsStub);
        this.marketDataService = new MarketDataService(marketDataStub);
    }

    public static InvestApi create(String token) {
        return new InvestApi(token);
    }

    public static InvestApi createReadonly(String token) {
        return new InvestApi(token);
    }

    public InstrumentsService getInstrumentsService() {
        return instrumentsService;
    }

    public MarketDataService getMarketDataService() {
        return marketDataService;
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            channel.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class InstrumentsService {
        private final InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub;

        private InstrumentsService(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub) {
            this.stub = stub;
        }

        public List<Share> getAllSharesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_ALL)
                    .build();
            return stub.shares(request).getInstrumentsList();
        }

        public List<Currency> getAllCurrenciesSync() {
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_ALL)
                    .build();
            return stub.currencies(request).getInstrumentsList();
        }
    }

    public static final class MarketDataService {
        private final MarketDataServiceGrpc.MarketDataServiceBlockingStub stub;

        private MarketDataService(MarketDataServiceGrpc.MarketDataServiceBlockingStub stub) {
            this.stub = stub;
        }

        public List<LastPrice> getLastPricesSync(List<String> figies) {
            if (figies == null || figies.isEmpty()) {
                return List.of();
            }

            GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                    .addAllInstrumentId(figies)
                    .build();
            return stub.getLastPrices(request).getLastPricesList();
        }
    }
}
