package EventHandlers;

import commands.BotCommand;
import commands.CurrencyInfoCommand;
import commands.HelpCommand;
import commands.SandboxAlertCommand;
import commands.SandboxAssetsCommand;
import commands.SandboxBalanceCommand;
import commands.SandboxBuyCurrencyCommand;
import commands.SandboxBuyCommand;
import commands.SandboxCancelOrderCommand;
import commands.SandboxCurrencyPortfolioCommand;
import commands.SandboxHistoryCommand;
import commands.SandboxLimitBuyCommand;
import commands.SandboxLimitSellCommand;
import commands.SandboxMarginCommand;
import commands.SandboxMyOrdersCommand;
import commands.SandboxMyRankCommand;
import commands.SandboxPortfolioCommand;
import commands.SandboxPriceCommand;
import commands.SandboxRegisterCommand;
import commands.SandboxSellCommand;
import commands.SandboxSellCurrencyCommand;
import commands.SandboxStatsCommand;
import commands.SandboxStopLossCommand;
import commands.SandboxTakeProfitCommand;
import commands.SandboxTopCommand;
import commands.SharesInfoCommand;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Generated;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.CurrencyInfoService;
import services.HelpInfoService;
import services.SharesInfoService;
import services.sandbox.SandboxCurrencyService;
import services.sandbox.SandboxTradingService;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

/**
 * Обработчик входящих сообщений Discord.
 *
 * <p>Слушает события {@link MessageReceivedEvent}, фильтрует сообщения по
 * разрешённым каналам и делегирует выполнение подходящей команде из списка
 * зарегистрированных {@link BotCommand}.
 */
public class MessageHandler extends ListenerAdapter {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final Logger logger = LoggerFactory.getLogger("default-logger");
    private static final Set<String> ALLOW_CHANNEL_IDS = Set.copyOf(ConfigLoader.getAllowedChannelIds());

    private final List<BotCommand> commands;

    /**
     * Инициализирует обработчик и все зарегистрированные команды бота.
     *
     * @param api                   клиент T-Invest API для рыночных данных
     * @param sandboxTradingService сервис торговли в режиме песочницы
     */
    public MessageHandler(TInvestApi api, SandboxTradingService sandboxTradingService) {
        CurrencyInfoService currencyInfoService = new CurrencyInfoService(api);
        SharesInfoService sharesInfoService = new SharesInfoService(api);
        HelpInfoService helpInfoService = new HelpInfoService(api);
        SandboxCurrencyService sandboxCurrencyService = sandboxTradingService.createCurrencyService();

        this.commands = List.of(
                new CurrencyInfoCommand(currencyInfoService),
                new SharesInfoCommand(sharesInfoService),
                new HelpCommand(helpInfoService),
                new SandboxRegisterCommand(sandboxTradingService),
                new SandboxAssetsCommand(sandboxTradingService),
                new SandboxPortfolioCommand(sandboxTradingService),
                new SandboxBalanceCommand(sandboxTradingService),
                new SandboxMarginCommand(sandboxTradingService),
                new SandboxPriceCommand(sandboxTradingService),
                new SandboxBuyCommand(sandboxTradingService),
                new SandboxSellCommand(sandboxTradingService),
                new SandboxBuyCurrencyCommand(sandboxCurrencyService),
                new SandboxSellCurrencyCommand(sandboxCurrencyService),
                new SandboxCurrencyPortfolioCommand(sandboxCurrencyService),
                new SandboxTopCommand(sandboxTradingService),
                new SandboxMyRankCommand(sandboxTradingService),
                new SandboxHistoryCommand(sandboxTradingService),
                new SandboxStatsCommand(sandboxTradingService),
                new SandboxStopLossCommand(sandboxTradingService),
                new SandboxTakeProfitCommand(sandboxTradingService),
                new SandboxLimitBuyCommand(sandboxTradingService),
                new SandboxLimitSellCommand(sandboxTradingService),
                new SandboxMyOrdersCommand(sandboxTradingService),
                new SandboxCancelOrderCommand(sandboxTradingService),
                new SandboxAlertCommand(sandboxTradingService)
        );
    }

    /**
     * Вызывается JDA при получении нового сообщения.
     * Обрабатывает только сообщения, начинающиеся с «+» в разрешённых каналах.
     *
     * @param event событие Discord о полученном сообщении
     */
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            if (!this.isBotAsking(event)) {
                return;
            }
            String msg = event.getMessage().getContentDisplay().trim();
            String response = this.handle(msg, event);
            event.getChannel().sendMessage((CharSequence) response).submit();
        } catch (Exception e) {
            this.logger.error("Ошибка: {}", (Object) e.getMessage());
        }
    }

    private String handle(String msg, MessageReceivedEvent event) {
        String lower = msg.toLowerCase(Locale.ROOT);
        String[] parts = msg.split("\\s+");

        for (BotCommand command : commands) {
            if (command.matches(lower, parts)) {
                return command.execute(event, msg, parts);
            }
        }

        return "неизвестная команда, напишите '+помощь'";
    }

    private boolean isBotAsking(MessageReceivedEvent event) {
        return event.getMessage().getContentDisplay().startsWith("+")
                && ALLOW_CHANNEL_IDS.contains(event.getChannel().getId());
    }
}
