package com.discord.stonks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("discord")
public record DiscordProperties(
		String token,
		List<String> allowedChannelIds,
		String reportGuildId,
		String reportChannelName
) {
	public DiscordProperties {
		if (allowedChannelIds == null) allowedChannelIds = List.of();
	}
}
