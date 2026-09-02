package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.TradeSide;
import services.sandbox.model.TradeRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class TradeRepository extends BaseRepository {

	private static final String UPSERT =
			"INSERT INTO sandbox_trades (id, user_id, ticker, trade_side, qty, price, fee, trade_timestamp, schema_version) " +
			"VALUES (?,?,?,?,?,?,?,?,?) " +
			"ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, ticker=EXCLUDED.ticker, " +
			"trade_side=EXCLUDED.trade_side, qty=EXCLUDED.qty, price=EXCLUDED.price, " +
			"fee=EXCLUDED.fee, trade_timestamp=EXCLUDED.trade_timestamp, schema_version=EXCLUDED.schema_version";

	public TradeRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, TradeRecord trade) {
		jdbc.update(UPSERT,
				key,
				trade.getUserId(),
				trade.getTicker(),
				trade.getSide().name(),
				trade.getQty(),
				trade.getPrice(),
				trade.getFee(),
				trade.getTimestamp() != null ? trade.getTimestamp().toEpochMilli() : 0L,
				trade.getSchemaVersion()
		);
	}

	public TradeRecord findById(String key) {
		List<TradeRecord> results = jdbc.query(
				"SELECT * FROM sandbox_trades WHERE id = ?", this::mapRow, key);
		return results.isEmpty() ? null : results.getFirst();
	}

	public List<TradeRecord> findAll() {
		return jdbc.query("SELECT * FROM sandbox_trades", this::mapRow);
	}

	public List<TradeRecord> findByUserId(String userId) {
		return jdbc.query("SELECT * FROM sandbox_trades WHERE user_id = ?", this::mapRow, userId);
	}

	public void delete(String key) {
		jdbc.update("DELETE FROM sandbox_trades WHERE id = ?", key);
	}

	private TradeRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
		TradeRecord trade = new TradeRecord(
				rs.getString("id"),
				rs.getString("user_id"),
				rs.getString("ticker"),
				TradeSide.valueOf(rs.getString("trade_side")),
				rs.getInt("qty"),
				nz(rs.getBigDecimal("price")),
				nz(rs.getBigDecimal("fee")),
				Instant.ofEpochMilli(rs.getLong("trade_timestamp"))
		);
		trade.setSchemaVersion(rs.getInt("schema_version"));
		return trade;
	}
}
