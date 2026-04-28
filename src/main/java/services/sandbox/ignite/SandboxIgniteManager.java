package services.sandbox.ignite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.sql.DataSource;

public class SandboxIgniteManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxIgniteManager.class);

    private final DataSource dataSource;
    private final SandboxUserRepository usersRepo;
    private final PositionRepository positionsRepo;
    private final TradeRepository tradesRepo;
    private final LimitOrderRepository limitOrdersRepo;
    private final StopOrderRepository stopOrdersRepo;
    private final PriceAlertRepository priceAlertsRepo;

    public SandboxIgniteManager() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(ConfigLoader.getDbUrl());
        hikariConfig.setUsername(ConfigLoader.getDbUsername());
        hikariConfig.setPassword(ConfigLoader.getDbPassword());
        hikariConfig.setMaximumPoolSize(5);
        this.dataSource = new HikariDataSource(hikariConfig);

        new SchemaInitializer(dataSource).initSchema();
        new SandboxMigrationService(this).runMigrations();

        this.usersRepo      = new SandboxUserRepository(dataSource);
        this.positionsRepo  = new PositionRepository(dataSource);
        this.tradesRepo     = new TradeRepository(dataSource);
        this.limitOrdersRepo = new LimitOrderRepository(dataSource);
        this.stopOrdersRepo = new StopOrderRepository(dataSource);
        this.priceAlertsRepo = new PriceAlertRepository(dataSource);

        log.info("SandboxIgniteManager: connected to PostgreSQL at {}", ConfigLoader.getDbUrl());
    }

    public SandboxUserRepository usersRepo()        { return usersRepo; }
    public PositionRepository positionsRepo()       { return positionsRepo; }
    public TradeRepository tradesRepo()             { return tradesRepo; }
    public LimitOrderRepository limitOrdersRepo()   { return limitOrdersRepo; }
    public StopOrderRepository stopOrdersRepo()     { return stopOrdersRepo; }
    public PriceAlertRepository priceAlertsRepo()   { return priceAlertsRepo; }
}
