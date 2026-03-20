package services.sandbox.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for sandbox order operations: market orders, limit orders, stop loss/take profit.
 */
public interface ISandboxOrderService {

    String buy(String userId, String userName, String ticker, int qty);

    String sell(String userId, String userName, String ticker, int qty);

    String placeLimitBuy(String userId, String userName, String ticker, int qty, BigDecimal limitPrice);

    String placeLimitSell(String userId, String userName, String ticker, int qty, BigDecimal limitPrice);

    String myOrders(String userId);

    String cancelOrder(String userId, String orderId);

    String setStopLoss(String userId, String ticker, BigDecimal triggerPrice);

    String setTakeProfit(String userId, String ticker, BigDecimal triggerPrice);

    String setAlert(String userId, String ticker, BigDecimal targetPrice);

    /** Called by scheduler: checks and executes triggered stop/take-profit orders. Returns [userId, message] pairs. */
    List<String[]> checkStopOrders();

    /** Called by scheduler: checks and executes triggered limit orders. Returns [userId, message] pairs. */
    List<String[]> checkLimitOrders();

    /** Called by scheduler: checks and fires price alerts. Returns [userId, message] pairs. */
    List<String[]> checkPriceAlerts();
}
