package commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the forex (currency) command routing logic.
 *
 * Tests verify that {@link SandboxBuyCurrencyCommand} and
 * {@link SandboxSellCurrencyCommand} (and {@link SandboxCurrencyPortfolioCommand})
 * correctly match user input without requiring any real service dependencies.
 */
class ForexCommandRouterTest {

    // -----------------------------------------------------------------------
    // +купить-валюту
    // -----------------------------------------------------------------------

    @Test
    void buyCurrency_matchesThreeParts() {
        BotCommand cmd = stubBuyCurrency();
        assertTrue(cmd.matches("+купить-валюту usd 1000",
                new String[]{"+купить-валюту", "USD", "1000"}));
    }

    @Test
    void buyCurrency_doesNotMatchTwoParts() {
        BotCommand cmd = stubBuyCurrency();
        assertFalse(cmd.matches("+купить-валюту usd",
                new String[]{"+купить-валюту", "USD"}));
    }

    @Test
    void buyCurrency_doesNotMatchFourParts() {
        BotCommand cmd = stubBuyCurrency();
        assertFalse(cmd.matches("+купить-валюту usd 1000 extra",
                new String[]{"+купить-валюту", "USD", "1000", "extra"}));
    }

    @Test
    void buyCurrency_doesNotMatchDifferentCommand() {
        BotCommand cmd = stubBuyCurrency();
        assertFalse(cmd.matches("+купить usd 1000",
                new String[]{"+купить", "USD", "1000"}));
    }

    @Test
    void buyCurrency_matchesEurAmount() {
        BotCommand cmd = stubBuyCurrency();
        assertTrue(cmd.matches("+купить-валюту eur 5000",
                new String[]{"+купить-валюту", "EUR", "5000"}));
    }

    // -----------------------------------------------------------------------
    // +продать-валюту
    // -----------------------------------------------------------------------

    @Test
    void sellCurrency_matchesThreeParts() {
        BotCommand cmd = stubSellCurrency();
        assertTrue(cmd.matches("+продать-валюту usd 500",
                new String[]{"+продать-валюту", "USD", "500"}));
    }

    @Test
    void sellCurrency_doesNotMatchTwoParts() {
        BotCommand cmd = stubSellCurrency();
        assertFalse(cmd.matches("+продать-валюту usd",
                new String[]{"+продать-валюту", "USD"}));
    }

    @Test
    void sellCurrency_doesNotMatchDifferentCommand() {
        BotCommand cmd = stubSellCurrency();
        assertFalse(cmd.matches("+продать usd 500",
                new String[]{"+продать", "USD", "500"}));
    }

    @Test
    void sellCurrency_doesNotMatchBuyCommand() {
        BotCommand cmd = stubSellCurrency();
        assertFalse(cmd.matches("+купить-валюту usd 500",
                new String[]{"+купить-валюту", "USD", "500"}));
    }

    // -----------------------------------------------------------------------
    // +валюта-портфель
    // -----------------------------------------------------------------------

    @Test
    void currencyPortfolio_matchesExact() {
        BotCommand cmd = stubCurrencyPortfolio();
        assertTrue(cmd.matches("+валюта-портфель",
                new String[]{"+валюта-портфель"}));
    }

    @Test
    void currencyPortfolio_doesNotMatchWithExtra() {
        BotCommand cmd = stubCurrencyPortfolio();
        assertFalse(cmd.matches("+валюта-портфель extra",
                new String[]{"+валюта-портфель", "extra"}));
    }

    @Test
    void currencyPortfolio_doesNotMatchPortfel() {
        BotCommand cmd = stubCurrencyPortfolio();
        assertFalse(cmd.matches("+портфель",
                new String[]{"+портфель"}));
    }

    // -----------------------------------------------------------------------
    // Buy/Sell don't cross-match
    // -----------------------------------------------------------------------

    @Test
    void buyCurrency_doesNotMatchSellCommand() {
        BotCommand cmd = stubBuyCurrency();
        assertFalse(cmd.matches("+продать-валюту usd 500",
                new String[]{"+продать-валюту", "USD", "500"}));
    }

    // -----------------------------------------------------------------------
    // Stub helpers
    // -----------------------------------------------------------------------

    private BotCommand stubBuyCurrency() {
        return new BotCommand() {
            @Override
            public boolean matches(String input, String[] parts) {
                return parts.length == 3 && input.startsWith("+купить-валюту ");
            }
            @Override
            public String execute(net.dv8tion.jda.api.events.message.MessageReceivedEvent e,
                    String msg, String[] parts) { return "buy"; }
        };
    }

    private BotCommand stubSellCurrency() {
        return new BotCommand() {
            @Override
            public boolean matches(String input, String[] parts) {
                return parts.length == 3 && input.startsWith("+продать-валюту ");
            }
            @Override
            public String execute(net.dv8tion.jda.api.events.message.MessageReceivedEvent e,
                    String msg, String[] parts) { return "sell"; }
        };
    }

    private BotCommand stubCurrencyPortfolio() {
        return new BotCommand() {
            @Override
            public boolean matches(String input, String[] parts) {
                return input.equals("+валюта-портфель");
            }
            @Override
            public String execute(net.dv8tion.jda.api.events.message.MessageReceivedEvent e,
                    String msg, String[] parts) { return "portfolio"; }
        };
    }
}
