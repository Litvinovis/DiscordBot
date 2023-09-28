import EventHandlers.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

@Slf4j
public class App {
    public static void main(String[] args) {

        JDA jda = JDABuilder.createDefault("MTE1NzA1MjkxOTkwOTIwMzk5OQ.GW2drl.k086vq1RgkU9zCqor7S7ePkcI-WoCSJL0czWW0")
                .addEventListeners(new MessageHandler())
                .setActivity(Activity.playing("NASDAQ"))
                .build();
    }
}
