package services.sandbox.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public class TradeRecord implements Serializable {
    private String id;
    private String userId;
    private String ticker;
    private String side;
    private int qty;
    private BigDecimal price;
    private BigDecimal fee;
    private Instant timestamp;

    public TradeRecord() {
    }

    public TradeRecord(String id, String userId, String ticker, String side, int qty,
                       BigDecimal price, BigDecimal fee, Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.ticker = ticker;
        this.side = side;
        this.qty = qty;
        this.price = price;
        this.fee = fee;
        this.timestamp = timestamp;
    }

    public String getId() {
        return this.id;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getTicker() {
        return this.ticker;
    }

    public String getSide() {
        return this.side;
    }

    public int getQty() {
        return this.qty;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public BigDecimal getFee() {
        return this.fee;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }
}
