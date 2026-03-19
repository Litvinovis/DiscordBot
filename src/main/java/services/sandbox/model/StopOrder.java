package services.sandbox.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a Stop Loss or Take Profit order attached to an open position.
 */
public class StopOrder implements Serializable {
    /** "SL" = stop-loss, "TP" = take-profit */
    private String type;
    private String id;
    private String userId;
    private String ticker;
    private double triggerPrice;
    private Instant createdAt;

    public StopOrder() {
    }

    public StopOrder(String id, String userId, String ticker,
                     String type, double triggerPrice, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.ticker = ticker;
        this.type = type;
        this.triggerPrice = triggerPrice;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTicker() { return ticker; }
    public String getType() { return type; }
    public double getTriggerPrice() { return triggerPrice; }
    public Instant getCreatedAt() { return createdAt; }
}
