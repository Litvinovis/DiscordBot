package services.sandbox.ignite;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис периодической проверки состояния Apache Ignite 3 с механизмом переподключения.
 *
 * <p>Каждые 5 минут выполняет лёгкий SQL-запрос к кластеру. При обнаружении сбоя
 * немедленно запускает цикл переподключения с интервалом {@value #RECONNECT_PERIOD_SEC} секунд.
 * После успешного переподключения цикл останавливается и возобновляется обычная
 * проверка здоровья.
 */
public class IgniteHealthService {

    private static final Logger log = LoggerFactory.getLogger(IgniteHealthService.class);

    /** Как часто выполняется проверка здоровья (мс, можно переопределить в тестах). */
    static final long CHECK_PERIOD_MS = TimeUnit.MINUTES.toMillis(5);

    /** Интервал между попытками переподключения при потере соединения (секунды). */
    private static final long RECONNECT_PERIOD_SEC = 30;

    private final SandboxIgniteManager manager;

    /** Счётчик суммарных неудач health-check с момента запуска. */
    private final AtomicLong healthCheckFailures = new AtomicLong(0);

    /** true пока активен цикл переподключения. */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-check");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт сервис на основе менеджера Ignite 3.
     *
     * @param manager менеджер подключения Ignite 3
     */
    public IgniteHealthService(SandboxIgniteManager manager) {
        this.manager = manager;
    }

    /**
     * Запускает периодическую проверку здоровья.
     * Первая проверка выполняется через один период после старта.
     */
    public void start() {
        long periodSeconds = CHECK_PERIOD_MS / 1000;
        scheduler.scheduleAtFixedRate(
                this::runCheck,
                periodSeconds,
                periodSeconds,
                TimeUnit.SECONDS
        );
        log.info("IgniteHealthService запущен (период={}с)", periodSeconds);
        // Немедленно запускаем переподключение если Ignite недоступен при старте
        if (manager.getIgniteClient() == null) {
            log.warn("IgniteHealthService: Ignite недоступен при старте, немедленно запускаю переподключение");
            scheduleReconnect();
        }
    }

    /**
     * Останавливает проверку здоровья.
     */
    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Возвращает суммарное количество неудачных health-check с момента запуска.
     */
    public long getHealthCheckFailures() {
        return healthCheckFailures.get();
    }

    /**
     * Выполняет одиночную проверку: SELECT 1 к кластеру.
     * При сбое инициирует цикл переподключения.
     * Метод пакетного доступа для тестирования без запуска планировщика.
     */
    void runCheck() {
        IgniteClient client = manager.getIgniteClient();
        if (client == null) {
            long failures = healthCheckFailures.incrementAndGet();
            log.warn("IgniteHealthService: проверка ПРОВАЛЕНА — client == null (всего сбоев: {})", failures);
            scheduleReconnect();
            return;
        }
        try {
            try (var rs = client.sql().execute(null, "SELECT 1")) {
                if (rs.hasNext()) {
                    rs.next();
                }
            }
            if (reconnecting.compareAndSet(true, false)) {
                log.info("IgniteHealthService: Ignite 3 кластер восстановлен");
            } else {
                log.debug("IgniteHealthService: Ignite 3 кластер OK");
            }
        } catch (Exception e) {
            long failures = healthCheckFailures.incrementAndGet();
            log.warn("IgniteHealthService: проверка ПРОВАЛЕНА (всего сбоев: {}). Причина: {}",
                    failures, e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * Запускает цикл переподключения, если он ещё не активен.
     */
    private void scheduleReconnect() {
        if (reconnecting.compareAndSet(false, true)) {
            log.info("IgniteHealthService: запускаю цикл переподключения (интервал {}с)", RECONNECT_PERIOD_SEC);
            scheduler.schedule(this::attemptReconnect, RECONNECT_PERIOD_SEC, TimeUnit.SECONDS);
        }
    }

    /**
     * Одна попытка переподключения. При неудаче планирует следующую через
     * {@value #RECONNECT_PERIOD_SEC} секунд.
     */
    private void attemptReconnect() {
        log.info("IgniteHealthService: попытка переподключения к Ignite 3...");
        boolean ok = manager.reconnect();
        if (ok) {
            // Верифицируем, что новый клиент действительно отвечает
            try {
                IgniteClient fresh = manager.getIgniteClient();
                try (var rs = fresh.sql().execute(null, "SELECT 1")) {
                    if (rs.hasNext()) rs.next();
                }
                reconnecting.set(false);
                log.info("IgniteHealthService: переподключение успешно, кластер доступен");
            } catch (Exception e) {
                log.warn("IgniteHealthService: переподключение выполнено, но верификация не прошла: {} — повтор через {}с",
                        e.getMessage(), RECONNECT_PERIOD_SEC);
                scheduler.schedule(this::attemptReconnect, RECONNECT_PERIOD_SEC, TimeUnit.SECONDS);
            }
        } else {
            log.warn("IgniteHealthService: переподключение не удалось — повтор через {}с", RECONNECT_PERIOD_SEC);
            scheduler.schedule(this::attemptReconnect, RECONNECT_PERIOD_SEC, TimeUnit.SECONDS);
        }
    }
}
