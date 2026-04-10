package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.Tuple;
import services.sandbox.model.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Репозиторий открытых позиций пользователей песочницы.
 *
 * <p>Первичный ключ: составная строка {@code userId::ticker} (position_key).
 */
public class PositionRepository extends BaseIgniteRepository {

    private static final String TABLE_NAME = "sandbox_positions";

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     *
     * @param clientSupplier поставщик актуального клиента Ignite 3
     */
    public PositionRepository(Supplier<IgniteClient> clientSupplier) {
        super(clientSupplier, TABLE_NAME);
    }

    /**
     * Сохраняет позицию по составному ключу {@code positionKey}.
     */
    public void save(String key, Position position) {
        Tuple k = Tuple.create().set("position_key", key);
        view().put(null, k, modelToRow(position));
    }

    /**
     * Возвращает позицию по ключу или {@code null}, если не найдена.
     */
    public Position findById(String key) {
        Tuple k = Tuple.create().set("position_key", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает список всех позиций через SQL SELECT.
     */
    public List<Position> findAll() {
        List<Position> result = new ArrayList<>();
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                Position pos = new Position();
                pos.setUserId(row.stringValue("USER_ID"));
                pos.setTicker(row.stringValue("TICKER"));
                pos.setInstrumentId(row.stringValue("INSTRUMENT_ID"));
                pos.setQuantity(row.intValue("QUANTITY"));
                pos.setAvgPrice(row.doubleValue("AVG_PRICE"));
                pos.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(pos);
            }
        } catch (Exception e) {
            log.error("PositionRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Возвращает все позиции указанного пользователя через SQL.
     */
    public List<Position> findByUserId(String userId) {
        List<Position> result = new ArrayList<>();
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null,
                "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ?", userId)) {
            while (rs.hasNext()) {
                var row = rs.next();
                Position pos = new Position();
                pos.setUserId(userId);
                pos.setTicker(row.stringValue("TICKER"));
                pos.setInstrumentId(row.stringValue("INSTRUMENT_ID"));
                pos.setQuantity(row.intValue("QUANTITY"));
                pos.setAvgPrice(row.doubleValue("AVG_PRICE"));
                pos.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(pos);
            }
        } catch (Exception e) {
            log.error("PositionRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет позицию по ключу.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("position_key", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private Position rowToModel(String key, Tuple row) {
        Position pos = new Position();
        pos.setUserId(row.stringValue("user_id"));
        pos.setTicker(row.stringValue("ticker"));
        pos.setInstrumentId(row.stringValue("instrument_id"));
        pos.setQuantity(row.intValue("quantity"));
        pos.setAvgPrice(row.doubleValue("avg_price"));
        pos.setSchemaVersion(row.intValue("schema_version"));
        return pos;
    }

    private Tuple modelToRow(Position pos) {
        return Tuple.create()
                .set("user_id", pos.getUserId())
                .set("ticker", pos.getTicker())
                .set("instrument_id", pos.getInstrumentId())
                .set("quantity", pos.getQuantity())
                .set("avg_price", pos.getAvgPrice())
                .set("schema_version", pos.getSchemaVersion());
    }
}
