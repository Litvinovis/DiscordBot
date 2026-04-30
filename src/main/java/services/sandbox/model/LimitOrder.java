package services.sandbox.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a pending limit order (buy or sell) placed by a user.
 */
public class LimitOrder implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int CURRENT_SCHEMA_VERSION = 1;

	private int schemaVersion = 0;

	private String id;
	private String userId;
	private String userName;
	private String ticker;
	private TradeSide side;
	private int qty;
	/** Target price at which the order should be executed */
	private double limitPrice;
	private Instant createdAt;

	public LimitOrder() {
	}

	public LimitOrder(String id, String userId, String userName,
					  String ticker, TradeSide side, int qty,
					  double limitPrice, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.userName = userName;
		this.ticker = ticker;
		this.side = side;
		this.qty = qty;
		this.limitPrice = limitPrice;
		this.createdAt = createdAt;
	}

	public String getId() { return id; }
	public String getUserId() { return userId; }
	public String getUserName() { return userName; }
	public String getTicker() { return ticker; }
	public TradeSide getSide() { return side; }
	public int getQty() { return qty; }
	public double getLimitPrice() { return limitPrice; }
	public Instant getCreatedAt() { return createdAt; }

	public int getSchemaVersion() { return schemaVersion; }
	public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
}
