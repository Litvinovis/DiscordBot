package services.sandbox;

import com.discord.stonks.config.DiscordProperties;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SandboxReportScheduler {

	private static final Logger log = LoggerFactory.getLogger(SandboxReportScheduler.class);

	private final SandboxTradingService service;
	private final JDA jda;
	private final String channelId;
	private final String period;

	public SandboxReportScheduler(SandboxTradingService service,
								   JDA jda,
								   DiscordProperties discordProperties,
								   @Value("${reports.sandbox-report-period}") String period) {
		this.service = service;
		this.jda = jda;
		this.period = period;
		var ids = discordProperties.allowedChannelIds();
		this.channelId = ids.isEmpty() ? "" : ids.getFirst();
	}

	@Scheduled(cron = "${reports.sandbox-report-cron}", zone = "Asia/Yekaterinburg")
	public void sendReport() {
		if (channelId.isBlank()) {
			log.warn("SandboxReportScheduler: channelId не задан, пропуск отчёта");
			return;
		}
		try {
			MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
			if (channel != null) {
				channel.sendMessage(service.top(period)).queue();
			}
		} catch (Exception e) {
			log.error("Ошибка при отправке отчёта за период {}: {}", period, e.getMessage(), e);
		}
	}
}
