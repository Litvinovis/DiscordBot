package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.Position;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class PositionRepository extends BaseRepository {

	private static final String UPSERT =
			"INSERT INTO sandbox_positions (position_key, user_id, ticker, instrument_id, quantity, avg_price, schema_version) " +
			"VALUES (?,?,?,?,?,?,?) " +
			"ON CONFLICT (position_key) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
			"instrument_id=EXCLUDED.instrument_id, quantity=EXCLUDED.quantity, " +
			"avg_price=EXCLUDED.avg_price, schema_version=EXCLUDED.schema_version";

	public PositionRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, Position position) {
		try {
			jdbc.update(UPSERT,
					key,
					position.getUserId(),
					position.getTicker(),
					position.getInstrumentId(),
					position.getQuantity(),
					position.getAvgPrice(),
					position.getSchemaVersion()
			);
		} catch (Exception e) {
			log.error("PositionRepository.save({}) failed: {}", key, e.getMessage(), e);
		}
	}

	public Position findById(String key) {
		try {
			List<Position> results = jdbc.query(
					"SELECT * FROM sandbox_positions WHERE position_key = ?", this::mapRow, key);
			return results.isEmpty() ? null : results.getFirst();
		} catch (Exception e) {
			log.error("PositionRepository.findById({}) failed: {}", key, e.getMessage(), e);
			return null;
		}
	}

	public List<Position> findAll() {
		try {
			return jdbc.query("SELECT * FROM sandbox_positions", this::mapRow);
		} catch (Exception e) {
			log.error("PositionRepository.findAll() failed: {}", e.getMessage(), e);
			return List.of();
		}
	}

	public List<Position> findByUserId(String userId) {
		try {
			return jdbc.query("SELECT * FROM sandbox_positions WHERE user_id = ?", this::mapRow, userId);
		} catch (Exception e) {
			log.error("PositionRepository.findByUserId({}) failed: {}", userId, e.getMessage(), e);
			return List.of();
		}
	}

	public void delete(String key) {
		try {
			jdbc.update("DELETE FROM sandbox_positions WHERE position_key = ?", key);
		} catch (Exception e) {
			log.error("PositionRepository.delete({}) failed: {}", key, e.getMessage(), e);
		}
	}

	private Position mapRow(ResultSet rs, int rowNum) throws SQLException {
		Position p = new Position(
				rs.getString("user_id"),
				rs.getString("ticker"),
				rs.getString("instrument_id"),
				rs.getInt("quantity"),
				rs.getDouble("avg_price")
		);
		p.setSchemaVersion(rs.getInt("schema_version"));
		return p;
	}
}
