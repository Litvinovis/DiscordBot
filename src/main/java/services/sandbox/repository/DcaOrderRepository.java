package services.sandbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.sandbox.model.DcaOrder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class DcaOrderRepository extends BaseRepository {

    private static final String UPSERT =
            "INSERT INTO dca_orders (user_id, ticker, amount_rub, frequency, next_execution, created_at, active) " +
            "VALUES (?,?,?,?,?,?,?) " +
            "ON CONFLICT (user_id, ticker) DO UPDATE SET " +
            "amount_rub=EXCLUDED.amount_rub, frequency=EXCLUDED.frequency, " +
            "next_execution=EXCLUDED.next_execution, active=EXCLUDED.active";

    public DcaOrderRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void upsert(DcaOrder order) {
        try {
            jdbc.update(UPSERT,
                    order.getUserId(),
                    order.getTicker(),
                    order.getAmountRub(),
                    order.getFrequency(),
                    order.getNextExecution().toEpochMilli(),
                    order.getCreatedAt().toEpochMilli(),
                    order.isActive()
            );
        } catch (Exception e) {
            log.error("DcaOrderRepository.upsert({}/{}) failed: {}", order.getUserId(), order.getTicker(), e.getMessage(), e);
        }
    }

    public List<DcaOrder> findByUser(String userId) {
        try {
            return jdbc.query(
                    "SELECT * FROM dca_orders WHERE user_id = ? AND active = TRUE",
                    this::mapRow, userId);
        } catch (Exception e) {
            log.error("DcaOrderRepository.findByUser({}) failed: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    public void cancel(String userId, String ticker) {
        try {
            jdbc.update(
                    "UPDATE dca_orders SET active = FALSE WHERE user_id = ? AND ticker = ? AND active = TRUE",
                    userId, ticker);
        } catch (Exception e) {
            log.error("DcaOrderRepository.cancel({}/{}) failed: {}", userId, ticker, e.getMessage(), e);
        }
    }

    public List<DcaOrder> findDueOrders() {
        try {
            return jdbc.query(
                    "SELECT * FROM dca_orders WHERE active = TRUE AND next_execution <= ?",
                    this::mapRow, Instant.now().toEpochMilli());
        } catch (Exception e) {
            log.error("DcaOrderRepository.findDueOrders() failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void updateNextExecution(long id, Instant nextTime) {
        try {
            jdbc.update(
                    "UPDATE dca_orders SET next_execution = ? WHERE id = ?",
                    nextTime.toEpochMilli(), id);
        } catch (Exception e) {
            log.error("DcaOrderRepository.updateNextExecution({}) failed: {}", id, e.getMessage(), e);
        }
    }

    private DcaOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        DcaOrder order = new DcaOrder(
                rs.getString("user_id"),
                rs.getString("ticker"),
                rs.getDouble("amount_rub"),
                rs.getString("frequency"),
                Instant.ofEpochMilli(rs.getLong("next_execution")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getBoolean("active")
        );
        order.setId(rs.getLong("id"));
        return order;
    }
}
