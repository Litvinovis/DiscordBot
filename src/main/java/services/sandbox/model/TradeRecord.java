package services.sandbox.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Запись об исполненной сделке в торговой песочнице.
 *
 * <p>Хранит направление (BUY/SELL), тикер, цену, количество лотов,
 * комиссию и время исполнения.
 * Используется как сущность Ignite-кэша {@code stonks_sandbox_trades}.
 */
public class TradeRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion = 0;

    private String id;
    private String userId;
    private String ticker;
    private String side;
    private int qty;
    private double price;
    private double fee;
    private Instant timestamp;

    public TradeRecord() {
    }

    public TradeRecord(String id, String userId, String ticker, String side, int qty,
                       double price, double fee, Instant timestamp) {
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

    public double getPrice() {
        return this.price;
    }

    public double getFee() {
        return this.fee;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public int getSchemaVersion() {
        return this.schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
