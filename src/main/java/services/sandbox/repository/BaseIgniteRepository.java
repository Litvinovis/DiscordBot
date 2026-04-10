package services.sandbox.repository;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Базовый класс для репозиториев Apache Ignite 3.
 *
 * <p>Инкапсулирует ленивую инициализацию {@link KeyValueView} с двойной проверкой
 * блокировки (double-checked locking) — view автоматически пересоздаётся
 * при смене клиента после переподключения.
 */
public abstract class BaseIgniteRepository {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Supplier<IgniteClient> clientSupplier;
    private final String tableName;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> kvView;

    protected BaseIgniteRepository(Supplier<IgniteClient> clientSupplier, String tableName) {
        this.clientSupplier = clientSupplier;
        this.tableName = tableName;
    }

    /**
     * Возвращает актуальный {@link KeyValueView}, переинициализируя его при смене клиента.
     *
     * @throws IllegalStateException если Ignite 3 недоступен
     */
    protected KeyValueView<Tuple, Tuple> view() {
        IgniteClient current = clientSupplier.get();
        if (current == null) {
            throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
        }
        if (kvView == null || current != lastClient) {
            synchronized (this) {
                current = clientSupplier.get();
                if (current == null) {
                    throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
                }
                if (kvView == null || current != lastClient) {
                    kvView = current.tables().table(tableName).keyValueView();
                    lastClient = current;
                }
            }
        }
        return kvView;
    }

    /**
     * Возвращает текущий клиент для SQL-запросов.
     * Может вернуть {@code null}, если Ignite недоступен — вызывающий код должен это учитывать.
     */
    protected IgniteClient client() {
        return clientSupplier.get();
    }
}
