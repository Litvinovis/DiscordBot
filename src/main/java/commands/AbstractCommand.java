package commands;

import java.math.BigDecimal;

/**
 * Базовый класс для команд бота с вспомогательными методами парсинга аргументов.
 */
public abstract class AbstractCommand implements BotCommand {

    /**
     * Парсит строку как целое число. Возвращает -1 при ошибке.
     */
    protected static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Парсит строку как BigDecimal, допуская запятую вместо точки.
     * Возвращает {@code null} при ошибке.
     */
    protected static BigDecimal parseBigDecimal(String s) {
        try {
            return new BigDecimal(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
}
