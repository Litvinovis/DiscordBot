package services.sandbox.api;

/**
 * Interface for sandbox portfolio operations: balance, portfolio view, margin info.
 */
public interface ISandboxPortfolioService {

    /**
     * Регистрирует нового участника песочницы и начисляет стартовый баланс.
     *
     * @param userId   идентификатор пользователя Discord
     * @param userName имя пользователя Discord
     * @return строка с результатом регистрации
     */
    String register(String userId, String userName);

    /**
     * Возвращает список всех доступных для торговли тикеров.
     *
     * @return отформатированный список тикеров
     */
    String assets();

    /**
     * Возвращает содержимое портфеля пользователя с P&amp;L по каждой позиции.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированный портфель
     */
    String portfolio(String userId);

    /**
     * Возвращает сводку баланса: рублёвый счёт, equity, ROI, плечо.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная строка баланса
     */
    String balance(String userId);

    /**
     * Возвращает маржинальные показатели: уровень маржи, порог margin call, плечо.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная строка маржинальных показателей
     */
    String margin(String userId);

    /**
     * Возвращает текущую рыночную цену указанного тикера.
     *
     * @param ticker тикер инструмента
     * @return строка с ценой или сообщение об ошибке
     */
    String price(String ticker);

    /**
     * Возвращает историю последних сделок пользователя (до 20 записей).
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная история сделок
     */
    String history(String userId);
}
