package services.sandbox;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import services.sandbox.model.Position;
import services.sandbox.model.SandboxUser;
import services.sandbox.repository.PositionRepository;
import services.sandbox.repository.SandboxUserRepository;

import java.util.List;

/**
 * Sends a morning digest DM to each active user at 9:00 Asia/Yekaterinburg (UTC+5, 7:00 МСК).
 * Only users with at least one open position receive a message.
 */
@Component
public class MorningDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(MorningDigestScheduler.class);

    private final JDA jda;
    private final SandboxTradingService sandboxTradingService;
    private final PositionRepository positionRepository;
    private final SandboxUserRepository sandboxUserRepository;

    public MorningDigestScheduler(JDA jda,
                                  SandboxTradingService sandboxTradingService,
                                  PositionRepository positionRepository,
                                  SandboxUserRepository sandboxUserRepository) {
        this.jda = jda;
        this.sandboxTradingService = sandboxTradingService;
        this.positionRepository = positionRepository;
        this.sandboxUserRepository = sandboxUserRepository;
    }

    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Yekaterinburg")
    public void sendMorningDigest() {
        log.info("Запуск утреннего дайджеста");
        try {
            List<SandboxUser> allUsers = sandboxUserRepository.findAll();
            for (SandboxUser user : allUsers) {
                try {
                    if (!user.isMorningDigestEnabled()) continue;
                    List<Position> positions = positionRepository.findByUserId(user.getUserId());
                    if (positions == null || positions.stream().noneMatch(p -> p.getQuantity() > 0)) continue;

                    String digest = buildDigest(user, positions);
                    sendDm(user.getUserId(), digest);
                } catch (Exception e) {
                    log.warn("Ошибка при отправке дайджеста пользователю {}: {}", user.getUserId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при запуске утреннего дайджеста", e);
        }
    }

    String buildDigest(SandboxUser user, List<Position> positions) {
        StringBuilder sb = new StringBuilder();
        sb.append("☀️ **Утренний дайджест**\n\n");
        sb.append("💰 Баланс: **").append(String.format("%.0f ₽", user.getCash())).append("**\n\n");
        sb.append("📦 Открытые позиции:\n");
        boolean hasPositions = false;
        for (Position pos : positions) {
            if (pos.getQuantity() <= 0) continue;
            hasPositions = true;
            try {
                String priceStr = sandboxTradingService.price(pos.getTicker());
                sb.append("• **").append(pos.getTicker()).append("** — ")
                  .append(pos.getQuantity()).append(" шт. | ").append(priceStr).append("\n");
            } catch (Exception e) {
                sb.append("• **").append(pos.getTicker()).append("** — ").append(pos.getQuantity()).append(" шт.\n");
            }
        }
        if (!hasPositions) return null;
        sb.append("\nУдачных торгов! 📈");
        return sb.toString();
    }

    private void sendDm(String userId, String message) {
        if (message == null) return;
        try {
            User discordUser = jda.retrieveUserById(userId).complete();
            if (discordUser != null) {
                discordUser.openPrivateChannel()
                    .flatMap(ch -> ch.sendMessage(message))
                    .queue(
                        success -> log.debug("Дайджест отправлен пользователю {}", userId),
                        err -> log.warn("Не удалось отправить DM пользователю {}: {}", userId, err.getMessage())
                    );
            }
        } catch (Exception e) {
            log.warn("Ошибка при отправке DM пользователю {}: {}", userId, e.getMessage());
        }
    }
}
