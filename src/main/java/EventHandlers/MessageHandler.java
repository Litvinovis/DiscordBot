/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.codahale.metrics.Counter
 *  lombok.Generated
 *  net.dv8tion.jda.api.events.message.MessageReceivedEvent
 *  net.dv8tion.jda.api.hooks.ListenerAdapter
 *  org.jetbrains.annotations.NotNull
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package EventHandlers;

import com.codahale.metrics.Counter;
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

public class MessageHandler
extends ListenerAdapter {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final CurrencyInfoService currencyInfoService;
    private final SharesInfoService sharesInfoService;
    private final HelpInfoService helpInfoService;
    private final SandboxTradingService sandboxTradingService;
    private final Logger logger = LoggerFactory.getLogger((String)"default-logger");
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
            event.getChannel().sendMessage((CharSequence)response).submit();
        }
        catch (Exception e) {
            this.logger.error("\u041e\u0448\u0438\u0431\u043a\u0430: {}", (Object)e.getMessage());
        }
    }

    private String handle(String msg, MessageReceivedEvent event) {
        String lower = msg.toLowerCase(Locale.ROOT);
        String[] p = msg.split("\\s+");
        if (lower.startsWith("+\u0432\u0430\u043b\u044e\u0442\u0430 ")) {
            return this.currencyInfoService.getCurrencyInfo(msg.substring(8));
        }
        if (lower.startsWith("+\u0430\u043a\u0446\u0438\u044f ")) {
            return this.sharesInfoService.getSharesInfo(msg.substring(7));
        }
        if (lower.equals("+\u043f\u043e\u043c\u043e\u0449\u044c")) {
            return this.helpInfoService.getHelpInfo();
        }
        if (lower.equals("+\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f")) {
            return this.sandboxTradingService.register(event.getAuthor().getId(), event.getAuthor().getName());
        }
        if (lower.equals("+\u0430\u043a\u0442\u0438\u0432\u044b")) {
            return this.sandboxTradingService.assets();
        }
        if (lower.equals("+\u043f\u043e\u0440\u0442\u0444\u0435\u043b\u044c")) {
            return this.sandboxTradingService.portfolio(event.getAuthor().getId());
        }
        if (lower.equals("+\u0431\u0430\u043b\u0430\u043d\u0441")) {
            return this.sandboxTradingService.balance(event.getAuthor().getId());
        }
        if (lower.equals("+\u043c\u0430\u0440\u0436\u0430")) {
            return this.sandboxTradingService.margin(event.getAuthor().getId());
        }
        if (p.length == 2 && lower.startsWith("+\u0446\u0435\u043d\u0430 ")) {
            return this.sandboxTradingService.price(p[1]);
        }
        if (p.length == 3 && lower.startsWith("+\u043a\u0443\u043f\u0438\u0442\u044c ")) {
            return this.sandboxTradingService.buy(event.getAuthor().getId(), event.getAuthor().getName(), p[1], this.parseInt(p[2]));
        }
        if (p.length == 3 && lower.startsWith("+\u043f\u0440\u043e\u0434\u0430\u0442\u044c ")) {
            return this.sandboxTradingService.sell(event.getAuthor().getId(), event.getAuthor().getName(), p[1], this.parseInt(p[2]));
        }
        if (p.length >= 2 && lower.startsWith("+\u0442\u043e\u043f ")) {
            String period = p[1].toLowerCase(Locale.ROOT);
            if (period.equals("\u0434\u0435\u043d\u044c") || period.equals("\u043d\u0435\u0434\u0435\u043b\u044f") || period.equals("\u043c\u0435\u0441\u044f\u0446")) {
                return this.sandboxTradingService.top(period);
            }
            if (period.equals("\u0432\u0441\u0435") || period.equals("\u0432\u0441\u0451")) {
                return this.sandboxTradingService.top("all");
            }
        }
        // +мой-рейтинг
        if (lower.equals("+\u043c\u043e\u0439-\u0440\u0435\u0439\u0442\u0438\u043d\u0433")) {
            return this.sandboxTradingService.myRank(event.getAuthor().getId());
        }
        // +история
        if (lower.equals("+\u0438\u0441\u0442\u043e\u0440\u0438\u044f")) {
            return this.sandboxTradingService.history(event.getAuthor().getId());
        }
        // +стата / +статистика
        if (lower.equals("+\u0441\u0442\u0430\u0442\u0430") || lower.equals("+\u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430")) {
            return this.sandboxTradingService.stats(event.getAuthor().getId());
        }
        // +стоп-лосс TICKER PRICE
        if (p.length == 3 && lower.startsWith("+\u0441\u0442\u043e\u043f-\u043b\u043e\u0441\u0441 ")) {
            double price = this.parseDouble(p[2]);
            if (price <= 0) return "\u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0443\u044e \u0446\u0435\u043d\u0443: +\u0441\u0442\u043e\u043f-\u043b\u043e\u0441\u0441 \u0422\u0418\u041a\u0415\u0420 \u0426\u0415\u041d\u0410";
            return this.sandboxTradingService.setStopLoss(event.getAuthor().getId(), p[1], price);
        }
        // +тейк-профит TICKER PRICE
        if (p.length == 3 && lower.startsWith("+\u0442\u0435\u0439\u043a-\u043f\u0440\u043e\u0444\u0438\u0442 ")) {
            double price = this.parseDouble(p[2]);
            if (price <= 0) return "\u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0443\u044e \u0446\u0435\u043d\u0443: +\u0442\u0435\u0439\u043a-\u043f\u0440\u043e\u0444\u0438\u0442 \u0422\u0418\u041a\u0415\u0420 \u0426\u0415\u041d\u0410";
            return this.sandboxTradingService.setTakeProfit(event.getAuthor().getId(), p[1], price);
        }
        // +лимит-куплю TICKER QTY PRICE
        if (p.length == 4 && lower.startsWith("+\u043b\u0438\u043c\u0438\u0442-\u043a\u0443\u043f\u043b\u044e ")) {
            int qty = this.parseInt(p[2]);
            double price = this.parseDouble(p[3]);
            if (qty <= 0 || price <= 0) return "\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: +\u043b\u0438\u043c\u0438\u0442-\u043a\u0443\u043f\u043b\u044e \u0422\u0418\u041a\u0415\u0420 \u041a\u041e\u041b-\u0412\u041e \u0426\u0415\u041d\u0410";
            return this.sandboxTradingService.placeLimitBuy(event.getAuthor().getId(), event.getAuthor().getName(), p[1], qty, price);
        }
        // +лимит-продам TICKER QTY PRICE
        if (p.length == 4 && lower.startsWith("+\u043b\u0438\u043c\u0438\u0442-\u043f\u0440\u043e\u0434\u0430\u043c ")) {
            int qty = this.parseInt(p[2]);
            double price = this.parseDouble(p[3]);
            if (qty <= 0 || price <= 0) return "\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: +\u043b\u0438\u043c\u0438\u0442-\u043f\u0440\u043e\u0434\u0430\u043c \u0422\u0418\u041a\u0415\u0420 \u041a\u041e\u041b-\u0412\u041e \u0426\u0415\u041d\u0410";
            return this.sandboxTradingService.placeLimitSell(event.getAuthor().getId(), event.getAuthor().getName(), p[1], qty, price);
        }
        // +мои-заявки
        if (lower.equals("+\u043c\u043e\u0438-\u0437\u0430\u044f\u0432\u043a\u0438")) {
            return this.sandboxTradingService.myOrders(event.getAuthor().getId());
        }
        // +отмена-заявки ID
        if (p.length == 2 && lower.startsWith("+\u043e\u0442\u043c\u0435\u043d\u0430-\u0437\u0430\u044f\u0432\u043a\u0438 ")) {
            return this.sandboxTradingService.cancelOrder(event.getAuthor().getId(), p[1]);
        }
        // +алерт TICKER PRICE
        if (p.length == 3 && lower.startsWith("+\u0430\u043b\u0435\u0440\u0442 ")) {
            double price = this.parseDouble(p[2]);
            if (price <= 0) return "\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: +\u0430\u043b\u0435\u0440\u0442 \u0422\u0418\u041a\u0415\u0420 \u0426\u0415\u041d\u0410";
            return this.sandboxTradingService.setAlert(event.getAuthor().getId(), p[1], price);
        }
        return "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u0430, \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 '+\u043f\u043e\u043c\u043e\u0449\u044c'";
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (Exception e) {
            return -1;
        }
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return -1.0;
        }
    }

    private boolean isBotAsking(MessageReceivedEvent event) {
        return event.getMessage().getContentDisplay().startsWith("+") && ALLOW_CHANNEL_IDS.contains(event.getChannel().getId());
    }
}
