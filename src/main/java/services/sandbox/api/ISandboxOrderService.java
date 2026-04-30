package services.sandbox.api;

import java.math.BigDecimal;

/**
 * Interface for sandbox order operations: market orders, limit orders, stop loss/take profit.
 */
public interface ISandboxOrderService {

    /**
     * Выполняет рыночную покупку указанного количества акций.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @param ticker   тикер инструмента
     * @param qty      количество лотов
     * @return строка с результатом операции
     */
    String buy(String userId, String userName, String ticker, int qty);

    /**
     * Выполняет рыночную продажу указанного количества акций.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @param ticker   тикер инструмента
     * @param qty      количество лотов
     * @return строка с результатом операции
     */
    String sell(String userId, String userName, String ticker, int qty);

    /**
     * Размещает лимитную заявку на покупку.
     *
     * @param userId     идентификатор пользователя Discord
     * @param userName   имя пользователя Discord
     * @param ticker     тикер инструмента
     * @param qty        количество лотов
     * @param limitPrice целевая цена исполнения
     * @return строка с подтверждением или ошибкой
     */
    String placeLimitBuy(String userId, String userName, String ticker, int qty, BigDecimal limitPrice);

    /**
     * Размещает лимитную заявку на продажу.
     *
     * @param userId     идентификатор пользователя Discord
     * @param userName   имя пользователя Discord
     * @param ticker     тикер инструмента
     * @param qty        количество лотов
     * @param limitPrice целевая цена исполнения
     * @return строка с подтверждением или ошибкой
     */
    String placeLimitSell(String userId, String userName, String ticker, int qty, BigDecimal limitPrice);

    /**
     * Возвращает список активных лимитных заявок пользователя.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированный список заявок
     */
    String myOrders(String userId);

    /**
     * Отменяет лимитную заявку по идентификатору (полному или сокращённому).
     *
     * @param userId  идентификатор пользователя Discord
     * @param orderId идентификатор заявки (или его префикс)
     * @return строка с результатом отмены
     */
    String cancelOrder(String userId, String orderId);

    /**
     * Устанавливает стоп-лосс ордер для открытой позиции по указанному тикеру.
     *
     * @param userId       идентификатор пользователя Discord
     * @param ticker       тикер инструмента
     * @param triggerPrice цена срабатывания стоп-лосса
     * @return строка с подтверждением или ошибкой
     */
    String setStopLoss(String userId, String ticker, BigDecimal triggerPrice);

    /**
     * Устанавливает тейк-профит ордер для открытой позиции по указанному тикеру.
     *
     * @param userId       идентификатор пользователя Discord
     * @param ticker       тикер инструмента
     * @param triggerPrice цена срабатывания тейк-профита
     * @return строка с подтверждением или ошибкой
     */
    String setTakeProfit(String userId, String ticker, BigDecimal triggerPrice);

    /**
     * Устанавливает ценовой алерт: бот отправит DM, когда цена достигнет цели.
     *
     * @param userId      идентификатор пользователя Discord
     * @param ticker      тикер инструмента
     * @param targetPrice целевая цена срабатывания алерта
     * @return строка с подтверждением
     */
    String setAlert(String userId, String ticker, BigDecimal targetPrice);
}
