package services.sandbox.ignite;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.migration.SandboxMigrationService;
import services.sandbox.repository.LimitOrderRepository;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.PriceAlertRepository;
import services.sandbox.repository.SandboxUserRepository;
import services.sandbox.repository.StopOrderRepository;
import services.sandbox.repository.TradeRepository;
import utils.ConfigLoader;

/**
 * Управляет подключением к Apache Ignite 3 и предоставляет доступ ко всем
 * репозиториям песочницы: пользователи, позиции, сделки, лимитные заявки,
 * стоп-ордера и ценовые алерты.
 *
 * <p>При создании экземпляра устанавливается thin client подключение к Ignite 3,
 * инициализируется схема таблиц, создаются репозитории, затем выполняются
 * миграции схемы данных.
 *
 * <p>При потере соединения с кластером вызов {@link #reconnect()} создаёт новый
 * {@link IgniteClient}, переинициализирует схему и сбрасывает кэшированные view
 * во всех репозиториях.
 */
public class SandboxIgniteManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxIgniteManager.class);

    private final String address;
    private volatile IgniteClient igniteClient;
    private final SandboxUserRepository usersRepo;
    private final PositionRepository positionsRepo;
    private final TradeRepository tradesRepo;
    private final LimitOrderRepository limitOrdersRepo;
    private final StopOrderRepository stopOrdersRepo;
    private final PriceAlertRepository priceAlertsRepo;

    /**
     * Инициализирует Ignite 3 thin client и создаёт все репозитории.
     * По завершении запускает инициализацию схемы и миграции данных.
     */
    public SandboxIgniteManager() {
        this.address = ConfigLoader.getIgnite3Address();
        log.info("SandboxIgniteManager: connecting to Ignite 3 at {}", address);
        this.igniteClient = IgniteClient.builder()
                .addresses(address)
                .build();

        // Инициализируем схему (CREATE TABLE IF NOT EXISTS)
        new SchemaInitializer(igniteClient).initSchema();

        // Создаём репозитории — передаём supplier, чтобы при переподключении
        // они автоматически получали свежий клиент
        this.usersRepo = new SandboxUserRepository(this::getIgniteClient);
        this.positionsRepo = new PositionRepository(this::getIgniteClient);
        this.tradesRepo = new TradeRepository(this::getIgniteClient);
        this.limitOrdersRepo = new LimitOrderRepository(this::getIgniteClient);
        this.stopOrdersRepo = new StopOrderRepository(this::getIgniteClient);
        this.priceAlertsRepo = new PriceAlertRepository(this::getIgniteClient);

        // Запускаем миграции схемы данных
        new SandboxMigrationService(this).runMigrations();
    }

    /**
     * Возвращает текущий подключённый клиент Apache Ignite 3.
     *
     * @return клиент Ignite 3
     */
    public IgniteClient getIgniteClient() {
        return igniteClient;
    }

    /**
     * Переподключается к Apache Ignite 3: закрывает старый клиент, создаёт новый,
     * переинициализирует схему. Репозитории автоматически получат новый клиент
     * через supplier при следующем обращении к их view.
     *
     * @return {@code true} если переподключение прошло успешно
     */
    public synchronized boolean reconnect() {
        log.info("SandboxIgniteManager: reconnecting to Ignite 3 at {}...", address);
        try {
            if (igniteClient != null) {
                try {
                    igniteClient.close();
                } catch (Exception ex) {
                    log.warn("SandboxIgniteManager: error closing old client: {}", ex.getMessage());
                }
            }
            igniteClient = IgniteClient.builder()
                    .addresses(address)
                    .build();
            new SchemaInitializer(igniteClient).initSchema();
            log.info("SandboxIgniteManager: reconnected successfully to {}", address);
            return true;
        } catch (Exception e) {
            log.error("SandboxIgniteManager: reconnect failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Возвращает репозиторий пользователей песочницы.
     *
     * @return репозиторий {@code sandbox_users}
     */
    public SandboxUserRepository usersRepo() {
        return usersRepo;
    }

    /**
     * Возвращает репозиторий позиций.
     *
     * @return репозиторий {@code sandbox_positions}
     */
    public PositionRepository positionsRepo() {
        return positionsRepo;
    }

    /**
     * Возвращает репозиторий истории сделок.
     *
     * @return репозиторий {@code sandbox_trades}
     */
    public TradeRepository tradesRepo() {
        return tradesRepo;
    }

    /**
     * Возвращает репозиторий лимитных заявок.
     *
     * @return репозиторий {@code sandbox_limit_orders}
     */
    public LimitOrderRepository limitOrdersRepo() {
        return limitOrdersRepo;
    }

    /**
     * Возвращает репозиторий стоп-ордеров.
     *
     * @return репозиторий {@code sandbox_stop_orders}
     */
    public StopOrderRepository stopOrdersRepo() {
        return stopOrdersRepo;
    }

    /**
     * Возвращает репозиторий ценовых алертов.
     *
     * @return репозиторий {@code sandbox_price_alerts}
     */
    public PriceAlertRepository priceAlertsRepo() {
        return priceAlertsRepo;
    }
}
