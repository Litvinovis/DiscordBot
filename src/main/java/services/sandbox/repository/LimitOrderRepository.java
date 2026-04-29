package services.sandbox.repository;

import services.sandbox.model.LimitOrder;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LimitOrderRepository extends BaseRepository {

    public LimitOrderRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, LimitOrder order) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_limit_orders (id, user_id, user_name, ticker, trade_side, qty, limit_price, created_at, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, user_name=EXCLUDED.user_name, " +
                 "ticker=EXCLUDED.ticker, trade_side=EXCLUDED.trade_side, qty=EXCLUDED.qty, " +
                 "limit_price=EXCLUDED.limit_price, created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, order.getUserId());
            ps.setString(3, order.getUserName());
            ps.setString(4, order.getTicker());
            ps.setString(5, order.getSide());
            ps.setInt(6, order.getQty());
            ps.setDouble(7, order.getLimitPrice());
            ps.setLong(8, order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L);
            ps.setInt(9, order.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("LimitOrderRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public LimitOrder findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_limit_orders WHERE id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("LimitOrderRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<LimitOrder> findAll() {
        List<LimitOrder> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_limit_orders");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("LimitOrderRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_limit_orders WHERE id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("LimitOrderRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private LimitOrder mapRow(ResultSet rs) throws SQLException {
        LimitOrder order = new LimitOrder(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("user_name"),
                rs.getString("ticker"),
                rs.getString("trade_side"),
                rs.getInt("qty"),
                rs.getDouble("limit_price"),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
        order.setSchemaVersion(rs.getInt("schema_version"));
        return order;
    }
}
