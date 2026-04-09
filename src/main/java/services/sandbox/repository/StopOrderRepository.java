package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import java.util.function.Supplier;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.model.StopOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий стоп-ордеров (SL/TP) песочницы.
 *
 * <p>Поле {@code type} хранится как {@code order_type} (SQL-зарезервированное слово).
 * Поле {@code createdAt} — как BIGINT (epoch millis).
 */
public class StopOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(StopOrderRepository.class);
    private static final String TABLE_NAME = "sandbox_stop_orders";

    private final Supplier<IgniteClient> clientSupplier;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> kvView;

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param igniteClient подключённый клиент Ignite 3
     */
    public StopOrderRepository(Supplier<IgniteClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    private KeyValueView<Tuple, Tuple> view() {
        IgniteClient current = clientSupplier.get();
        if (current == null) {
            throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
        }
        if (kvView == null || current != lastClient) {
            synchronized (this) {
                current = clientSupplier.get();
                if (current == null) {
                    throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
                }
                if (kvView == null || current != lastClient) {
                    kvView = current.tables().table(TABLE_NAME).keyValueView();
                    lastClient = current;
                }
            }
        }
        return kvView;
    }

    /**
     * Сохраняет стоп-ордер по ключу (orderId).
     */
    public void save(String key, StopOrder order) {
        Tuple k = Tuple.create().set("id", key);
        Tuple v = modelToRow(order);
        view().put(null, k, v);
    }

    /**
     * Возвращает стоп-ордер по id или {@code null}.
     */
    public StopOrder findById(String key) {
        Tuple k = Tuple.create().set("id", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает все стоп-ордера через SQL SELECT.
     */
    public List<StopOrder> findAll() {
        List<StopOrder> result = new ArrayList<>();
        IgniteClient _client = clientSupplier.get();
        if (_client == null) return result;
        try (var rs = _client.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String id = row.stringValue("ID");
                StopOrder order = new StopOrder(
                        id,
                        row.stringValue("USER_ID"),
                        row.stringValue("TICKER"),
                        row.stringValue("ORDER_TYPE"),
                        row.doubleValue("TRIGGER_PRICE"),
                        Instant.ofEpochMilli(row.longValue("CREATED_AT"))
                );
                order.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(order);
            }
        } catch (Exception e) {
            log.error("StopOrderRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет стоп-ордер по id.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("id", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private StopOrder rowToModel(String key, Tuple row) {
        StopOrder order = new StopOrder(
                key,
                row.stringValue("user_id"),
                row.stringValue("ticker"),
                row.stringValue("order_type"),
                row.doubleValue("trigger_price"),
                Instant.ofEpochMilli(row.longValue("created_at"))
        );
        order.setSchemaVersion(row.intValue("schema_version"));
        return order;
    }

    private Tuple modelToRow(StopOrder order) {
        return Tuple.create()
                .set("order_type", order.getType())
                .set("user_id", order.getUserId())
                .set("ticker", order.getTicker())
                .set("trigger_price", order.getTriggerPrice())
                .set("created_at", order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L)
                .set("schema_version", order.getSchemaVersion());
    }
}
