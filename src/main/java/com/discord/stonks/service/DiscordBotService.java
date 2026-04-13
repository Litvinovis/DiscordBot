package com.discord.stonks.service;

import com.discord.stonks.listener.DiscordEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Основной сервис Discord бота с автоматическим переподключением.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordBotService {
    
    private final DiscordReconnectService reconnectService;
    private final NetworkHealthService networkHealthService;
    private final DiscordEventListener eventListener;
    
    @Value("${discord.bot-activity:Торгую акциями}")
    private String botActivity;
    
    /**
     * Инициализация бота.
     */
    @PostConstruct
    public void init() {
        log.info("Инициализация Discord бота...");
        
        // Запускаем мониторинг сети
        networkHealthService.startMonitoring();
        
        // DiscordReconnectService уже запускается сам через @PostConstruct
        // Ждём инициализации и регистрируем event listener
        
        // Устанавливаем активность бота
        setBotActivity();
        
        log.info("Discord бот инициализирован с автоматическим переподключением");
    }
    
    /**
     * Установка активности бота.
     */
    private void setBotActivity() {
        JDA jda = reconnectService.getJda();
        if (jda != null && reconnectService.isConnected()) {
            jda.getPresence().setActivity(Activity.watching(botActivity));
            log.info("Активность бота установлена: {}", botActivity);
        } else {
            // Запланируем установку активности после подключения
            log.debug("Бот ещё не подключен. Активность будет установлена позже");
        }
    }
    
    /**
     * Получение экземпляра JDA.
     */
    public JDA getJda() {
        return reconnectService.getJda();
    }
    
    /**
     * Проверка подключения бота.
     */
    public boolean isBotConnected() {
        return reconnectService.isConnected();
    }
    
    /**
     * Получение статуса бота.
     */
    public String getBotStatus() {
        JDA jda = getJda();
        if (jda == null) {
            return "NOT_INITIALIZED";
        }
        
        return String.format("%s (Network: %s)", 
                jda.getStatus(),
                networkHealthService.getDetailedStatus());
    }
    
    /**
     * Принудительное переподключение бота.
     */
    public void reconnectBot() {
        log.info("Принудительное переподключение бота...");
        reconnectService.forceReconnect();
    }
    
    /**
     * Получение информации о боте.
     */
    public String getBotInfo() {
        JDA jda = getJda();
        if (jda == null || !isBotConnected()) {
            return "Бот не подключен";
        }
        
        return String.format(
                "🤖 *Статус бота:*\n" +
                "• ID: %s\n" +
                "• Имя: %s\n" +
                "• Статус: %s\n" +
                "• Серверов: %d\n" +
                "• Сеть: %s\n" +
                "• Автореконнект: %s",
                jda.getSelfUser().getId(),
                jda.getSelfUser().getName(),
                jda.getStatus(),
                jda.getGuilds().size(),
                networkHealthService.getDetailedStatus(),
                "ВКЛ"
        );
    }
    
    /**
     * Остановка бота.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Остановка Discord бота...");
        
        // Останавливаем сервисы в правильном порядке
        networkHealthService.shutdown();
        // DiscordReconnectService остановится сам через @PreDestroy
        
        log.info("Discord бот остановлен");
    }
}