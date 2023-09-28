import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

@Slf4j
public class App {
    public static void main(String[] args) {
        JDA jda = JDABuilder.createDefault("MTE1NzA1MjkxOTkwOTIwMzk5OQ.GtaRz_.viS-DSQyWJDcXI1b0nAHl1p4BQiMM8368CQjKE").build();

        try {
//            jda.awaitReady().getCategories().get(1).getTextChannels().get(0).sendMessage("Привет пидоры, готовы нагибать рынок?").timeout(5, TimeUnit.SECONDS).submit();
        } catch (Exception e) {
            log.trace("Упс {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
