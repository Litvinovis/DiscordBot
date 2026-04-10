package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.Tuple;
import services.sandbox.model.TradeRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Репозиторий истории сделок песочницы.
 *
 * <p>Поле {@code timestamp} (Instant) хранится как BIGINT (epoch millis).
 * Поле {@code side} хранится как {@code trade_side} (во избежание конфликта с зарезервированным словом).
 */
public class TradeRepository extends BaseIgniteRepository {

    private static final String TABLE_NAME = "sandbox_trades";

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param clientSupplier поставщик актуального клиента Ignite 3
     */
    public TradeRepository(Supplier<IgniteClient> clientSupplier) {
        super(clientSupplier, TABLE_NAME);
    }

    /**
     * Сохраняет запись о сделке по ключу (tradeId).
     */
    public void save(String key, TradeRecord trade) {
        Tuple k = Tuple.create().set("id", key);
        view().put(null, k, modelToRow(trade));
    }

    /**
     * Возвращает запись о сделке по id или {@code null}, если не найдена.
     */
    public TradeRecord findById(String key) {
        Tuple k = Tuple.create().set("id", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает все сделки через SQL SELECT.
     */
    public List<TradeRecord> findAll() {
        List<TradeRecord> result = new ArrayList<>();
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String id = row.stringValue("ID");
                TradeRecord trade = new TradeRecord(
                        id,
                        row.stringValue("USER_ID"),
                        row.stringValue("TICKER"),
                        row.stringValue("TRADE_SIDE"),
                        row.intValue("QTY"),
                        row.doubleValue("PRICE"),
                        row.doubleValue("FEE"),
                        Instant.ofEpochMilli(row.longValue("TRADE_TIMESTAMP"))
                );
                trade.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(trade);
            }
        } catch (Exception e) {
            log.error("TradeRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Возвращает все сделки указанного пользователя через SQL.
     */
    public List<TradeRecord> findByUserId(String userId) {
        List<TradeRecord> result = new ArrayList<>();
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null,
                "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ?", userId)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String id = row.stringValue("ID");
                TradeRecord trade = new TradeRecord(
                        id,
                        userId,
                        row.stringValue("TICKER"),
                        row.stringValue("TRADE_SIDE"),
                        row.intValue("QTY"),
                        row.doubleValue("PRICE"),
                        row.doubleValue("FEE"),
                        Instant.ofEpochMilli(row.longValue("TRADE_TIMESTAMP"))
                );
                trade.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(trade);
            }
        } catch (Exception e) {
            log.error("TradeRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет сделку по id.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("id", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private TradeRecord rowToModel(String key, Tuple row) {
        TradeRecord trade = new TradeRecord(
                key,
                row.stringValue("user_id"),
                row.stringValue("ticker"),
                row.stringValue("trade_side"),
                row.intValue("qty"),
                row.doubleValue("price"),
                row.doubleValue("fee"),
                Instant.ofEpochMilli(row.longValue("trade_timestamp"))
        );
        trade.setSchemaVersion(row.intValue("schema_version"));
        return trade;
    }

    private Tuple modelToRow(TradeRecord trade) {
        return Tuple.create()
                .set("user_id", trade.getUserId())
                .set("ticker", trade.getTicker())
                .set("trade_side", trade.getSide())
                .set("qty", trade.getQty())
                .set("price", trade.getPrice())
                .set("fee", trade.getFee())
                .set("trade_timestamp", trade.getTimestamp() != null ? trade.getTimestamp().toEpochMilli() : 0L)
                .set("schema_version", trade.getSchemaVersion());
    }
}
