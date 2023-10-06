package EventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.Random;

@Slf4j
public class MessageHandler extends ListenerAdapter {
  private final InvestApi api;

  public MessageHandler(InvestApi api) {
    this.api = api;
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    try {
      if (event.getChannel().getName().equals("основной")) {
        if (event.getAuthor().getName().equals("chegobnk") && random()) {
          event.getChannel().sendMessage("О, мсье чегобник как дела?")
                  .submit();
        } else if (event.getAuthor().getName().equals("kakimito") && random()) {
          event.getChannel().sendMessage("Kakimito, опять ты чушь несёшь?")
                  .submit();
        } else if (event.getAuthor().getName().equals("l4rover") && random()) {
          event.getChannel().sendMessage("О создатель, ты в чате!")
                  .submit();
        } else if (event.getAuthor().getName().equals("david orson") && random()) {
          event.getChannel().sendMessage("А можно форточку открыть?")
                  .submit();
        } else if (event.getAuthor().getName().equals("я бы взял") && random()) {
          event.getChannel().sendMessage("аргументный аргумент конечно")
                  .submit();
        }
      }
    } catch (Exception e) {
      log.error(Constants.LOG_MESSAGE, e.getMessage());
    }
  }

  private boolean random() {
    Random random = new Random();
    return random.nextBoolean();
  }
}
