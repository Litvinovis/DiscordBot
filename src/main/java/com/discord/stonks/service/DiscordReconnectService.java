package com.discord.stonks.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Сервис автоматического переподключения Discord бота.
 * Мониторит состояние подключения и переподключается при проблемах.
 */
@Slf4j
@Service
public class DiscordReconnectService {
    
    @Value("${discord.token}")
    private String discordToken;
    
    @Value("${discord.auto-reconnect:true}")
    private boolean autoReconnect;
    
    @Value("${discord.reconnect-delay:10}")
    private int reconnectDelaySeconds;
    
    @Value("${discord.max-reconnect-attempts:10}")
    private int maxReconnectAttempts;
    
    @Value("${discord.health-check-interval:60}")
    private int healthCheckIntervalSeconds;
    
    private final AtomicReference<JDA> jdaRef = new AtomicReference<>();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean shutdownRequested = false;
    
    /**
     * Инициализация бота и запуск мониторинга.
     */
    @PostConstruct
    public void init() {
        if (discordToken == null || discordToken.isEmpty()) {
            log.error("Discord токен не настроен. Бот не будет запущен.");
            return;
        }
        
        connect();
        
        if (autoReconnect) {
            startHealthMonitor();
            log.info("Автоматическое переподключение включено. Интервал проверки: {} секунд", 
                    healthCheckIntervalSeconds);
        }
    }
    
    /**
     * Подключение к Discord.
     */
    private void connect() {
        try {
            log.info("Подключение к Discord...");
            
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER
                    )
                    .setAutoReconnect(true)  // Встроенный auto-reconnect JDA
                    .build();
            
            jda.awaitReady();
            jdaRef.set(jda);
            reconnectAttempts.set(0);
            
            log.info("Discord бот успешно подключен. ID: {}", jda.getSelfUser().getId());
            log.info("Бот находится на {} серверах", jda.getGuilds().size());
            
        } catch (Exception e) {
            log.error("Ошибка подключения к Discord", e);
            scheduleReconnect();
        }
    }
    
    /**
     * Запуск мониторинга здоровья подключения.
     */
    private void startHealthMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkConnectionHealth();
            } catch (Exception e) {
                log.error("Ошибка в health check", e);
            }
        }, healthCheckIntervalSeconds, healthCheckIntervalSeconds, TimeUnit.SECONDS);
        
        log.debug("Health monitor запущен");
    }
    
    /**
     * Проверка состояния подключения.
     */
    private void checkConnectionHealth() {
        JDA jda = jdaRef.get();
        
        if (jda == null) {
            log.warn("JDA не инициализирован. Попытка переподключения...");
            reconnect();
            return;
        }
        
        try {
            // Проверяем статус подключения
            JDA.Status status = jda.getStatus();
            
            switch (status) {
                case CONNECTED:
                    // Всё хорошо
                    if (reconnectAttempts.get() > 0) {
                        log.info("Подключение восстановлено после {} попыток", reconnectAttempts.get());
                        reconnectAttempts.set(0);
                    }
                    break;
                    
                case DISCONNECTED:
                case FAILED_TO_LOGIN:
                case SHUTDOWN:
                    log.warn("Discord бот отключен. Статус: {}", status);
                    reconnect();
                    break;
                    
                case ATTEMPTING_TO_RECONNECT:
                case RECONNECT_QUEUED:
                    log.debug("Идёт переподключение... Статус: {}", status);
                    break;
                    
                case INITIALIZING:
                case LOADING_SUBSYSTEMS:
                    log.debug("Инициализация... Статус: {}", status);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Ошибка при проверке состояния подключения", e);
        }
    }
    
    /**
     * Переподключение с экспоненциальной задержкой.
     */
    private synchronized void reconnect() {
        if (shutdownRequested) {
            return;
        }
        
        int attempt = reconnectAttempts.incrementAndGet();
        
        if (attempt > maxReconnectAttempts) {
            log.error("Достигнут лимит попыток переподключения ({}). Остановка.", maxReconnectAttempts);
            return;
        }
        
        // Экспоненциальная задержка: 10s, 20s, 40s, 80s...
        long delay = reconnectDelaySeconds * (1L << (attempt - 1));
        delay = Math.min(delay, 300); // Макс 5 минут
        
        log.info("Попытка переподключения #{}. Задержка: {} секунд", attempt, delay);
        
        scheduler.schedule(() -> {
            try {
                JDA oldJda = jdaRef.get();
                if (oldJda != null) {
                    oldJda.shutdown();
                }
                
                connect();
                
            } catch (Exception e) {
                log.error("Ошибка при переподключении", e);
            }
        }, delay, TimeUnit.SECONDS);
    }
    
    /**
     * Планирование переподключения (для использования извне).
     */
    public void scheduleReconnect() {
        if (autoReconnect) {
            scheduler.execute(this::reconnect);
        }
    }
    
    /**
     * Принудительное переподключение.
     */
    public void forceReconnect() {
        log.info("Принудительное переподключение...");
        reconnectAttempts.set(0);
        reconnect();
    }
    
    /**
     * Получение текущего экземпляра JDA.
     */
    public JDA getJda() {
        return jdaRef.get();
    }
    
    /**
     * Проверка, подключен ли бот.
     */
    public boolean isConnected() {
        JDA jda = jdaRef.get();
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
    
    /**
     * Остановка сервиса.
     */
    @PreDestroy
    public void shutdown() {
        shutdownRequested = true;
        
        log.info("Остановка DiscordReconnectService...");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        JDA jda = jdaRef.get();
        if (jda != null) {
            jda.shutdown();
        }
        
        log.info("DiscordReconnectService остановлен");
    }
}