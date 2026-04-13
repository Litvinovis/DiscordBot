package com.discord.stonks.listener;

import com.discord.stonks.service.DiscordReconnectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.springframework.stereotype.Component;

/**
 * Обработчик событий Discord для мониторинга состояния подключения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener implements EventListener {
    
    private final DiscordReconnectService reconnectService;
    
    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof ReadyEvent) {
            onReady((ReadyEvent) event);
        } else if (event instanceof SessionDisconnectEvent) {
            onDisconnect((SessionDisconnectEvent) event);
        } else if (event instanceof SessionResumeEvent) {
            onResume((SessionResumeEvent) event);
        } else if (event instanceof SessionRecreateEvent) {
            onRecreate((SessionRecreateEvent) event);
        } else if (event instanceof ShutdownEvent) {
            onShutdown((ShutdownEvent) event);
        } else if (event instanceof StatusChangeEvent) {
            onStatusChange((StatusChangeEvent) event);
        }
    }
    
    /**
     * Бот готов и подключен.
     */
    private void onReady(ReadyEvent event) {
        log.info("✅ Discord бот готов. Подключен к {} серверам", 
                event.getJDA().getGuilds().size());
        
        // Сбрасываем счётчик попыток переподключения
        // (reconnectService делает это сам, но для надёжности)
    }
    
    /**
     * Отключение от Discord.
     */
    private void onDisconnect(SessionDisconnectEvent event) {
        log.warn("🔌 Discord бот отключен. Код: {}, Сервер закрыт: {}", 
                event.getCloseCode(), event.isClosedByServer());
        
        // Если сервер закрыл соединение (не мы), сразу планируем переподключение
        if (event.isClosedByServer()) {
            log.info("Сервер Discord закрыл соединение. Планируем переподключение...");
            reconnectService.scheduleReconnect();
        }
    }
    
    /**
     * Восстановление сессии.
     */
    private void onResume(SessionResumeEvent event) {
        log.info("🔄 Сессия Discord восстановлена");
    }
    
    /**
     * Пересоздание сессии.
     */
    private void onRecreate(SessionRecreateEvent event) {
        log.info("🔄 Сессия Discord пересоздана");
    }
    
    /**
     * Выключение бота.
     */
    private void onShutdown(ShutdownEvent event) {
        log.info("🔴 Discord бот выключен. Код: {}", event.getCode());
    }
    
    /**
     * Изменение статуса подключения.
     */
    private void onStatusChange(StatusChangeEvent event) {
        log.debug("Статус Discord изменился: {} -> {}", 
                event.getOldStatus(), event.getNewStatus());
        
        // Обработка критических изменений статуса
        switch (event.getNewStatus()) {
            case DISCONNECTED:
            case FAILED_TO_LOGIN:
            case SHUTDOWN:
                log.warn("Критическое изменение статуса: {}", event.getNewStatus());
                
                // Не переподключаем сразу, ждём экспоненциальную задержку
                // reconnectService сам обработает это в health check
                break;
                
            case ATTEMPTING_TO_RECONNECT:
                log.info("Попытка переподключения к Discord...");
                break;
                
            case CONNECTED:
                log.info("Подключение к Discord восстановлено");
                break;
        }
    }
    
    /**
     * Регистрация слушателя в JDA.
     */
    public void register(net.dv8tion.jda.api.JDA jda) {
        if (jda != null) {
            jda.addEventListener(this);
            log.debug("DiscordEventListener зарегистрирован");
        }
    }
}