# Stonks Bot

Discord-бот для симуляции торговли акциями. Подключается к T-Bank Invest API (Тинькофф) для получения реальных котировок, а внутри ведёт виртуальный торговый счёт каждого участника сервера с балансом, портфелем, лимитными заявками, стоп-лоссами и тейк-профитами.

Данные хранятся в PostgreSQL, что обеспечивает сохранность состояния между перезапусками бота.

---

## Стек технологий

| Компонент | Версия |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| JDA (Discord API) | 6.5 |
| T-Bank Invest Java SDK | 1.49.6 |
| PostgreSQL | 16 |
| HikariCP | 7.0.2 |
| Logback | из Spring Boot BOM |
| Lombok | 1.18.48 |
| Maven | 3.x |

---

## Доступные команды

Все команды начинаются с символа `+` и пишутся в разрешённом текстовом канале.

### Информация

| Команда | Описание |
|---|---|
| `+помощь` | Показать справку по командам |
| `+акция <тикер/название>` | Информация об акции (цена, название, биржа) |
| `+валюта <тикер>` | Информация о валютной паре |
| `+активы` | Список доступных для торговли тикеров |
| `+цена <ТИКЕР>` | Текущая цена актива в песочнице |

### Торговля (песочница)

| Команда | Описание |
|---|---|
| `+регистрация` | Зарегистрироваться и получить стартовый баланс (1 000 000 ₽) |
| `+купить <ТИКЕР> <кол-во>` | Купить акции по рыночной цене |
| `+продать <ТИКЕР> <кол-во>` | Продать акции по рыночной цене |
| `+лимит-куплю <ТИКЕР> <кол-во> <цена>` | Выставить лимитную заявку на покупку |
| `+лимит-продам <ТИКЕР> <кол-во> <цена>` | Выставить лимитную заявку на продажу |
| `+мои-заявки` | Список активных лимитных заявок |
| `+отмена-заявки <id>` | Отменить лимитную заявку по ID |
| `+стоп-лосс <ТИКЕР> <цена>` | Установить стоп-лосс на позицию |
| `+тейк-профит <ТИКЕР> <цена>` | Установить тейк-профит на позицию |
| `+алерт <ТИКЕР> <цена>` | Получить уведомление при достижении цены |

### Аналитика

| Команда | Описание |
|---|---|
| `+портфель` | Текущий портфель (позиции и P&L) |
| `+баланс` | Свободный кэш на счёте |
| `+маржа` | Информация о марже и плече |
| `+история` | История совершённых сделок |
| `+стата` / `+статистика` | Личная статистика (win rate, PnL и т.д.) |
| `+топ день\|неделя\|месяц\|все` | Таблица лидеров по периоду |
| `+мой-рейтинг` | Своя позиция в рейтинге |
| `+дайджест вкл\|выкл` | Подписка на дайджест портфеля в личные сообщения |

---

## Автоматические уведомления

| Что | Когда по умолчанию | Настройка |
|---|---|---|
| Рейтинг доходности в канал | понедельник, 10:00 | `SANDBOX_REPORT_CRON`, период — `SANDBOX_REPORT_PERIOD` |
| Дайджест портфеля в ЛС | понедельник, 9:00 | `MORNING_DIGEST_CRON`, включается командой `+дайджест вкл` |
| Отчёт по валютам | ежедневно, 10:00 | `CURRENCY_REPORT_CRON` |
| Отчёт по акциям | ежедневно, 10:05 | `SHARES_REPORT_CRON` |
| Лимитные заявки, стопы, алерты | проверка раз в минуту | — |
| DCA-ордера | проверка раз в час | — |

Время — `Asia/Yekaterinburg` (UTC+5).

---

## Конфигурация

Бот читает настройки из переменных окружения (приоритет) или из файла `application.yml` / `config/application.yml`.

### Обязательные переменные

| Переменная | Описание |
|---|---|
| `DISCORD_BOT_TOKEN` | Токен Discord-бота (из Discord Developer Portal) |
| `TINKOFF_TOKEN` | Токен T-Bank Invest API (readonly достаточно для котировок) |

### Необязательные переменные

| Переменная | По умолчанию | Описание |
|---|---|---|
| `DISCORD_ALLOWED_CHANNEL_IDS` | `1157258712138907700` | ID каналов через запятую |
| `TINKOFF_API_MODE` | `prod` | `prod` или `sandbox` |
| `INVEST_API_TARGET` | `dns:///invest-public-api.tbank.ru:443` | gRPC-endpoint Invest API. SDK ≥1.49.2 проверяет сертификаты вшитым truststore (корень Минцифры), поэтому старый endpoint `tinkoff.ru` не работает |
| `SANDBOX_START_BALANCE` | `1000000.00` | Стартовый баланс песочницы (₽) |
| `SANDBOX_COMMISSION_RATE` | `0.001` | Комиссия (0.1%) |
| `SANDBOX_MAX_LEVERAGE` | `3.0` | Максимальное плечо |
| `SANDBOX_MAINTENANCE_MARGIN` | `0.25` | Порог margin call: при margin level ниже него позиции ликвидируются |
| `SANDBOX_REPORT_CRON` | `0 0 10 ? * MON` | Когда слать рейтинг доходности в канал |
| `SANDBOX_REPORT_PERIOD` | `неделя` | Период рейтинга: `день`, `неделя` или `месяц` |
| `MORNING_DIGEST_CRON` | `0 0 9 ? * MON` | Когда слать дайджест портфеля в ЛС |
| `CURRENCY_REPORT_CRON` | `0 0 10 * * *` | Отчёт по валютам |
| `SHARES_REPORT_CRON` | `0 5 10 * * *` | Отчёт по акциям |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/stonks` | JDBC URL базы данных |
| `DB_USER` | `stonks` | Пользователь PostgreSQL |
| `DB_PASSWORD` | — | Пароль пользователя PostgreSQL |

> **Важно:** файл `application.yml` добавлен в `.gitignore` — он не попадёт в репозиторий.

---

## Сборка и запуск

### Требования

- JDK 25+
- Maven 3.6+
- PostgreSQL 16+

### Сборка fat-jar

```bash
mvn package -DskipTests
```

Артефакт: `target/DiscordBot.jar`

### Запуск локально

```bash
export DISCORD_BOT_TOKEN=your-token
export TINKOFF_TOKEN=your-tinkoff-token
java -jar target/DiscordBot.jar
```

Схема БД создаётся автоматически при первом запуске (`schema.sql`).

---

## Деплой через GitHub Actions

CI/CD настроен в `.github/workflows/deploy.yml` и запускается при каждом пуше в `main`.

### Что происходит при деплое

1. **Checkout** — получение исходников.
2. **Build & Test** — `mvn package -B` (сборка + тесты).
3. **Deploy** — копирование `DiscordBot.jar` в `/opt/DiscordBot/` и рестарт systemd-сервиса `bot-discord`.
4. **Smoke test** — проверка, что сервис запустился и в логах нет критичных ошибок. При неудаче — автоматический откат на предыдущий jar.
5. **Telegram notification** — уведомление об успехе или ошибке деплоя.

### Требования к runner

- Self-hosted GitHub Actions runner на сервере деплоя.
- Java 25 (устанавливается шагом `actions/setup-java`).
- Systemd-сервис `bot-discord`, который запускает `DiscordBot.jar` с нужными переменными окружения.
- Runner имеет права на `sudo systemctl restart bot-discord` и `sudo cp`.

### GitHub Secrets (обязательные для деплоя)

| Secret | Описание |
|---|---|
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота для уведомлений |
| `TELEGRAM_CHAT_ID` | ID чата для уведомлений |

Токены Discord и Tinkoff передаются через переменные окружения systemd-юнита на сервере — они не хранятся в репозитории.

### Пример systemd unit (`/etc/systemd/system/bot-discord.service`)

```ini
[Unit]
Description=Stonks Discord Bot
After=network.target

[Service]
Type=simple
User=botuser
WorkingDirectory=/opt/DiscordBot
EnvironmentFile=/opt/DiscordBot/.env
ExecStart=/usr/bin/java -jar /opt/DiscordBot/DiscordBot.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## Структура проекта

```
src/main/java/
├── App.java                          # Точка входа
├── commands/                         # Команды бота
├── events/
│   └── MessageHandler.java           # Обработка команд Discord
├── services/
│   ├── CurrencyInfoService.java      # Информация о валютах
│   ├── SharesInfoService.java        # Информация об акциях
│   ├── HelpInfoService.java          # Справка
│   ├── sandbox/
│   │   ├── SandboxTradingService.java    # Логика торговли
│   │   ├── SandboxOrderScheduler.java    # Исполнение лимитных и стоп-заявок, алертов
│   │   ├── SandboxReportScheduler.java   # Рейтинг доходности в канал (по умолчанию еженедельно)
│   │   ├── MorningDigestScheduler.java   # Дайджест портфеля в ЛС (по умолчанию еженедельно)
│   │   ├── SandboxPriceService.java      # Котировки: загрузка списком и кэш на 5 секунд
│   │   ├── SandboxRiskManager.java       # Плечо и порог margin call
│   │   ├── migration/SandboxMigrationService.java  # Миграция данных при старте
│   │   ├── model/                    # Модели данных
│   │   └── repository/               # JDBC-репозитории
│   ├── statTask/                     # Задачи сбора статистики
│   └── tbank/TInvestApi.java         # Обёртка над T-Bank gRPC API
└── config/                           # Spring-конфигурация и свойства
```

---

## Лицензия

См. файл [LICENSE](LICENSE).
