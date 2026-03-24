/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.dv8tion.jda.api.JDA
 *  net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
 */
package services.sandbox;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import services.sandbox.SandboxTradingService;
import utils.ConfigLoader;

/**
 * Планировщик ежедневных отчётов по итогам торгов в песочнице.
 *
 * <p>Каждый день в 10:00 по екатеринбургскому времени отправляет в первый
 * разрешённый Discord-канал топ-5 участников за текущий день.
 */
public class SandboxReportScheduler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Создаёт и запускает планировщик ежедневных отчётов.
     *
     * @param service сервис торговли в песочнице
     * @param jda     экземпляр JDA для отправки сообщений в канал
     */
    public SandboxReportScheduler(SandboxTradingService service, JDA jda) {
        List<String> channels = ConfigLoader.getAllowedChannelIds();
        if (channels.isEmpty()) {
            return;
        }
        String channelId = channels.get(0);
        long initialDelay = this.initialDelayTo10am();
        this.scheduler.scheduleAtFixedRate(() -> {
            MessageChannel channel = (MessageChannel)jda.getChannelById(MessageChannel.class, channelId);
            if (channel != null) {
                channel.sendMessage((CharSequence)service.top("\u0434\u0435\u043d\u044c")).queue();
            }
        }, initialDelay, TimeUnit.DAYS.toSeconds(1L), TimeUnit.SECONDS);
    }

    private long initialDelayTo10am() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = now.withHour(10).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1L);
        }
        return Duration.between(now, next).getSeconds();
    }
}

