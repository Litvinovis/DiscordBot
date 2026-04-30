package com.discord.stonks.config;

import events.MessageHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import services.sandbox.CbrRateService;
import services.sandbox.SandboxCurrencyService;
import services.sandbox.SandboxTradingService;
import services.sandbox.repository.SandboxUserRepository;
import services.tbank.TInvestApi;

@Configuration
@EnableConfigurationProperties({DiscordProperties.class, TinkoffProperties.class, SandboxProperties.class})
public class JdaConfig {

	private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

	@Bean
	public TInvestApi tInvestApi(TinkoffProperties props) {
		String target = props.target();
		if (!target.contains(":///")) {
			target = "dns:///" + target;
		}
		return TInvestApi.create(
				props.token(),
				"sandbox".equalsIgnoreCase(props.apiMode()),
				target
		);
	}

	@Bean
	public SandboxCurrencyService sandboxCurrencyService(SandboxUserRepository users,
														  CbrRateService cbrRateService,
														  SandboxTradingService tradingService) {
		return new SandboxCurrencyService(users, cbrRateService, tradingService.userLocks);
	}

	@Bean
	public JDA jda(MessageHandler messageHandler, DiscordProperties props) throws Exception {
		String token = props.token();
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("Токен Discord не задан (discord.token)");
		}

		int delaySec = 5;
		while (true) {
			try {
				JDA jda = JDABuilder.createDefault(token)
						.enableIntents(GatewayIntent.MESSAGE_CONTENT)
						.addEventListeners(messageHandler)
						.setActivity(Activity.playing("NASDAQ"))
						.build();
				jda.awaitReady();
				log.info("Discord JDA успешно инициализирован");
				return jda;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			} catch (Exception e) {
				log.warn("Не удалось подключиться к Discord ({}), повтор через {} сек", e.getMessage(), delaySec);
				Thread.sleep(delaySec * 1000L);
				delaySec = Math.min(delaySec * 2, 60);
			}
		}
	}
}
