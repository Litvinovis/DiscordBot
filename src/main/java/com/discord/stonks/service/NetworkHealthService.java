package com.discord.stonks.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис проверки сетевого здоровья.
 * Мониторит доступность DNS и внешних сервисов.
 */
@Slf4j
@Service
public class NetworkHealthService {
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean networkHealthy = new AtomicBoolean(true);
    private final AtomicBoolean dnsHealthy = new AtomicBoolean(true);
    private final AtomicBoolean discordApiHealthy = new AtomicBoolean(true);
    
    private volatile boolean shutdownRequested = false;
    
    /**
     * Запуск мониторинга сети.
     */
    public void startMonitoring() {
        // Проверка каждые 30 секунд
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkNetworkHealth();
            } catch (Exception e) {
                log.error("Ошибка при проверке сети", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
        
        log.info("Network monitoring started");
    }
    
    /**
     * Проверка сетевого здоровья.
     */
    private void checkNetworkHealth() {
        boolean previousNetworkState = networkHealthy.get();
        boolean previousDnsState = dnsHealthy.get();
        boolean previousDiscordState = discordApiHealthy.get();
        
        // 1. Проверка DNS
        boolean dnsOk = checkDns();
        dnsHealthy.set(dnsOk);
        
        // 2. Проверка Discord API (только если DNS работает)
        boolean discordOk = dnsOk && checkDiscordApi();
        discordApiHealthy.set(discordOk);
        
        // 3. Общее состояние сети
        boolean networkOk = dnsOk && discordOk;
        networkHealthy.set(networkOk);
        
        // Логирование изменений состояния
        if (previousDnsState != dnsOk) {
            log.info("DNS состояние изменилось: {} -> {}", previousDnsState ? "OK" : "FAIL", dnsOk ? "OK" : "FAIL");
        }
        
        if (previousDiscordState != discordOk) {
            log.info("Discord API состояние изменилось: {} -> {}", 
                    previousDiscordState ? "OK" : "FAIL", discordOk ? "OK" : "FAIL");
        }
        
        if (previousNetworkState != networkOk) {
            log.info("Сетевое состояние изменилось: {} -> {}", 
                    previousNetworkState ? "OK" : "FAIL", networkOk ? "OK" : "FAIL");
            
            // Можно отправить уведомление или выполнить действие
            onNetworkStateChange(networkOk);
        }
    }
    
    /**
     * Проверка DNS разрешения.
     */
    private boolean checkDns() {
        try {
            long startTime = System.currentTimeMillis();
            InetAddress[] addresses = InetAddress.getAllByName("discord.com");
            long duration = System.currentTimeMillis() - startTime;
            
            if (addresses.length > 0) {
                log.debug("DNS resolution OK: discord.com -> {} ({} ms)", 
                        addresses[0].getHostAddress(), duration);
                return true;
            }
            
        } catch (UnknownHostException e) {
            log.warn("DNS resolution failed for discord.com: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during DNS check", e);
        }
        
        return false;
    }
    
    /**
     * Проверка доступности Discord API.
     */
    private boolean checkDiscordApi() {
        try {
            // Простая HTTP проверка
            java.net.URL url = new java.net.URL("https://discord.com");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            boolean ok = responseCode == 200 || responseCode == 301 || responseCode == 302;
            
            if (ok) {
                log.debug("Discord API доступен (HTTP {})", responseCode);
            } else {
                log.warn("Discord API недоступен (HTTP {})", responseCode);
            }
            
            return ok;
            
        } catch (IOException e) {
            log.warn("Discord API недоступен: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during Discord API check", e);
            return false;
        }
    }
    
    /**
     * Обработчик изменения состояния сети.
     */
    private void onNetworkStateChange(boolean networkOk) {
        if (networkOk) {
            log.info("Сеть восстановлена. Можно переподключать ботов...");
            // Здесь можно уведомить DiscordReconnectService о восстановлении сети
        } else {
            log.warn("Проблемы с сетью. Откладываем переподключения...");
            // Здесь можно приостановить попытки переподключения
        }
    }
    
    /**
     * Проверка общего состояния сети.
     */
    public boolean isNetworkHealthy() {
        return networkHealthy.get();
    }
    
    /**
     * Проверка состояния DNS.
     */
    public boolean isDnsHealthy() {
        return dnsHealthy.get();
    }
    
    /**
     * Проверка состояния Discord API.
     */
    public boolean isDiscordApiHealthy() {
        return discordApiHealthy.get();
    }
    
    /**
     * Получение детального статуса.
     */
    public String getDetailedStatus() {
        return String.format("Network: %s, DNS: %s, Discord API: %s",
                networkHealthy.get() ? "OK" : "FAIL",
                dnsHealthy.get() ? "OK" : "FAIL",
                discordApiHealthy.get() ? "OK" : "FAIL");
    }
    
    /**
     * Остановка мониторинга.
     */
    public void shutdown() {
        shutdownRequested = true;
        
        log.info("Stopping network monitoring...");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Network monitoring stopped");
    }
}