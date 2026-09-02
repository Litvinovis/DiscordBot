package services.sandbox.model;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a DCA (Dollar Cost Averaging) order — automatic periodic purchase
 * of a ticker for a fixed RUB amount.
 */
public class DcaOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String userId;
    private String ticker;
    private BigDecimal amountRub;
    /** "WEEKLY" or "MONTHLY" */
    private String frequency;
    private Instant nextExecution;
    private Instant createdAt;
    private boolean active;

    public DcaOrder() {
    }

    public DcaOrder(String userId, String ticker, BigDecimal amountRub,
                    String frequency, Instant nextExecution, Instant createdAt, boolean active) {
        this.userId = userId;
        this.ticker = ticker;
        this.amountRub = amountRub;
        this.frequency = frequency;
        this.nextExecution = nextExecution;
        this.createdAt = createdAt;
        this.active = active;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public BigDecimal getAmountRub() { return amountRub; }
    public void setAmountRub(BigDecimal amountRub) { this.amountRub = amountRub; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public Instant getNextExecution() { return nextExecution; }
    public void setNextExecution(Instant nextExecution) { this.nextExecution = nextExecution; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
