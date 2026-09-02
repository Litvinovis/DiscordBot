package services.sandbox.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.SandboxUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SandboxUserRepository extends BaseRepository {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String UPSERT =
			"INSERT INTO sandbox_users (user_id, user_name, cash, borrowed, total_fees, " +
			"daily_baseline_date, daily_baseline_equity, weekly_baseline_date, weekly_baseline_equity, " +
			"monthly_baseline_date, monthly_baseline_equity, currency_holdings, schema_version, last_replenish_date, morning_digest_enabled) " +
			"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
			"ON CONFLICT (user_id) DO UPDATE SET user_name=EXCLUDED.user_name, cash=EXCLUDED.cash, " +
			"borrowed=EXCLUDED.borrowed, total_fees=EXCLUDED.total_fees, " +
			"daily_baseline_date=EXCLUDED.daily_baseline_date, daily_baseline_equity=EXCLUDED.daily_baseline_equity, " +
			"weekly_baseline_date=EXCLUDED.weekly_baseline_date, weekly_baseline_equity=EXCLUDED.weekly_baseline_equity, " +
			"monthly_baseline_date=EXCLUDED.monthly_baseline_date, monthly_baseline_equity=EXCLUDED.monthly_baseline_equity, " +
			"currency_holdings=EXCLUDED.currency_holdings, schema_version=EXCLUDED.schema_version, " +
			"last_replenish_date=EXCLUDED.last_replenish_date, morning_digest_enabled=EXCLUDED.morning_digest_enabled";

	public SandboxUserRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	// Thread-safety: no in-memory cache; all persistence is via PostgreSQL ON CONFLICT DO UPDATE
	// which provides atomic upsert at the DB level. No additional synchronization needed here.
	public void save(String key, SandboxUser user) {
		jdbc.update(UPSERT,
				key,
				user.getUserName(),
				user.getCash(),
				user.getBorrowed(),
				user.getTotalFees(),
				user.getDailyBaselineDate(),
				user.getDailyBaselineEquity(),
				user.getWeeklyBaselineDate(),
				user.getWeeklyBaselineEquity(),
				user.getMonthlyBaselineDate(),
				user.getMonthlyBaselineEquity(),
				serializeHoldings(user.getCurrencyHoldings()),
				user.getSchemaVersion(),
				user.getLastReplenishDate(),
				user.isMorningDigestEnabled()
		);
	}

	public SandboxUser findById(String key) {
		List<SandboxUser> results = jdbc.query(
				"SELECT * FROM sandbox_users WHERE user_id = ?", this::mapRow, key);
		return results.isEmpty() ? null : results.getFirst();
	}

	public List<SandboxUser> findAll() {
		return jdbc.query("SELECT * FROM sandbox_users", this::mapRow);
	}

	public void delete(String key) {
		jdbc.update("DELETE FROM sandbox_users WHERE user_id = ?", key);
	}

	private SandboxUser mapRow(ResultSet rs, int rowNum) throws SQLException {
		SandboxUser user = new SandboxUser();
		user.setUserId(rs.getString("user_id"));
		user.setUserName(rs.getString("user_name"));
		user.setCash(nz(rs.getBigDecimal("cash")));
		user.setBorrowed(nz(rs.getBigDecimal("borrowed")));
		user.setTotalFees(nz(rs.getBigDecimal("total_fees")));
		user.setDailyBaselineDate(rs.getObject("daily_baseline_date", LocalDate.class));
		user.setDailyBaselineEquity(nz(rs.getBigDecimal("daily_baseline_equity")));
		user.setWeeklyBaselineDate(rs.getObject("weekly_baseline_date", LocalDate.class));
		user.setWeeklyBaselineEquity(nz(rs.getBigDecimal("weekly_baseline_equity")));
		user.setMonthlyBaselineDate(rs.getObject("monthly_baseline_date", LocalDate.class));
		user.setMonthlyBaselineEquity(nz(rs.getBigDecimal("monthly_baseline_equity")));
		user.setCurrencyHoldings(parseHoldings(rs.getString("currency_holdings")));
		user.setSchemaVersion(rs.getInt("schema_version"));
		user.setLastReplenishDate(rs.getObject("last_replenish_date", LocalDate.class));
		user.setMorningDigestEnabled(rs.getBoolean("morning_digest_enabled"));
		return user;
	}

	private Map<String, BigDecimal> parseHoldings(String json) {
		if (json == null || json.isBlank()) return new HashMap<>();
		try {
			return MAPPER.readValue(json, new TypeReference<Map<String, BigDecimal>>() {});
		} catch (Exception e) {
			// Повреждённая запись не должна делать пользователя нечитаемым целиком
			log.warn("Не удалось разобрать currency_holdings ({}), валютные остатки прочитаны как пустые: {}",
					json, e.getMessage());
			return new HashMap<>();
		}
	}

	private String serializeHoldings(Map<String, BigDecimal> holdings) {
		if (holdings == null || holdings.isEmpty()) return "{}";
		try {
			return MAPPER.writeValueAsString(holdings);
		} catch (Exception e) {
			// Раньше сюда подставлялся "{}" — валютные остатки молча обнулялись в БД
			throw new IllegalStateException("Не удалось сериализовать валютные остатки", e);
		}
	}
}
