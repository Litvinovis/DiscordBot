package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.LimitOrder;
import services.sandbox.model.TradeSide;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class LimitOrderRepository extends BaseRepository {

	private static final String UPSERT =
			"INSERT INTO sandbox_limit_orders (id, user_id, user_name, ticker, trade_side, qty, limit_price, created_at, schema_version) " +
			"VALUES (?,?,?,?,?,?,?,?,?) " +
			"ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, user_name=EXCLUDED.user_name, " +
			"ticker=EXCLUDED.ticker, trade_side=EXCLUDED.trade_side, qty=EXCLUDED.qty, " +
			"limit_price=EXCLUDED.limit_price, created_at=EXCLUDED.created_at, schema_version=EXCLUDED.schema_version";

	public LimitOrderRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, LimitOrder order) {
		jdbc.update(UPSERT,
				key,
				order.getUserId(),
				order.getUserName(),
				order.getTicker(),
				order.getSide().name(),
				order.getQty(),
				order.getLimitPrice(),
				order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : 0L,
				order.getSchemaVersion()
		);
	}

	public LimitOrder findById(String key) {
		List<LimitOrder> results = jdbc.query(
				"SELECT * FROM sandbox_limit_orders WHERE id = ?", this::mapRow, key);
		return results.isEmpty() ? null : results.getFirst();
	}

	public List<LimitOrder> findAll() {
		return jdbc.query("SELECT * FROM sandbox_limit_orders", this::mapRow);
	}

	public long countByUserAndTicker(String userId, String ticker) {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM sandbox_limit_orders WHERE user_id = ? AND ticker = ?",
				Long.class, userId, ticker);
		return count != null ? count : 0L;
	}

	public void delete(String key) {
		jdbc.update("DELETE FROM sandbox_limit_orders WHERE id = ?", key);
	}

	private LimitOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
		LimitOrder order = new LimitOrder(
				rs.getString("id"),
				rs.getString("user_id"),
				rs.getString("user_name"),
				rs.getString("ticker"),
				TradeSide.valueOf(rs.getString("trade_side")),
				rs.getInt("qty"),
				rs.getDouble("limit_price"),
				Instant.ofEpochMilli(rs.getLong("created_at"))
		);
		order.setSchemaVersion(rs.getInt("schema_version"));
		return order;
	}
}
