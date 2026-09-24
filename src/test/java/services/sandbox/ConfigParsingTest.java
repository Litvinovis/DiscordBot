package services.sandbox;

import com.discord.stonks.config.DiscordProperties;
import com.discord.stonks.config.TinkoffProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParsingTest {

	@Test
	void channelIds_commaSeparatedEnvValue_splitIntoIds() {
		// DISCORD_ALLOWED_CHANNEL_IDS=id1, id2 приходит одним элементом списка
		DiscordProperties props = new DiscordProperties("t", List.of("111, 222", ""), null, null);

		assertEquals(List.of("111", "222"), props.allowedChannelIds());
	}

	@Test
	void tinkoffTarget_defaultsToTbankEndpoint() {
		assertTrue(new TinkoffProperties("t", null, null).target().contains("tbank.ru"));
	}
}
