package services.sandbox.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.Tuple;
import services.sandbox.model.SandboxUser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Репозиторий пользователей песочницы.
 *
 * <p>Использует {@link org.apache.ignite.table.KeyValueView} Apache Ignite 3 для операций по ключу
 * и SQL через {@link IgniteClient#sql()} для выборок всех записей.
 * Колонка {@code currency_holdings} хранится как JSON VARCHAR.
 */
public class SandboxUserRepository extends BaseIgniteRepository {

    private static final String TABLE_NAME = "sandbox_users";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Создаёт репозиторий. View инициализируется лениво при первом обращении.
     * При смене клиента (переподключение) view сбрасывается автоматически.
     *
     * @param clientSupplier поставщик актуального клиента Ignite 3
     */
    public SandboxUserRepository(Supplier<IgniteClient> clientSupplier) {
        super(clientSupplier, TABLE_NAME);
    }

    /**
     * Сохраняет пользователя по ключу userId.
     */
    public void save(String key, SandboxUser user) {
        Tuple k = Tuple.create().set("user_id", key);
        Tuple v = modelToRow(user);
        view().put(null, k, v);
    }

    /**
     * Возвращает пользователя по userId или {@code null}, если не найден.
     */
    public SandboxUser findById(String key) {
        Tuple k = Tuple.create().set("user_id", key);
        Tuple row = view().get(null, k);
        if (row == null) return null;
        return rowToModel(key, row);
    }

    /**
     * Возвращает список всех пользователей через SQL SELECT.
     */
    public List<SandboxUser> findAll() {
        List<SandboxUser> result = new ArrayList<>();
        IgniteClient cl = client();
        if (cl == null) return result;
        try (var rs = cl.sql().execute(null, "SELECT * FROM " + TABLE_NAME)) {
            while (rs.hasNext()) {
                var row = rs.next();
                String userId = row.stringValue("USER_ID");
                SandboxUser user = new SandboxUser();
                user.setUserId(userId);
                user.setUserName(row.stringValue("USER_NAME"));
                user.setCash(row.doubleValue("CASH"));
                user.setBorrowed(row.doubleValue("BORROWED"));
                user.setTotalFees(row.doubleValue("TOTAL_FEES"));
                String dailyDate = row.stringValue("DAILY_BASELINE_DATE");
                if (dailyDate != null) user.setDailyBaselineDate(LocalDate.parse(dailyDate));
                user.setDailyBaselineEquity(row.doubleValue("DAILY_BASELINE_EQUITY"));
                String weeklyDate = row.stringValue("WEEKLY_BASELINE_DATE");
                if (weeklyDate != null) user.setWeeklyBaselineDate(LocalDate.parse(weeklyDate));
                user.setWeeklyBaselineEquity(row.doubleValue("WEEKLY_BASELINE_EQUITY"));
                String monthlyDate = row.stringValue("MONTHLY_BASELINE_DATE");
                if (monthlyDate != null) user.setMonthlyBaselineDate(LocalDate.parse(monthlyDate));
                user.setMonthlyBaselineEquity(row.doubleValue("MONTHLY_BASELINE_EQUITY"));
                String holdingsJson = row.stringValue("CURRENCY_HOLDINGS");
                user.setCurrencyHoldings(parseHoldings(holdingsJson));
                user.setSchemaVersion(row.intValue("SCHEMA_VERSION"));
                result.add(user);
            }
        } catch (Exception e) {
            log.error("SandboxUserRepository.findAll() failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Удаляет пользователя по userId.
     */
    public void delete(String key) {
        Tuple k = Tuple.create().set("user_id", key);
        view().remove(null, k);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private SandboxUser rowToModel(String key, Tuple row) {
        SandboxUser user = new SandboxUser();
        user.setUserId(key);
        user.setUserName(row.stringValue("user_name"));
        user.setCash(row.doubleValue("cash"));
        user.setBorrowed(row.doubleValue("borrowed"));
        user.setTotalFees(row.doubleValue("total_fees"));
        String dailyDate = row.stringValue("daily_baseline_date");
        if (dailyDate != null) user.setDailyBaselineDate(LocalDate.parse(dailyDate));
        user.setDailyBaselineEquity(row.doubleValue("daily_baseline_equity"));
        String weeklyDate = row.stringValue("weekly_baseline_date");
        if (weeklyDate != null) user.setWeeklyBaselineDate(LocalDate.parse(weeklyDate));
        user.setWeeklyBaselineEquity(row.doubleValue("weekly_baseline_equity"));
        String monthlyDate = row.stringValue("monthly_baseline_date");
        if (monthlyDate != null) user.setMonthlyBaselineDate(LocalDate.parse(monthlyDate));
        user.setMonthlyBaselineEquity(row.doubleValue("monthly_baseline_equity"));
        String holdingsJson = row.stringValue("currency_holdings");
        user.setCurrencyHoldings(parseHoldings(holdingsJson));
        user.setSchemaVersion(row.intValue("schema_version"));
        return user;
    }

    private Tuple modelToRow(SandboxUser user) {
        String holdingsJson = serializeHoldings(user.getCurrencyHoldings());
        return Tuple.create()
                .set("user_name", user.getUserName())
                .set("cash", user.getCash())
                .set("borrowed", user.getBorrowed())
                .set("total_fees", user.getTotalFees())
                .set("daily_baseline_date",
                        user.getDailyBaselineDate() != null ? user.getDailyBaselineDate().toString() : null)
                .set("daily_baseline_equity", user.getDailyBaselineEquity())
                .set("weekly_baseline_date",
                        user.getWeeklyBaselineDate() != null ? user.getWeeklyBaselineDate().toString() : null)
                .set("weekly_baseline_equity", user.getWeeklyBaselineEquity())
                .set("monthly_baseline_date",
                        user.getMonthlyBaselineDate() != null ? user.getMonthlyBaselineDate().toString() : null)
                .set("monthly_baseline_equity", user.getMonthlyBaselineEquity())
                .set("currency_holdings", holdingsJson)
                .set("schema_version", user.getSchemaVersion());
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
