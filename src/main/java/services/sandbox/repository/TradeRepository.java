package services.sandbox.repository;

import services.sandbox.model.TradeRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TradeRepository extends BaseRepository {

    public TradeRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, TradeRecord trade) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_trades (id, user_id, ticker, trade_side, qty, price, fee, trade_timestamp, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
                 "trade_side=EXCLUDED.trade_side, qty=EXCLUDED.qty, price=EXCLUDED.price, " +
                 "fee=EXCLUDED.fee, trade_timestamp=EXCLUDED.trade_timestamp, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, trade.getUserId());
            ps.setString(3, trade.getTicker());
            ps.setString(4, trade.getSide());
            ps.setInt(5, trade.getQty());
            ps.setDouble(6, trade.getPrice());
            ps.setDouble(7, trade.getFee());
            ps.setLong(8, trade.getTimestamp() != null ? trade.getTimestamp().toEpochMilli() : 0L);
            ps.setInt(9, trade.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("TradeRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public TradeRecord findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_trades WHERE id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("TradeRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<TradeRecord> findAll() {
        List<TradeRecord> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_trades");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("TradeRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public List<TradeRecord> findByUserId(String userId) {
        List<TradeRecord> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_trades WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("TradeRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_trades WHERE id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("TradeRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private TradeRecord mapRow(ResultSet rs) throws SQLException {
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
