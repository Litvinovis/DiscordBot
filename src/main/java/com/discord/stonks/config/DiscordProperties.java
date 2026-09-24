package com.discord.stonks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@ConfigurationProperties("discord")
public record DiscordProperties(
		String token,
		List<String> allowedChannelIds,
		String reportGuildId,
		String reportChannelName
) {
	public DiscordProperties {
		if (allowedChannelIds == null) allowedChannelIds = List.of();
		// DISCORD_ALLOWED_CHANNEL_IDS подставляется одним элементом списка: «id1,id2»
		// не совпадал ни с одним каналом, и бот молчал везде
		allowedChannelIds = allowedChannelIds.stream()
				.filter(Objects::nonNull)
				.flatMap(v -> Arrays.stream(v.split(",")))
				.map(String::trim)
				.filter(v -> !v.isEmpty())
				.toList();
	}
}
