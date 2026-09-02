package services.sandbox.model;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;

/**
 * Запись об исполненной сделке в торговой песочнице.
 *
 * <p>Хранит направление (BUY/SELL), тикер, цену, количество лотов,
 * комиссию и время исполнения.
 * Хранится в PostgreSQL-таблице {@code sandbox_trades}.
 */
public class TradeRecord implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int CURRENT_SCHEMA_VERSION = 1;

	private int schemaVersion = 0;

	private String id;
	private String userId;
	private String ticker;
	private TradeSide side;
	private int qty;
	private BigDecimal price;
	private BigDecimal fee;
	private Instant timestamp;

	public TradeRecord() {
	}

	public TradeRecord(String id, String userId, String ticker, TradeSide side, int qty,
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

	public TradeSide getSide() {
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

	public int getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}
}
