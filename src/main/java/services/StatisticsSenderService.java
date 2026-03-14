/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.dv8tion.jda.api.JDA
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.statTask.CurrencyStatTask;
import services.statTask.SharesStatTask;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class StatisticsSenderService {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsSenderService.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public StatisticsSenderService(TInvestApi api, JDA jda) {
        this.runCurrencyTask(api, jda);
        this.runSharesTask(api, jda);
    }

    private void runCurrencyTask(TInvestApi api, JDA jda) {
        try {
            CurrencyStatTask currencyStatTask = new CurrencyStatTask(api, jda);
            String cronExpression = ConfigLoader.getCurrencyReportCron();
            String[] parts = cronExpression.split(" ");
            if (parts.length != 6) {
                logger.error("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0444\u043e\u0440\u043c\u0430\u0442 cron \u0432\u044b\u0440\u0430\u0436\u0435\u043d\u0438\u044f \u0434\u043b\u044f \u0432\u0430\u043b\u044e\u0442\u043d\u044b\u0445 \u043e\u0442\u0447\u0435\u0442\u043e\u0432: {}", (Object)cronExpression);
                return;
            }
            int second = this.parseCronPart(parts[0], 0, 59);
            int minute = this.parseCronPart(parts[1], 0, 59);
            int hour = this.parseCronPart(parts[2], 0, 23);
            long initialDelay = this.calculateInitialDelay(hour, minute, second);
            long period = 86400L;
            this.scheduler.scheduleAtFixedRate(currencyStatTask, initialDelay, period, TimeUnit.SECONDS);
            logger.info("\u0417\u0430\u0434\u0430\u0447\u0430 \u0432\u0430\u043b\u044e\u0442\u043d\u044b\u0445 \u043e\u0442\u0447\u0435\u0442\u043e\u0432 \u0437\u0430\u043f\u043b\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0430 \u043d\u0430 {}:{}:{}", new Object[]{hour, minute, second});
        }
        catch (Exception e) {
            logger.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u043f\u043b\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0438 \u0437\u0430\u0434\u0430\u0447\u0438 \u0432\u0430\u043b\u044e\u0442\u043d\u044b\u0445 \u043e\u0442\u0447\u0435\u0442\u043e\u0432", (Throwable)e);
        }
    }

    private void runSharesTask(TInvestApi api, JDA jda) {
        try {
            SharesStatTask sharesStatTask = new SharesStatTask(api, jda);
            String cronExpression = ConfigLoader.getSharesReportCron();
            String[] parts = cronExpression.split(" ");
            if (parts.length != 6) {
                logger.error("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0444\u043e\u0440\u043c\u0430\u0442 cron \u0432\u044b\u0440\u0430\u0436\u0435\u043d\u0438\u044f \u0434\u043b\u044f \u043e\u0442\u0447\u0435\u0442\u043e\u0432 \u043f\u043e \u0430\u043a\u0446\u0438\u044f\u043c: {}", (Object)cronExpression);
                return;
            }
            int second = this.parseCronPart(parts[0], 0, 59);
            int minute = this.parseCronPart(parts[1], 0, 59);
            int hour = this.parseCronPart(parts[2], 0, 23);
            long initialDelay = this.calculateInitialDelay(hour, minute, second);
            long period = 86400L;
            this.scheduler.scheduleAtFixedRate(sharesStatTask, initialDelay, period, TimeUnit.SECONDS);
            logger.info("\u0417\u0430\u0434\u0430\u0447\u0430 \u043e\u0442\u0447\u0435\u0442\u043e\u0432 \u043f\u043e \u0430\u043a\u0446\u0438\u044f\u043c \u0437\u0430\u043f\u043b\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0430 \u043d\u0430 {}:{}:{}", new Object[]{hour, minute, second});
        }
        catch (Exception e) {
            logger.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u043f\u043b\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0438 \u0437\u0430\u0434\u0430\u0447\u0438 \u043e\u0442\u0447\u0435\u0442\u043e\u0432 \u043f\u043e \u0430\u043a\u0446\u0438\u044f\u043c", (Throwable)e);
        }
    }

    private int parseCronPart(String part, int min, int max) {
        block3: {
            try {
                int value = Integer.parseInt(part);
                if (value >= min && value <= max) {
                    return value;
                }
            }
            catch (NumberFormatException e) {
                if (!"*".equals(part)) break block3;
                return min;
            }
        }
        return min;
    }

    private long calculateInitialDelay(int targetHour, int targetMinute, int targetSecond) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = now.withHour(targetHour).withMinute(targetMinute).withSecond(targetSecond).withNano(0);
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1L);
        }
        return Duration.between(now, targetTime).getSeconds();
    }
}

