package services.sandbox.repository;

import services.sandbox.model.StopOrder;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StopOrderRepository extends BaseIgniteRepository {

    public StopOrderRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, StopOrder order) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_stop_orders (id, order_type, user_id, ticker, trigger_price, created_at, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET order_type=EXCLUDED.order_type, user_id=EXCLUDED.user_id, " +
                 "ticker=EXCLUDED.ticker, trigger_price=EXCLUDED.trigger_price, " +
                 "created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, order.getType());
            ps.setString(3, order.getUserId());
            ps.setString(4, order.getTicker());
            ps.setDouble(5, order.getTriggerPrice());
            ps.setLong(6, order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L);
            ps.setInt(7, order.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("StopOrderRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public StopOrder findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_stop_orders WHERE id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("StopOrderRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<StopOrder> findAll() {
        List<StopOrder> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_stop_orders");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("StopOrderRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_stop_orders WHERE id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("StopOrderRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private StopOrder mapRow(ResultSet rs) throws SQLException {
        StopOrder order = new StopOrder(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("ticker"),
                rs.getString("order_type"),
                rs.getDouble("trigger_price"),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
        order.setSchemaVersion(rs.getInt("schema_version"));
        return order;
    }
}
