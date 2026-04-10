package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.Tuple;
import services.sandbox.model.PriceAlert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Репозиторий ценовых алертов песочницы.
 *
 * <p>Поле {@code createdAt} хранится как BIGINT (epoch millis).
 */
public class PriceAlertRepository extends BaseIgniteRepository {

    private static final String TABLE_NAME = "sandbox_price_alerts";

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param clientSupplier поставщик актуального клиента Ignite 3
     */
    public PriceAlertRepository(Supplier<IgniteClient> clientSupplier) {
        super(clientSupplier, TABLE_NAME);
    }

    /**
     * Сохраняет ценовой алерт по ключу (alertId).
     */
    public void save(String key, PriceAlert alert) {
        Tuple k = Tuple.create().set("id", key);
        view().put(null, k, modelToRow(alert));
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
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
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
