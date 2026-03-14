/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  net.dv8tion.jda.api.JDA
 *  net.dv8tion.jda.api.JDABuilder
 *  net.dv8tion.jda.api.entities.Activity
 *  net.dv8tion.jda.api.requests.GatewayIntent
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
import EventHandlers.MessageHandler;
import lombok.Generated;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.StatisticsSenderService;
import services.sandbox.SandboxReportScheduler;
import services.sandbox.SandboxTradingService;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class App {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        Logger start = LoggerFactory.getLogger((String)"default-logger");
        start.trace("\u0421\u0442\u0430\u0440\u0442\u0443\u0435\u043c \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435");
        String discordToken = ConfigLoader.getDiscordToken();
        String tinkoffToken = ConfigLoader.getTinkoffToken();
        String apiMode = ConfigLoader.getTinkoffApiMode();
        start.info("\u0422\u043e\u043a\u0435\u043d Discord \u0437\u0430\u0434\u0430\u043d: {}", (Object)(discordToken != null && !discordToken.isBlank() ? 1 : 0));
        start.info("\u0422\u043e\u043a\u0435\u043d Tinkoff \u0437\u0430\u0434\u0430\u043d: {}", (Object)(tinkoffToken != null && !tinkoffToken.isBlank() ? 1 : 0));
        start.info("\u0420\u0435\u0436\u0438\u043c API: {}", (Object)apiMode);
        if (discordToken == null || discordToken.isEmpty()) {
            start.error("\u0422\u043e\u043a\u0435\u043d Discord \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0438\u043b\u0438 \u043f\u0443\u0441\u0442");
            return;
        }
        if (tinkoffToken == null || tinkoffToken.isEmpty()) {
            start.error("\u0422\u043e\u043a\u0435\u043d Tinkoff \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0438\u043b\u0438 \u043f\u0443\u0441\u0442");
            return;
        }
        Object target = System.getProperty("invest.api.target");
        if (target == null || ((String)target).isBlank()) {
            target = "dns:///invest-public-api.tinkoff.ru:443";
        } else if (!((String)target).contains(":///")) {
            target = "dns:///" + (String)target;
        }
        TInvestApi api = TInvestApi.create(tinkoffToken, false, (String)target);
        SandboxTradingService sandboxTradingService = new SandboxTradingService(api);
        try {
            JDA jda = JDABuilder.createDefault((String)discordToken).enableIntents(GatewayIntent.MESSAGE_CONTENT, new GatewayIntent[0]).addEventListeners(new Object[]{new MessageHandler(api, sandboxTradingService)}).setActivity(Activity.playing((String)"NASDAQ")).build();
            jda.awaitReady();
            new StatisticsSenderService(api, jda);
            new SandboxReportScheduler(sandboxTradingService, jda);
            start.info("\u0411\u043e\u0442 Discord \u0443\u0441\u043f\u0435\u0448\u043d\u043e \u0438\u043d\u0438\u0446\u0438\u0430\u043b\u0438\u0437\u0438\u0440\u043e\u0432\u0430\u043d");
        }
        catch (Exception e) {
            start.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0438\u043d\u0438\u0446\u0438\u0430\u043b\u0438\u0437\u0430\u0446\u0438\u0438 \u0431\u043e\u0442\u0430 Discord", (Throwable)e);
        }
    }
}

