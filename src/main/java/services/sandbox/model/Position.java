package services.sandbox.model;

import java.io.Serializable;

/**
 * Представляет открытую позицию пользователя по конкретному инструменту в песочнице.
 *
 * <p>Хранит тикер, количество лотов и среднюю цену покупки.
 * Хранится в PostgreSQL-таблице {@code sandbox_positions}.
 */
public class Position implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int CURRENT_SCHEMA_VERSION = 1;

	private int schemaVersion = 0;

	private String userId;
	private String ticker;
	private String instrumentId;
	private int quantity;
	private double avgPrice;

	public Position() {
	}

	public Position(String userId, String ticker, String instrumentId, int quantity, double avgPrice) {
		this.userId = userId;
		this.ticker = ticker;
		this.instrumentId = instrumentId;
		this.quantity = quantity;
		this.avgPrice = avgPrice;
	}

	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getTicker() {
		return this.ticker;
	}

	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	public String getInstrumentId() {
		return this.instrumentId;
	}

	public void setInstrumentId(String instrumentId) {
		this.instrumentId = instrumentId;
	}

	public int getQuantity() {
		return this.quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public double getAvgPrice() {
		return this.avgPrice;
	}

	public void setAvgPrice(double avgPrice) {
		this.avgPrice = avgPrice;
	}

	public int getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}
}
