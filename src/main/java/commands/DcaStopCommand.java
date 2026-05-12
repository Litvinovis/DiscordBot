package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import services.sandbox.repository.DcaOrderRepository;

import java.util.Locale;

/**
 * Handles "+дка-стоп TICKER" — cancel a DCA order for the given ticker.
 */
@Component
public class DcaStopCommand implements BotCommand {

    private final DcaOrderRepository dcaOrderRepository;

    public DcaStopCommand(DcaOrderRepository dcaOrderRepository) {
        this.dcaOrderRepository = dcaOrderRepository;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 2 && input.startsWith("+дка-стоп ");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        String ticker = parts[1].toUpperCase(Locale.ROOT);
        dcaOrderRepository.cancel(event.getAuthor().getId(), ticker);
        return "✅ DCA для " + ticker + " отменён.";
    }
}
