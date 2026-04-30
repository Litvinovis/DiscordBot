package services.sandbox;

public enum RiskCheckResult {
    /** Trade is within risk limits — proceed. */
    OK,
    /** Equity ≤ 0 — force liquidate all positions, reject trade. */
    EQUITY_ZERO,
    /** Position leverage exceeds the configured maximum — reject trade. */
    LEVERAGE_EXCEEDED,
    /** Margin level fell below maintenance margin — force liquidate, allow trade to record. */
    MARGIN_CALL
}
