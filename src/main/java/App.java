import EventHandlers.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.core.InvestApi;

@Slf4j
public class App {
    public static void main(String[] args) {
        Logger start = LoggerFactory.getLogger("default-logger");
        start.trace("Стартуем приложение");
        InvestApi api = InvestApi.createReadonly("t.NmlowBcBADqNJf_PZsWWTKtQAcMW_XXaph0E8S2LV-XIRBlnMjp421kPvPwtbmRbFdj_7DdWOTQU67ff87cq0Q");
        JDA jda = JDABuilder.createDefault("MTE1NzA1MjkxOTkwOTIwMzk5OQ.GW2drl.k086vq1RgkU9zCqor7S7ePkcI-WoCSJL0czWW0")
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new MessageHandler(api))
                .setActivity(Activity.playing("NASDAQ"))
                .build();
    }
}
