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
 */
public class SandboxIgniteManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxIgniteManager.class);

    private final IgniteClient igniteClient;
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
        String address = ConfigLoader.getIgnite3Address();
        log.info("SandboxIgniteManager: connecting to Ignite 3 at {}", address);
        this.igniteClient = IgniteClient.builder()
                .addresses(address)
                .build();

        // Инициализируем схему (CREATE TABLE IF NOT EXISTS)
        new SchemaInitializer(igniteClient).initSchema();

        // Создаём репозитории (ленивая инициализация view внутри)
        this.usersRepo = new SandboxUserRepository(igniteClient);
        this.positionsRepo = new PositionRepository(igniteClient);
        this.tradesRepo = new TradeRepository(igniteClient);
        this.limitOrdersRepo = new LimitOrderRepository(igniteClient);
        this.stopOrdersRepo = new StopOrderRepository(igniteClient);
        this.priceAlertsRepo = new PriceAlertRepository(igniteClient);

        // Запускаем миграции схемы данных
        new SandboxMigrationService(this).runMigrations();
    }

    /**
     * Возвращает подключённый клиент Apache Ignite 3.
     *
     * @return клиент Ignite 3
     */
    public IgniteClient getIgniteClient() {
        return igniteClient;
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
