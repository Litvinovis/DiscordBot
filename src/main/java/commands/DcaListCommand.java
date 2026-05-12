package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import services.sandbox.model.DcaOrder;
import services.sandbox.repository.DcaOrderRepository;

import java.util.List;

/**
 * Handles "+дка-список" — list active DCA orders for the current user.
 */
@Component
public class DcaListCommand implements BotCommand {

    private final DcaOrderRepository dcaOrderRepository;

    public DcaListCommand(DcaOrderRepository dcaOrderRepository) {
        this.dcaOrderRepository = dcaOrderRepository;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return input.equals("+дка-список");
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        List<DcaOrder> orders = dcaOrderRepository.findByUser(event.getAuthor().getId());
        if (orders.isEmpty()) {
            return "📋 Нет активных DCA-ордеров.";
        }
        StringBuilder sb = new StringBuilder("📋 Активные DCA-ордера:\n");
        for (DcaOrder o : orders) {
            String freqLabel = "WEEKLY".equals(o.getFrequency()) ? "нед" : "мес";
            sb.append("• ").append(o.getTicker())
              .append(" — ").append((long) o.getAmountRub()).append(" ₽/").append(freqLabel).append("\n");
        }
        return sb.toString().trim();
    }
}
