package EventHandlers;

import com.codahale.metrics.Counter;
import java.math.BigDecimal;
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
import services.sandbox.SandboxTradingService;
import services.tbank.TInvestApi;
import utils.ConfigLoader;

public class MessageHandler extends ListenerAdapter {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final CurrencyInfoService currencyInfoService;
    private final SharesInfoService sharesInfoService;
    private final HelpInfoService helpInfoService;
    private final SandboxTradingService sandboxTradingService;
    private final Logger logger = LoggerFactory.getLogger("default-logger");
    private static final Set<String> ALLOW_CHANNEL_IDS = Set.copyOf(ConfigLoader.getAllowedChannelIds());
    public static final Counter JOB_COPY_SUCCESS = new Counter();

    public MessageHandler(TInvestApi api, SandboxTradingService sandboxTradingService) {
        this.currencyInfoService = new CurrencyInfoService(api);
        this.sharesInfoService = new SharesInfoService(api);
        this.helpInfoService = new HelpInfoService(api);
        this.sandboxTradingService = sandboxTradingService;
    }

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
        String[] p = msg.split("\\s+");
        if (lower.startsWith("+валюта ")) {
            return this.currencyInfoService.getCurrencyInfo(msg.substring(8));
        }
        if (lower.startsWith("+акция ")) {
            return this.sharesInfoService.getSharesInfo(msg.substring(7));
        }
        if (lower.equals("+помощь")) {
            return this.helpInfoService.getHelpInfo();
        }
        if (lower.equals("+регистрация")) {
            return this.sandboxTradingService.register(event.getAuthor().getId(), event.getAuthor().getName());
        }
        if (lower.equals("+активы")) {
            return this.sandboxTradingService.assets();
        }
        if (lower.equals("+портфель")) {
            return this.sandboxTradingService.portfolio(event.getAuthor().getId());
        }
        if (lower.equals("+баланс")) {
            return this.sandboxTradingService.balance(event.getAuthor().getId());
        }
        if (lower.equals("+маржа")) {
            return this.sandboxTradingService.margin(event.getAuthor().getId());
        }
        if (p.length == 2 && lower.startsWith("+цена ")) {
            return this.sandboxTradingService.price(p[1]);
        }
        if (p.length == 3 && lower.startsWith("+купить ")) {
            return this.sandboxTradingService.buy(event.getAuthor().getId(), event.getAuthor().getName(), p[1], this.parseInt(p[2]));
        }
        if (p.length == 3 && lower.startsWith("+продать ")) {
            return this.sandboxTradingService.sell(event.getAuthor().getId(), event.getAuthor().getName(), p[1], this.parseInt(p[2]));
        }
        if (p.length >= 2 && lower.startsWith("+топ ")) {
            String period = p[1].toLowerCase(Locale.ROOT);
            if (period.equals("день") || period.equals("неделя") || period.equals("месяц")) {
                return this.sandboxTradingService.top(period);
            }
            if (period.equals("все") || period.equals("всё")) {
                return this.sandboxTradingService.top("all");
            }
        }
        if (lower.equals("+мой-рейтинг")) {
            return this.sandboxTradingService.myRank(event.getAuthor().getId());
        }
        if (lower.equals("+история")) {
            return this.sandboxTradingService.history(event.getAuthor().getId());
        }
        if (lower.equals("+стата") || lower.equals("+статистика")) {
            return this.sandboxTradingService.stats(event.getAuthor().getId());
        }
        // +стоп-лосс TICKER PRICE
        if (p.length == 3 && lower.startsWith("+стоп-лосс ")) {
            BigDecimal price = this.parseBigDecimal(p[2]);
            if (price == null || price.signum() <= 0) return "Укажите корректную цену: +стоп-лосс ТИКЕР ЦЕНА";
            return this.sandboxTradingService.setStopLoss(event.getAuthor().getId(), p[1], price);
        }
        // +тейк-профит TICKER PRICE
        if (p.length == 3 && lower.startsWith("+тейк-профит ")) {
            BigDecimal price = this.parseBigDecimal(p[2]);
            if (price == null || price.signum() <= 0) return "Укажите корректную цену: +тейк-профит ТИКЕР ЦЕНА";
            return this.sandboxTradingService.setTakeProfit(event.getAuthor().getId(), p[1], price);
        }
        // +лимит-куплю TICKER QTY PRICE
        if (p.length == 4 && lower.startsWith("+лимит-куплю ")) {
            int qty = this.parseInt(p[2]);
            BigDecimal price = this.parseBigDecimal(p[3]);
            if (qty <= 0 || price == null || price.signum() <= 0) return "Использование: +лимит-куплю ТИКЕР КОЛ-ВО ЦЕНА";
            return this.sandboxTradingService.placeLimitBuy(event.getAuthor().getId(), event.getAuthor().getName(), p[1], qty, price);
        }
        // +лимит-продам TICKER QTY PRICE
        if (p.length == 4 && lower.startsWith("+лимит-продам ")) {
            int qty = this.parseInt(p[2]);
            BigDecimal price = this.parseBigDecimal(p[3]);
            if (qty <= 0 || price == null || price.signum() <= 0) return "Использование: +лимит-продам ТИКЕР КОЛ-ВО ЦЕНА";
            return this.sandboxTradingService.placeLimitSell(event.getAuthor().getId(), event.getAuthor().getName(), p[1], qty, price);
        }
        if (lower.equals("+мои-заявки")) {
            return this.sandboxTradingService.myOrders(event.getAuthor().getId());
        }
        if (p.length == 2 && lower.startsWith("+отмена-заявки ")) {
            return this.sandboxTradingService.cancelOrder(event.getAuthor().getId(), p[1]);
        }
        // +алерт TICKER PRICE
        if (p.length == 3 && lower.startsWith("+алерт ")) {
            BigDecimal price = this.parseBigDecimal(p[2]);
            if (price == null || price.signum() <= 0) return "Использование: +алерт ТИКЕР ЦЕНА";
            return this.sandboxTradingService.setAlert(event.getAuthor().getId(), p[1], price);
        }
        return "неизвестная команда, напишите '+помощь'";
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBotAsking(MessageReceivedEvent event) {
        return event.getMessage().getContentDisplay().startsWith("+")
                && ALLOW_CHANNEL_IDS.contains(event.getChannel().getId());
    }
}
