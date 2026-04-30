package services.sandbox.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a user-defined price alert. When the ticker reaches targetPrice,
 * the user receives a DM notification.
 */
public class PriceAlert implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int CURRENT_SCHEMA_VERSION = 1;

	private int schemaVersion = 0;

	private String id;
	private String userId;
	private String ticker;
	private double targetPrice;
	/** true = notify when price >= target, false = notify when price <= target */
	private boolean above;
	private Instant createdAt;

	public PriceAlert() {
	}

	public PriceAlert(String id, String userId, String ticker,
					  double targetPrice, boolean above, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.ticker = ticker;
		this.targetPrice = targetPrice;
		this.above = above;
		this.createdAt = createdAt;
	}

	public String getId() { return id; }
	public String getUserId() { return userId; }
	public String getTicker() { return ticker; }
	public double getTargetPrice() { return targetPrice; }
	public boolean isAbove() { return above; }
	public Instant getCreatedAt() { return createdAt; }

	public int getSchemaVersion() { return schemaVersion; }
	public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
}
