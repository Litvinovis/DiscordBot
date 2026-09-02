package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.StopOrder;
import services.sandbox.model.StopOrderType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class StopOrderRepository extends BaseRepository {

	private static final String UPSERT =
			"INSERT INTO sandbox_stop_orders (id, order_type, user_id, ticker, trigger_price, created_at, schema_version) " +
			"VALUES (?,?,?,?,?,?,?) " +
			"ON CONFLICT (id) DO UPDATE SET order_type=EXCLUDED.order_type, user_id=EXCLUDED.user_id, " +
			"ticker=EXCLUDED.ticker, trigger_price=EXCLUDED.trigger_price, " +
			"created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version";

	public StopOrderRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, StopOrder order) {
		jdbc.update(UPSERT,
				key,
				order.getType().name(),
				order.getUserId(),
				order.getTicker(),
				order.getTriggerPrice(),
				order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L,
				order.getSchemaVersion()
		);
	}

	public StopOrder findById(String key) {
		List<StopOrder> results = jdbc.query(
				"SELECT * FROM sandbox_stop_orders WHERE id = ?", this::mapRow, key);
		return results.isEmpty() ? null : results.getFirst();
	}

	public List<StopOrder> findAll() {
		return jdbc.query("SELECT * FROM sandbox_stop_orders", this::mapRow);
	}

	public void delete(String key) {
		jdbc.update("DELETE FROM sandbox_stop_orders WHERE id = ?", key);
	}

	public void deleteByUserAndTicker(String userId, String ticker) {
		jdbc.update("DELETE FROM sandbox_stop_orders WHERE user_id = ? AND ticker = ?", userId, ticker);
	}

	private StopOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new StopOrder(
				rs.getString("id"),
				rs.getString("user_id"),
				rs.getString("ticker"),
				StopOrderType.valueOf(rs.getString("order_type")),
				nz(rs.getBigDecimal("trigger_price")),
				Instant.ofEpochMilli(rs.getLong("created_at"))
		);
	}
}
