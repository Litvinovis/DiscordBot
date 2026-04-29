package services.sandbox.repository;

import services.sandbox.model.Position;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PositionRepository extends BaseRepository {

    public PositionRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, Position position) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_positions (position_key, user_id, ticker, instrument_id, quantity, avg_price, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?) " +
                 "ON CONFLICT (position_key) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
                 "instrument_id=EXCLUDED.instrument_id, quantity=EXCLUDED.quantity, " +
                 "avg_price=EXCLUDED.avg_price, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, position.getUserId());
            ps.setString(3, position.getTicker());
            ps.setString(4, position.getInstrumentId());
            ps.setInt(5, position.getQuantity());
            ps.setDouble(6, position.getAvgPrice());
            ps.setInt(7, position.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("PositionRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public Position findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_positions WHERE position_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("PositionRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<Position> findAll() {
        List<Position> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_positions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("PositionRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public List<Position> findByUserId(String userId) {
        List<Position> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_positions WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("PositionRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_positions WHERE position_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("PositionRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private Position mapRow(ResultSet rs) throws SQLException {
        Position pos = new Position();
        pos.setUserId(rs.getString("user_id"));
        pos.setTicker(rs.getString("ticker"));
        pos.setInstrumentId(rs.getString("instrument_id"));
        pos.setQuantity(rs.getInt("quantity"));
        pos.setAvgPrice(rs.getDouble("avg_price"));
        pos.setSchemaVersion(rs.getInt("schema_version"));
        return pos;
    }
}
