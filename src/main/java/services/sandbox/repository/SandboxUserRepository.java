package services.sandbox.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.SandboxUser;

import java.sql.ResultSet;
import java.sql.SQLException;
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
			"monthly_baseline_date, monthly_baseline_equity, currency_holdings, schema_version, last_replenish_date) " +
			"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
			"ON CONFLICT (user_id) DO UPDATE SET user_name=EXCLUDED.user_name, cash=EXCLUDED.cash, " +
			"borrowed=EXCLUDED.borrowed, total_fees=EXCLUDED.total_fees, " +
			"daily_baseline_date=EXCLUDED.daily_baseline_date, daily_baseline_equity=EXCLUDED.daily_baseline_equity, " +
			"weekly_baseline_date=EXCLUDED.weekly_baseline_date, weekly_baseline_equity=EXCLUDED.weekly_baseline_equity, " +
			"monthly_baseline_date=EXCLUDED.monthly_baseline_date, monthly_baseline_equity=EXCLUDED.monthly_baseline_equity, " +
			"currency_holdings=EXCLUDED.currency_holdings, schema_version=EXCLUDED.schema_version, " +
			"last_replenish_date=EXCLUDED.last_replenish_date";

	public SandboxUserRepository(JdbcTemplate jdbc) {
		super(jdbc);
	}

	public void save(String key, SandboxUser user) {
		try {
			jdbc.update(UPSERT,
					key,
					user.getUserName(),
					user.getCash(),
					user.getBorrowed(),
					user.getTotalFees(),
					user.getDailyBaselineDate() != null ? user.getDailyBaselineDate().toString() : null,
					user.getDailyBaselineEquity(),
					user.getWeeklyBaselineDate() != null ? user.getWeeklyBaselineDate().toString() : null,
					user.getWeeklyBaselineEquity(),
					user.getMonthlyBaselineDate() != null ? user.getMonthlyBaselineDate().toString() : null,
					user.getMonthlyBaselineEquity(),
					serializeHoldings(user.getCurrencyHoldings()),
					user.getSchemaVersion(),
					user.getLastReplenishDate() != null ? user.getLastReplenishDate().toString() : null
			);
		} catch (Exception e) {
			log.error("SandboxUserRepository.save({}) failed: {}", key, e.getMessage(), e);
		}
	}

	public SandboxUser findById(String key) {
		try {
			List<SandboxUser> results = jdbc.query(
					"SELECT * FROM sandbox_users WHERE user_id = ?", this::mapRow, key);
			return results.isEmpty() ? null : results.getFirst();
		} catch (Exception e) {
			log.error("SandboxUserRepository.findById({}) failed: {}", key, e.getMessage(), e);
			return null;
		}
	}

	public List<SandboxUser> findAll() {
		try {
			return jdbc.query("SELECT * FROM sandbox_users", this::mapRow);
		} catch (Exception e) {
			log.error("SandboxUserRepository.findAll() failed: {}", e.getMessage(), e);
			return List.of();
		}
	}

	public void delete(String key) {
		try {
			jdbc.update("DELETE FROM sandbox_users WHERE user_id = ?", key);
		} catch (Exception e) {
			log.error("SandboxUserRepository.delete({}) failed: {}", key, e.getMessage(), e);
		}
	}

	private SandboxUser mapRow(ResultSet rs, int rowNum) throws SQLException {
		SandboxUser user = new SandboxUser();
		user.setUserId(rs.getString("user_id"));
		user.setUserName(rs.getString("user_name"));
		user.setCash(rs.getDouble("cash"));
		user.setBorrowed(rs.getDouble("borrowed"));
		user.setTotalFees(rs.getDouble("total_fees"));
		String daily = rs.getString("daily_baseline_date");
		if (daily != null) user.setDailyBaselineDate(LocalDate.parse(daily));
		user.setDailyBaselineEquity(rs.getDouble("daily_baseline_equity"));
		String weekly = rs.getString("weekly_baseline_date");
		if (weekly != null) user.setWeeklyBaselineDate(LocalDate.parse(weekly));
		user.setWeeklyBaselineEquity(rs.getDouble("weekly_baseline_equity"));
		String monthly = rs.getString("monthly_baseline_date");
		if (monthly != null) user.setMonthlyBaselineDate(LocalDate.parse(monthly));
		user.setMonthlyBaselineEquity(rs.getDouble("monthly_baseline_equity"));
		user.setCurrencyHoldings(parseHoldings(rs.getString("currency_holdings")));
		user.setSchemaVersion(rs.getInt("schema_version"));
		String lastReplenish = rs.getString("last_replenish_date");
		if (lastReplenish != null) user.setLastReplenishDate(LocalDate.parse(lastReplenish));
		return user;
	}

	private Map<String, Double> parseHoldings(String json) {
		if (json == null || json.isBlank()) return new HashMap<>();
		try {
			return MAPPER.readValue(json, new TypeReference<Map<String, Double>>() {});
		} catch (Exception e) {
			log.warn("Failed to parse currency_holdings JSON: {}", e.getMessage());
			return new HashMap<>();
		}
	}

	private String serializeHoldings(Map<String, Double> holdings) {
		if (holdings == null || holdings.isEmpty()) return "{}";
		try {
			return MAPPER.writeValueAsString(holdings);
		} catch (Exception e) {
			log.warn("Failed to serialize currency_holdings: {}", e.getMessage());
			return "{}";
		}
	}
}
