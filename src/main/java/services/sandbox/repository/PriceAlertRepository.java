package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import java.util.function.Supplier;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.model.PriceAlert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий ценовых алертов песочницы.
 *
 * <p>Поле {@code createdAt} хранится как BIGINT (epoch millis).
 */
public class PriceAlertRepository {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertRepository.class);
    private static final String TABLE_NAME = "sandbox_price_alerts";

    private final Supplier<IgniteClient> clientSupplier;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> kvView;

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param igniteClient подключённый клиент Ignite 3
     */
    public PriceAlertRepository(Supplier<IgniteClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    private KeyValueView<Tuple, Tuple> view() {
        IgniteClient current = clientSupplier.get();
        if (kvView == null || current != lastClient) {
            synchronized (this) {
                current = clientSupplier.get();
                if (kvView == null || current != lastClient) {
                    kvView = current.tables().table(TABLE_NAME).keyValueView();
                    lastClient = current;
                }
            }
        }
        return kvView;
    }

    /**
     * Сохраняет ценовой алерт по ключу (alertId).
     */
    public void save(String key, PriceAlert alert) {
        Tuple k = Tuple.create().set("id", key);
        Tuple v = modelToRow(alert);
        view().put(null, k, v);
    }

    /**
     * Возвращает ценовой алерт по id или {@code null}.
     */
    public PriceAlert findById(String key) {
        Tuple k = Tuple.create().set("id", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает все ценовые алерты через SQL SELECT.
     */
    public List<PriceAlert> findAll() {
        List<PriceAlert> result = new ArrayList<>();
        try (var rs = clientSupplier.get().sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String id = row.stringValue("ID");
                PriceAlert alert = new PriceAlert(
                        id,
                        row.stringValue("USER_ID"),
                        row.stringValue("TICKER"),
                        row.doubleValue("TARGET_PRICE"),
                        row.booleanValue("ABOVE"),
                        Instant.ofEpochMilli(row.longValue("CREATED_AT"))
                );
                alert.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(alert);
            }
        } catch (Exception e) {
            log.error("PriceAlertRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет ценовой алерт по id.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("id", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private PriceAlert rowToModel(String key, Tuple row) {
        PriceAlert alert = new PriceAlert(
                key,
                row.stringValue("user_id"),
                row.stringValue("ticker"),
                row.doubleValue("target_price"),
                row.booleanValue("above"),
                Instant.ofEpochMilli(row.longValue("created_at"))
        );
        alert.setSchemaVersion(row.intValue("schema_version"));
        return alert;
    }

    private Tuple modelToRow(PriceAlert alert) {
        return Tuple.create()
                .set("user_id", alert.getUserId())
                .set("ticker", alert.getTicker())
                .set("target_price", alert.getTargetPrice())
                .set("above", alert.isAbove())
                .set("created_at", alert.getCreatedAt() != null ? alert.getCreatedAt().toEpochMilli() : 0L)
                .set("schema_version", alert.getSchemaVersion());
    }
}
