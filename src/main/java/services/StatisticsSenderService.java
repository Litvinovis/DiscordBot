package services;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.tinkoff.piapi.core.InvestApi;
import services.statTask.CurrencyStatTask;
import java.time.LocalTime;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StatisticsSenderService {
    private final CurrencyStatTask currencyStatTask;

    public StatisticsSenderService(InvestApi api, MessageReceivedEvent event) {
        this.currencyStatTask = new CurrencyStatTask(api, event);
        runCurrencyTask();
    }


    private void runCurrencyTask() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Время, когда должна выполняться задача
        LocalTime targetTime = LocalTime.of(21, 15);

        // Текущее время
        LocalTime now = LocalTime.now();

        // Вычисляем задержку до следующего запуска
        long initialDelay = ChronoUnit.SECONDS.between(now, targetTime);

        // Если целевое время уже прошло, планируем на следующий день
        if (initialDelay < 0) {
            initialDelay += TimeUnit.DAYS.toSeconds(1); // Добавляем 24 часа в секундах
        }

        // Запускаем задачу каждые 24 часа после первого запуска
        scheduler.scheduleAtFixedRate(currencyStatTask, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }
}
