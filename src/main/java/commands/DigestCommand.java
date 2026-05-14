package commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import services.sandbox.SandboxTradingService;

/**
 * Handles "+дайджест вкл" / "+дайджест выкл" — opt-in/out for morning digest DM.
 */
@Component
public class DigestCommand extends AbstractCommand {

    private final SandboxTradingService sandboxTradingService;

    public DigestCommand(SandboxTradingService sandboxTradingService) {
        this.sandboxTradingService = sandboxTradingService;
    }

    @Override
    public boolean matches(String input, String[] parts) {
        return parts.length == 2 && "+дайджест".equals(parts[0])
            && ("+вкл".equals("+" + parts[1]) || "+выкл".equals("+" + parts[1]));
    }

    @Override
    public String execute(MessageReceivedEvent event, String msg, String[] parts) {
        boolean enable = "вкл".equalsIgnoreCase(parts[1]);
        return sandboxTradingService.toggleMorningDigest(
            event.getAuthor().getId(),
            event.getAuthor().getName(),
            enable
        );
    }
}
