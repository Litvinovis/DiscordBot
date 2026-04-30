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
		try {
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
		} catch (Exception e) {
			log.error("LimitOrderRepository.save({}) failed: {}", key, e.getMessage(), e);
		}
	}

	public LimitOrder findById(String key) {
		try {
			List<LimitOrder> results = jdbc.query(
					"SELECT * FROM sandbox_limit_orders WHERE id = ?", this::mapRow, key);
			return results.isEmpty() ? null : results.getFirst();
		} catch (Exception e) {
			log.error("LimitOrderRepository.findById({}) failed: {}", key, e.getMessage(), e);
			return null;
		}
	}

	public List<LimitOrder> findAll() {
		try {
			return jdbc.query("SELECT * FROM sandbox_limit_orders", this::mapRow);
		} catch (Exception e) {
			log.error("LimitOrderRepository.findAll() failed: {}", e.getMessage(), e);
			return List.of();
		}
	}

	public void delete(String key) {
		try {
			jdbc.update("DELETE FROM sandbox_limit_orders WHERE id = ?", key);
		} catch (Exception e) {
			log.error("LimitOrderRepository.delete({}) failed: {}", key, e.getMessage(), e);
		}
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
