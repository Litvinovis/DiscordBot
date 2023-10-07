import EventHandlers.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import ru.tinkoff.piapi.core.InvestApi;

@Slf4j
public class App {
    public static void main(String[] args) {
        log.trace("Стартуем приложение");
        InvestApi api = InvestApi.createReadonly("t.xKX9aDEMB8xngn3y3rHeG3bc7DSzWOd2BZVZy_cOd5WymFsjedRQ0SN5pKeKC8Y7NH0CeiQB1M7JQN13s_VgJA");
        JDA jda = JDABuilder.createDefault("MTE1NzA1MjkxOTkwOTIwMzk5OQ.GW2drl.k086vq1RgkU9zCqor7S7ePkcI-WoCSJL0czWW0")
                .addEventListeners(new MessageHandler(api))
                .setActivity(Activity.playing("NASDAQ"))
                .build();
    }
}
