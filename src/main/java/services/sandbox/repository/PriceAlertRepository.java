package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.PriceAlert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class PriceAlertRepository extends BaseRepository {

	private static final String UPSERT =
			"INSERT INTO sandbox_price_alerts (id, user_id, ticker, target_price, above, created_at, schema_version) " +
			"VALUES (?,?,?,?,?,?,?) " +
			"ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
			"target_price=EXCLUDED.target_price, above=EXCLUDED.above, " +
			"created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version";

	public PriceAlertRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, PriceAlert alert) {
		jdbc.update(UPSERT,
				key,
				alert.getUserId(),
				alert.getTicker(),
				alert.getTargetPrice(),
				alert.isAbove(),
				alert.getCreatedAt() != null ? alert.getCreatedAt().toEpochMilli() : 0L,
				alert.getSchemaVersion()
		);
	}

	public PriceAlert findById(String key) {
		List<PriceAlert> results = jdbc.query(
				"SELECT * FROM sandbox_price_alerts WHERE id = ?", this::mapRow, key);
		return results.isEmpty() ? null : results.getFirst();
	}

	public List<PriceAlert> findAll() {
		return jdbc.query("SELECT * FROM sandbox_price_alerts", this::mapRow);
	}

	public void delete(String key) {
		jdbc.update("DELETE FROM sandbox_price_alerts WHERE id = ?", key);
	}

	private PriceAlert mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new PriceAlert(
				rs.getString("id"),
				rs.getString("user_id"),
				rs.getString("ticker"),
				nz(rs.getBigDecimal("target_price")),
				rs.getBoolean("above"),
				Instant.ofEpochMilli(rs.getLong("created_at"))
		);
	}
}
