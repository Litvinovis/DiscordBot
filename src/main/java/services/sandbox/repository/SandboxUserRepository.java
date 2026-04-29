package services.sandbox.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import services.sandbox.model.SandboxUser;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SandboxUserRepository extends BaseRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SandboxUserRepository(DataSource dataSource) {
        super(dataSource);
    }

    public void save(String key, SandboxUser user) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO sandbox_users (user_id, user_name, cash, borrowed, total_fees, " +
                 "daily_baseline_date, daily_baseline_equity, weekly_baseline_date, weekly_baseline_equity, " +
                 "monthly_baseline_date, monthly_baseline_equity, currency_holdings, schema_version) " +
                 "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (user_id) DO UPDATE SET user_name=EXCLUDED.user_name, cash=EXCLUDED.cash, " +
                 "borrowed=EXCLUDED.borrowed, total_fees=EXCLUDED.total_fees, " +
                 "daily_baseline_date=EXCLUDED.daily_baseline_date, daily_baseline_equity=EXCLUDED.daily_baseline_equity, " +
                 "weekly_baseline_date=EXCLUDED.weekly_baseline_date, weekly_baseline_equity=EXCLUDED.weekly_baseline_equity, " +
                 "monthly_baseline_date=EXCLUDED.monthly_baseline_date, monthly_baseline_equity=EXCLUDED.monthly_baseline_equity, " +
                 "currency_holdings=EXCLUDED.currency_holdings, schema_version=EXCLUDED.schema_version")) {
            ps.setString(1, key);
            ps.setString(2, user.getUserName());
            ps.setDouble(3, user.getCash());
            ps.setDouble(4, user.getBorrowed());
            ps.setDouble(5, user.getTotalFees());
            ps.setString(6, user.getDailyBaselineDate() != null ? user.getDailyBaselineDate().toString() : null);
            ps.setDouble(7, user.getDailyBaselineEquity());
            ps.setString(8, user.getWeeklyBaselineDate() != null ? user.getWeeklyBaselineDate().toString() : null);
            ps.setDouble(9, user.getWeeklyBaselineEquity());
            ps.setString(10, user.getMonthlyBaselineDate() != null ? user.getMonthlyBaselineDate().toString() : null);
            ps.setDouble(11, user.getMonthlyBaselineEquity());
            ps.setString(12, serializeHoldings(user.getCurrencyHoldings()));
            ps.setInt(13, user.getSchemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("SandboxUserRepository.save({}) failed: {}", key, e.getMessage(), e);
        }
    }

    public SandboxUser findById(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_users WHERE user_id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("SandboxUserRepository.findById({}) failed: {}", key, e.getMessage(), e);
        }
        return null;
    }

    public List<SandboxUser> findAll() {
        List<SandboxUser> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sandbox_users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.error("SandboxUserRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    public void delete(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sandbox_users WHERE user_id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("SandboxUserRepository.delete({}) failed: {}", key, e.getMessage(), e);
        }
    }

    private SandboxUser mapRow(ResultSet rs) throws SQLException {
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
