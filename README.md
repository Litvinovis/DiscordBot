# Stonks Bot

Discord-бот для симуляции торговли акциями. Подключается к T-Bank Invest API (Тинькофф) для получения реальных котировок, а внутри ведёт виртуальный торговый счёт каждого участника сервера с балансом, портфелем, лимитными заявками, стоп-лоссами и тейк-профитами.

Данные хранятся в Apache Ignite, что обеспечивает сохранность состояния между перезапусками бота.

---

## Стек технологий

| Компонент | Версия |
|---|---|
| Java | 21 |
| JDA (Discord API) | 6.4.1 |
| T-Bank Invest Java SDK | 1.48 |
| Apache Ignite | 3.1.0 (thin client) |
| Logback | 1.5.32 |
| Lombok | 1.18.44 |
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

---

## Конфигурация

Бот читает настройки из переменных окружения (приоритет) или из файла `application.yml` / `config/application.yml`.

Скопируй `.env.example` в `.env` и заполни значениями:

```bash
cp .env.example .env
```

### Обязательные переменные

| Переменная | Описание |
|---|---|
| `DISCORD_BOT_TOKEN` | Токен Discord-бота (из Discord Developer Portal) |
| `TINKOFF_TOKEN` | Токен T-Bank Invest API (readonly достаточно для котировок) |

### Необязательные переменные

| Переменная | По умолчанию | Описание |
|---|---|---|
| `DISCORD_ALLOWED_CHANNEL_IDS` | `1157258712138907700` | ID каналов через запятую |
| `TINKOFF_API_MODE` | `readonly` | `prod`, `sandbox` или `readonly` |
| `SANDBOX_START_BALANCE` | `1000000.00` | Стартовый баланс песочницы (₽) |
| `SANDBOX_COMMISSION_RATE` | `0.001` | Комиссия (0.1%) |
| `SANDBOX_MAX_LEVERAGE` | `3.0` | Максимальное плечо |
| `IGNITE_LOCAL_ADDRESS` | `127.0.0.1` | IP-адрес узла Ignite |
| `IGNITE_DISCOVERY_ADDRESSES` | `127.0.0.1:47500..47509` | Адреса discovery через запятую |
| `IGNITE_WORK_DIR` | `/tmp/ignite-stonks-client` | Рабочая директория Ignite |

> **Важно:** файлы `.env` и `application.yml` добавлены в `.gitignore` — они не попадут в репозиторий.

---

## Сборка и запуск

### Требования

- JDK 21+
- Maven 3.6+

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

Или с файлом конфига рядом с jar:

```bash
java -jar DiscordBot.jar
# бот ищет application.yml и config/application.yml в рабочей директории
```

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
- Java 21 (устанавливается шагом `actions/setup-java`).
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
├── events/
│   └── MessageHandler.java           # Обработка команд Discord
├── services/
│   ├── CurrencyInfoService.java      # Информация о валютах
│   ├── SharesInfoService.java        # Информация об акциях
│   ├── HelpInfoService.java          # Справка
│   ├── StatisticsSenderService.java  # Расписание отчётов
│   ├── sandbox/
│   │   ├── SandboxTradingService.java    # Логика торговли
│   │   ├── SandboxOrderScheduler.java    # Исполнение лимитных заявок
│   │   ├── SandboxReportScheduler.java   # Ежедневные отчёты
│   │   ├── ignite/SandboxIgniteManager.java  # Управление Ignite
│   │   ├── migration/SandboxMigrationService.java  # Миграция схемы
│   │   └── model/                    # Модели данных
│   ├── statTask/                     # Задачи сбора статистики
│   └── tbank/TInvestApi.java         # Обёртка над T-Bank gRPC API
└── utils/
    ├── ConfigLoader.java             # Загрузка конфигурации
    └── Constants.java
```

---

## Лицензия

См. файл [LICENSE](LICENSE).
