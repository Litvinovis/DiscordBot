import EventHandlers.MessageHandler;
import lombok.Generated;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.StatisticsSenderService;
import services.sandbox.SandboxOrderScheduler;
import services.sandbox.SandboxReportScheduler;
import services.sandbox.SandboxTradingService;
import services.sandbox.ignite.IgniteHealthService;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

/**
 * Точка входа в приложение Stonks Bot.
 *
 * <p>Инициализирует подключение к T-Invest API, запускает Discord-бота через JDA,
 * а также планировщики: отчётов по песочнице, выполнения заявок и сервис статистики.
 */
public class App {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(App.class);

    /**
     * Запускает приложение: проверяет токены, инициализирует API, JDA и все сервисы.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        Logger start = LoggerFactory.getLogger("default-logger");
        start.trace("Запускаем приложение");
        String discordToken = ConfigLoader.getDiscordToken();
        String tinkoffToken = ConfigLoader.getTinkoffToken();
        String apiMode = ConfigLoader.getTinkoffApiMode();
        start.info("Токен Discord задан: {}", (Object)(discordToken != null && !discordToken.isBlank() ? 1 : 0));
        start.info("Токен Tinkoff задан: {}", (Object)(tinkoffToken != null && !tinkoffToken.isBlank() ? 1 : 0));
        start.info("Режим API: {}", (Object)apiMode);
        if (discordToken == null || discordToken.isEmpty()) {
            start.error("Токен Discord отсутствует или пуст");
            return;
        }
        if (tinkoffToken == null || tinkoffToken.isEmpty()) {
            start.error("Токен Tinkoff отсутствует или пуст");
            return;
        }
        String target = System.getProperty("invest.api.target");
        if (target == null || target.isBlank()) {
            target = "dns:///invest-public-api.tinkoff.ru:443";
        } else if (!target.contains(":///")) {
            target = "dns:///" + target;
        }
        TInvestApi api = TInvestApi.create(tinkoffToken, "sandbox".equalsIgnoreCase(apiMode), target);
        SandboxTradingService sandboxTradingService = new SandboxTradingService(api);

        // Start Ignite health-check service
        IgniteHealthService igniteHealthService = new IgniteHealthService(sandboxTradingService.getIgniteManager());
        igniteHealthService.start();

        try {
            JDA jda = JDABuilder.createDefault(discordToken).enableIntents(GatewayIntent.MESSAGE_CONTENT, new GatewayIntent[0]).addEventListeners(new MessageHandler(api, sandboxTradingService)).setActivity(Activity.playing("NASDAQ")).build();
            jda.awaitReady();
            new StatisticsSenderService(api, jda);
            new SandboxReportScheduler(sandboxTradingService, jda);
            new SandboxOrderScheduler(sandboxTradingService, jda);
            start.info("Бот Discord успешно инициализирован");
        } catch (Exception e) {
            start.error("Ошибка инициализации бота Discord", (Throwable)e);
        }
    }
}
