package com.discord.stonks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tinkoff")
public record TinkoffProperties(
        String token,
        String apiMode,
        String target
) {
    public TinkoffProperties {
        if (apiMode == null) apiMode = "prod";
        if (target == null)  target  = "dns:///invest-public-api.tinkoff.ru:443";
    }
}
