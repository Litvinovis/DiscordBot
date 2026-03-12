import EventHandlers.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.tbank.TInvestApi;
import services.StatisticsSenderService;
import utils.ConfigLoader;

@Slf4j
public class App {
    public static void main(String[] args) {
        Logger start = LoggerFactory.getLogger("default-logger");
        start.trace("Стартуем приложение");
        
        // Загрузка конфигурации
        String discordToken = ConfigLoader.getDiscordToken();
        String tinkoffToken = ConfigLoader.getTinkoffToken();
        String apiMode = ConfigLoader.getTinkoffApiMode();
        
        // Логируем только факт наличия секретов, без деталей
        start.info("Токен Discord задан: {}", discordToken != null && !discordToken.isBlank());
        start.info("Токен Tinkoff задан: {}", tinkoffToken != null && !tinkoffToken.isBlank());
        start.info("Режим API: {}", apiMode);
        
        // Проверка токенов
        if (discordToken == null || discordToken.isEmpty()) {
            start.error("Токен Discord отсутствует или пуст");
            return;
        }
        
        if (tinkoffToken == null || tinkoffToken.isEmpty()) {
            start.error("Токен Tinkoff отсутствует или пуст");
            return;
        }
        
        // Для gRPC принудительно используем dns resolver, иначе на сервере выбирается unix resolver
        String target = System.getProperty("invest.api.target");
        if (target == null || target.isBlank()) {
            target = "dns:///invest-public-api.tinkoff.ru:443";
        } else if (!target.contains(":///")) {
            target = "dns:///" + target;
        }

        // Создание экземпляра Invest API
        // api-mode сохраняем только для обратной совместимости конфига; работаем с prod endpoint
        TInvestApi api = TInvestApi.create(tinkoffToken, false, target);
        
        // Создание экземпляра JDA
        try {
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new MessageHandler(api))
                    .setActivity(Activity.playing("NASDAQ"))
                    .build();
            
            // Initialize scheduled reports service after JDA is ready
            jda.awaitReady();
            new StatisticsSenderService(api, jda);
            
            start.info("Бот Discord успешно инициализирован");
        } catch (Exception e) {
            start.error("Ошибка инициализации бота Discord", e);
        }
    }
}