package services.sandbox.repository;

import services.sandbox.model.PriceAlert;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PriceAlertRepository extends BaseRepository {

    public PriceAlertRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, PriceAlert alert) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_price_alerts (id, user_id, ticker, target_price, above, created_at, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
                 "target_price=EXCLUDED.target_price, above=EXCLUDED.above, " +
                 "created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, alert.getUserId());
            ps.setString(3, alert.getTicker());
            ps.setDouble(4, alert.getTargetPrice());
            ps.setBoolean(5, alert.isAbove());
            ps.setLong(6, alert.getCreatedAt() != null ? alert.getCreatedAt().toEpochMilli() : 0L);
            ps.setInt(7, alert.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("PriceAlertRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public PriceAlert findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_price_alerts WHERE id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("PriceAlertRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<PriceAlert> findAll() {
        List<PriceAlert> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_price_alerts");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("PriceAlertRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_price_alerts WHERE id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("PriceAlertRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private PriceAlert mapRow(ResultSet rs) throws SQLException {
        PriceAlert alert = new PriceAlert(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("ticker"),
                rs.getDouble("target_price"),
                rs.getBoolean("above"),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
        alert.setSchemaVersion(rs.getInt("schema_version"));
        return alert;
    }
}
