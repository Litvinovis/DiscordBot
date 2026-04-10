package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.Tuple;
import services.sandbox.model.LimitOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Репозиторий активных лимитных заявок песочницы.
 *
 * <p>Поле {@code side} хранится как {@code trade_side},
 * {@code createdAt} — как BIGINT (epoch millis).
 */
public class LimitOrderRepository extends BaseIgniteRepository {

    private static final String TABLE_NAME = "sandbox_limit_orders";

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param clientSupplier поставщик актуального клиента Ignite 3
     */
    public LimitOrderRepository(Supplier<IgniteClient> clientSupplier) {
        super(clientSupplier, TABLE_NAME);
    }

    /**
     * Сохраняет лимитную заявку по ключу (orderId).
     */
    public void save(String key, LimitOrder order) {
        Tuple k = Tuple.create().set("id", key);
        view().put(null, k, modelToRow(order));
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
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
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
