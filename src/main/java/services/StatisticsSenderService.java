package services;

import net.dv8tion.jda.api.JDA;
import ru.tinkoff.piapi.core.InvestApi;
import services.statTask.CurrencyStatTask;
import services.statTask.SharesStatTask;
import utils.ConfigLoader;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsSenderService {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsSenderService.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public StatisticsSenderService(InvestApi api, JDA jda) {
        runCurrencyTask(api, jda);
        runSharesTask(api, jda);
    }

    private void runCurrencyTask(InvestApi api, JDA jda) {
        try {
            CurrencyStatTask currencyStatTask = new CurrencyStatTask(api, jda);
            String cronExpression = ConfigLoader.getCurrencyReportCron();
            
            // Parse cron expression (s m h d m w)
            String[] parts = cronExpression.split(" ");
            if (parts.length != 6) {
                logger.error("Неверный формат cron выражения для валютных отчетов: {}", cronExpression);
                return;
            }
            
            int second = parseCronPart(parts[0], 0, 59);
            int minute = parseCronPart(parts[1], 0, 59);
            int hour = parseCronPart(parts[2], 0, 23);
            
            // For simplicity, we'll schedule it daily at the specified time
            // A full cron implementation would be more complex
            long initialDelay = calculateInitialDelay(hour, minute, second);
            long period = 24 * 60 * 60; // 24 hours in seconds
            
            scheduler.scheduleAtFixedRate(currencyStatTask, initialDelay, period, java.util.concurrent.TimeUnit.SECONDS);
            logger.info("Задача валютных отчетов запланирована на {}:{}:{}", hour, minute, second);
        } catch (Exception e) {
            logger.error("Ошибка при планировании задачи валютных отчетов", e);
        }
    }

    private void runSharesTask(InvestApi api, JDA jda) {
        try {
            SharesStatTask sharesStatTask = new SharesStatTask(api, jda);
            String cronExpression = ConfigLoader.getSharesReportCron();
            
            // Parse cron expression (s m h d m w)
            String[] parts = cronExpression.split(" ");
            if (parts.length != 6) {
                logger.error("Неверный формат cron выражения для отчетов по акциям: {}", cronExpression);
                return;
            }
            
            int second = parseCronPart(parts[0], 0, 59);
            int minute = parseCronPart(parts[1], 0, 59);
            int hour = parseCronPart(parts[2], 0, 23);
            
            // For simplicity, we'll schedule it daily at the specified time
            long initialDelay = calculateInitialDelay(hour, minute, second);
            long period = 24 * 60 * 60; // 24 hours in seconds
            
            scheduler.scheduleAtFixedRate(sharesStatTask, initialDelay, period, java.util.concurrent.TimeUnit.SECONDS);
            logger.info("Задача отчетов по акциям запланирована на {}:{}:{}", hour, minute, second);
        } catch (Exception e) {
            logger.error("Ошибка при планировании задачи отчетов по акциям", e);
        }
    }

    private int parseCronPart(String part, int min, int max) {
        try {
            int value = Integer.parseInt(part);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Handle asterisk or other cron syntax
            if ("*".equals(part)) {
                return min; // Use minimum value as default
            }
        }
        return min; // Default fallback
    }

    private long calculateInitialDelay(int targetHour, int targetMinute, int targetSecond) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime targetTime = now.withHour(targetHour)
                                               .withMinute(targetMinute)
                                               .withSecond(targetSecond)
                                               .withNano(0);
        
        // If target time has already passed today, schedule for tomorrow
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1);
        }
        
        return java.time.Duration.between(now, targetTime).getSeconds();
    }
}