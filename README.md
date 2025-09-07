# DiscordBot

A Discord bot that provides financial information and entertainment features.

## Features

- Provides real-time financial data (currency exchange rates, stock/share information) from Tinkoff Invest API
- Interactive mini-games for entertainment
- Automated statistical reporting via scheduled tasks
- Help and usage guidance within Discord

## Configuration

The bot requires configuration through environment variables or the `application.yml` file:

```yaml
discord:
  token: ${DISCORD_TOKEN:your_discord_token_here}

tinkoff:
  token: ${TINKOFF_TOKEN:your_tinkoff_token_here}
  api-mode: ${TINKOFF_API_MODE:readonly}
```

### Environment Variables

- `DISCORD_TOKEN` - Your Discord bot token
- `TINKOFF_TOKEN` - Your Tinkoff Invest API token

## Building

```bash
mvn clean package
```

## Running

```bash
java -jar target/DiscordBot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Commands

- `+акция (имя или часть имени компании)` - информация о стоимости акции
- `+валюта (тикер валюты например USD)` - информация о курсе валюты к рублю
- `+помощь` - вывод информации о доступных командах

## Development

### Adding New Commands

1. Create a new service class in the `services` package
2. Register the service in the `MessageHandler`
3. Add command handling logic in the `onMessageReceived` method

### Creating New Games

1. Implement the `Game` interface in the `games` package
2. Register the game in the `GameService`