package services.sandbox.api;

/**
 * Interface for sandbox rating operations: leaderboard, personal ranking, statistics.
 */
public interface ISandboxRatingService {

    /**
     * Возвращает таблицу лидеров топ-5 участников за указанный период.
     *
     * @param period временной период: «день», «неделя», «месяц» или «all»
     * @return отформатированная таблица лидеров
     */
    String top(String period);

    /**
     * Возвращает персональный рейтинг пользователя среди всех участников.
     *
     * @param userId идентификатор пользователя Discord
     * @return строка с позицией, equity и ROI пользователя
     */
    String myRank(String userId);

    /**
     * Возвращает торговую статистику пользователя: win rate, P&amp;L, лучшая/худшая сделка.
     *
     * @param userId идентификатор пользователя Discord
     * @return отформатированная торговая статистика
     */
    String stats(String userId);
}
