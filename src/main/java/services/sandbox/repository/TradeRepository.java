package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.TradeRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class TradeRepository extends BaseRepository {

    private static final String UPSERT =
            "INSERT INTO sandbox_trades (id, user_id, ticker, trade_side, qty, price, fee, trade_timestamp, schema_version) " +
            "VALUES (?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
            "trade_side=EXCLUDED.trade_side, qty=EXCLUDED.qty, price=EXCLUDED.price, " +
            "fee=EXCLUDED.fee, trade_timestamp=EXCLUDED.trade_timestamp, schema_version=EXCLUDED.schema_version";

    public TradeRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(String key, TradeRecord trade) {
        try {
            jdbc.update(UPSERT,
                    key,
                    trade.getUserId(),
                    trade.getTicker(),
                    trade.getSide(),
                    trade.getQty(),
                    trade.getPrice(),
                    trade.getFee(),
                    trade.getTimestamp() != null ? trade.getTimestamp().toEpochMilli() : 0L,
                    trade.getSchemaVersion()
            );
        } catch (Exception e) {
            log.error("TradeRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public TradeRecord findById(String key) {
        try {
            List<TradeRecord> results = jdbc.query(
                    "SELECT * FROM sandbox_trades WHERE id = ?", this::mapRow, key);
            return results.isEmpty() ? null : results.getFirst();
        } catch (Exception e) {
            log.error("TradeRepository.findById({}) failed: {}", key, e.getMessage(), e);
            return null;
        }
    }

    public List<TradeRecord> findAll() {
        try {
            return jdbc.query("SELECT * FROM sandbox_trades", this::mapRow);
        } catch (Exception e) {
            log.error("TradeRepository.findAll() failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public List<TradeRecord> findByUserId(String userId) {
        try {
            return jdbc.query("SELECT * FROM sandbox_trades WHERE user_id = ?", this::mapRow, userId);
        } catch (Exception e) {
            log.error("TradeRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    public void delete(String key) {
        try {
            jdbc.update("DELETE FROM sandbox_trades WHERE id = ?", key);
        } catch (Exception e) {
            log.error("TradeRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private TradeRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        TradeRecord trade = new TradeRecord(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("ticker"),
                rs.getString("trade_side"),
                rs.getInt("qty"),
                rs.getDouble("price"),
                rs.getDouble("fee"),
                Instant.ofEpochMilli(rs.getLong("trade_timestamp"))
        );
        trade.setSchemaVersion(rs.getInt("schema_version"));
        return trade;
    }
}
