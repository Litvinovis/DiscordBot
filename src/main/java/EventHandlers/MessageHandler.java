package EventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

@Slf4j
public class MessageHandler extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getChannel().getName().equals("основной")) {
            try {
                event.getChannel().sendMessage("Согласен на все 100!")
                        .delay(5, TimeUnit.SECONDS)
                        .flatMap(message -> message.editMessage("хотя нет, передумал =)"))
                        .delay(5, TimeUnit.SECONDS)
                        .flatMap(Message::delete)
                        .delay(1, TimeUnit.SECONDS)
                        .flatMap(message -> event.getMessage().delete())
                        .submit();
            } catch (Exception e) {
                log.error(Constants.LOG_MESSAGE, e.getMessage());
            }
        }
    }
}
