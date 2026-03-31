package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.sandbox.model.LimitOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий активных лимитных заявок песочницы.
 *
 * <p>Поле {@code side} хранится как {@code trade_side}, {@code createdAt} — как BIGINT (epoch millis).
 */
public class LimitOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(LimitOrderRepository.class);
    private static final String TABLE_NAME = "sandbox_limit_orders";

    private final IgniteClient igniteClient;
    private volatile KeyValueView<Tuple, Tuple> kvView;

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param igniteClient подключённый клиент Ignite 3
     */
    public LimitOrderRepository(IgniteClient igniteClient) {
        this.igniteClient = igniteClient;
    }

    private KeyValueView<Tuple, Tuple> view() {
        if (kvView == null) {
            kvView = igniteClient.tables().table(TABLE_NAME).keyValueView();
        }
        return kvView;
    }

    /**
     * Сохраняет лимитную заявку по ключу (orderId).
     */
    public void save(String key, LimitOrder order) {
        Tuple k = Tuple.create().set("id", key);
        Tuple v = modelToRow(order);
        view().put(null, k, v);
    }

    /**
     * Возвращает лимитную заявку по id или {@code null}.
     */
    public LimitOrder findById(String key) {
        Tuple k = Tuple.create().set("id", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает все лимитные заявки через SQL SELECT.
     */
    public List<LimitOrder> findAll() {
        List<LimitOrder> result = new ArrayList<>();
        try (var rs = igniteClient.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String id = row.stringValue("ID");
                LimitOrder order = new LimitOrder(
                        id,
                        row.stringValue("USER_ID"),
                        row.stringValue("USER_NAME"),
                        row.stringValue("TICKER"),
                        row.stringValue("TRADE_SIDE"),
                        row.intValue("QTY"),
                        row.doubleValue("LIMIT_PRICE"),
                        Instant.ofEpochMilli(row.longValue("CREATED_AT"))
                );
                order.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(order);
            }
        } catch (Exception e) {
            log.error("LimitOrderRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет лимитную заявку по id.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("id", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private LimitOrder rowToModel(String key, Tuple row) {
        LimitOrder order = new LimitOrder(
                key,
                row.stringValue("user_id"),
                row.stringValue("user_name"),
                row.stringValue("ticker"),
                row.stringValue("trade_side"),
                row.intValue("qty"),
                row.doubleValue("limit_price"),
                Instant.ofEpochMilli(row.longValue("created_at"))
        );
        order.setSchemaVersion(row.intValue("schema_version"));
        return order;
    }

    private Tuple modelToRow(LimitOrder order) {
        return Tuple.create()
                .set("user_id", order.getUserId())
                .set("user_name", order.getUserName())
                .set("ticker", order.getTicker())
                .set("trade_side", order.getSide())
                .set("qty", order.getQty())
                .set("limit_price", order.getLimitPrice())
                .set("created_at", order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L)
                .set("schema_version", order.getSchemaVersion());
    }
}
