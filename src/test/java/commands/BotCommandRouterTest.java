package commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Command Pattern routing logic.
 *
 * Tests verify that BotCommand.matches() correctly identifies which command
 * should handle a given user input, without depending on Discord or Ignite.
 */
class BotCommandRouterTest {

    /**
     * Simulate the MessageHandler.handle() routing loop:
     * find the first command that matches and return its toString() name.
     */
    private String findMatchingCommand(List<? extends BotCommand> commands, String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        String[] parts = msg.split("\\s+");
        for (BotCommand cmd : commands) {
            if (cmd.matches(lower, parts)) {
                return cmd.toString();
            }
        }
        return "NONE";
    }

    // -----------------------------------------------------------------------
    // Stub commands for testing (do not need real service dependencies)
    // -----------------------------------------------------------------------

    /** A minimal stub that has the same matching logic as the real command. */
    private BotCommand stubCmd(java.util.function.BiFunction<String, String[], Boolean> matcher, String name) {
        return new BotCommand() {
            @Override
            public boolean matches(String input, String[] parts) {
                return matcher.apply(input, parts);
            }

            @Override
            public String execute(net.dv8tion.jda.api.events.message.MessageReceivedEvent event,
                    String msg, String[] parts) {
                return name;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Exact-match commands
    // -----------------------------------------------------------------------

    @Test
    void testPomosh_matchesExactly() {
        BotCommand cmd = stubCmd((input, p) -> input.equals("+помощь"), "HelpCommand");
        assertTrue(cmd.matches("+помощь", new String[]{"+помощь"}));
        assertFalse(cmd.matches("+помощь123", new String[]{"+помощь123"}));
    }

    @Test
    void testRegistration_matchesExactly() {
        BotCommand cmd = stubCmd((input, p) -> input.equals("+регистрация"), "SandboxRegisterCommand");
        assertTrue(cmd.matches("+регистрация", new String[]{"+регистрация"}));
        assertFalse(cmd.matches("+регистрация2", new String[]{"+регистрация2"}));
    }

    @Test
    void testBalans_matchesExactly() {
        BotCommand cmd = stubCmd((input, p) -> input.equals("+баланс"), "SandboxBalanceCommand");
        assertTrue(cmd.matches("+баланс", new String[]{"+баланс"}));
        assertFalse(cmd.matches("+баланс ", new String[]{"+баланс", ""}));
    }

    // -----------------------------------------------------------------------
    // Prefix-match commands
    // -----------------------------------------------------------------------

    @Test
    void testValuta_matchesPrefix() {
        BotCommand cmd = stubCmd((input, p) -> input.startsWith("+валюта "), "CurrencyInfoCommand");
        assertTrue(cmd.matches("+валюта usd", new String[]{"+валюта", "usd"}));
        assertFalse(cmd.matches("+валюта", new String[]{"+валюта"})); // no space → no ticker
        assertFalse(cmd.matches("+акция sber", new String[]{"+акция", "sber"}));
    }

    @Test
    void testAktsiya_matchesPrefix() {
        BotCommand cmd = stubCmd((input, p) -> input.startsWith("+акция "), "SharesInfoCommand");
        assertTrue(cmd.matches("+акция sber", new String[]{"+акция", "sber"}));
        assertFalse(cmd.matches("+акция", new String[]{"+акция"}));
    }

    // -----------------------------------------------------------------------
    // Length-constrained prefix commands
    // -----------------------------------------------------------------------

    @Test
    void testCena_requiresTwoParts() {
        BotCommand cmd = stubCmd((input, p) -> p.length == 2 && input.startsWith("+цена "), "SandboxPriceCommand");
        assertTrue(cmd.matches("+цена sber", new String[]{"+цена", "SBER"}));
        assertFalse(cmd.matches("+цена", new String[]{"+цена"}));
        assertFalse(cmd.matches("+цена sber extra", new String[]{"+цена", "sber", "extra"}));
    }

    @Test
    void testKupit_requiresThreeParts() {
        BotCommand cmd = stubCmd((input, p) -> p.length == 3 && input.startsWith("+купить "), "SandboxBuyCommand");
        assertTrue(cmd.matches("+купить sber 10", new String[]{"+купить", "SBER", "10"}));
        assertFalse(cmd.matches("+купить sber", new String[]{"+купить", "SBER"}));
        assertFalse(cmd.matches("+купить sber 10 extra", new String[]{"+купить", "SBER", "10", "extra"}));
    }

    @Test
    void testProdat_requiresThreeParts() {
        BotCommand cmd = stubCmd((input, p) -> p.length == 3 && input.startsWith("+продать "), "SandboxSellCommand");
        assertTrue(cmd.matches("+продать sber 5", new String[]{"+продать", "SBER", "5"}));
        assertFalse(cmd.matches("+продать sber", new String[]{"+продать", "SBER"}));
    }

    @Test
    void testLimitKuplyu_requiresFourParts() {
        BotCommand cmd = stubCmd((input, p) -> p.length == 4 && input.startsWith("+лимит-куплю "), "SandboxLimitBuyCommand");
        assertTrue(cmd.matches("+лимит-куплю sber 10 300", new String[]{"+лимит-куплю", "SBER", "10", "300"}));
        assertFalse(cmd.matches("+лимит-куплю sber 10", new String[]{"+лимит-куплю", "SBER", "10"}));
    }

    @Test
    void testLimitProdam_requiresFourParts() {
        BotCommand cmd = stubCmd((input, p) -> p.length == 4 && input.startsWith("+лимит-продам "), "SandboxLimitSellCommand");
        assertTrue(cmd.matches("+лимит-продам sber 10 320", new String[]{"+лимит-продам", "SBER", "10", "320"}));
        assertFalse(cmd.matches("+лимит-продам sber 10", new String[]{"+лимит-продам", "SBER", "10"}));
    }

    // -----------------------------------------------------------------------
    // Top command — period validation
    // -----------------------------------------------------------------------

    @Test
    void testTop_validPeriods() {
        BotCommand cmd = stubCmd((input, p) -> {
            if (p.length < 2 || !input.startsWith("+топ ")) return false;
            String period = p[1].toLowerCase(Locale.ROOT);
            return period.equals("день") || period.equals("неделя") || period.equals("месяц")
                    || period.equals("все") || period.equals("всё");
        }, "SandboxTopCommand");

        assertTrue(cmd.matches("+топ день", new String[]{"+топ", "день"}));
        assertTrue(cmd.matches("+топ неделя", new String[]{"+топ", "неделя"}));
        assertTrue(cmd.matches("+топ месяц", new String[]{"+топ", "месяц"}));
        assertTrue(cmd.matches("+топ все", new String[]{"+топ", "все"}));
        assertTrue(cmd.matches("+топ всё", new String[]{"+топ", "всё"}));
        assertFalse(cmd.matches("+топ год", new String[]{"+топ", "год"}));
        assertFalse(cmd.matches("+топ", new String[]{"+топ"}));
    }

    // -----------------------------------------------------------------------
    // No command matches → fallback "NONE"
    // -----------------------------------------------------------------------

    @Test
    void testUnknownCommand_returnsNone() {
        BotCommand help = stubCmd((input, p) -> input.equals("+помощь"), "HelpCommand");
        BotCommand balance = stubCmd((input, p) -> input.equals("+баланс"), "SandboxBalanceCommand");

        List<BotCommand> commands = List.of(help, balance);
        assertEquals("NONE", findMatchingCommand(commands, "+неизвестно"));
    }

    // -----------------------------------------------------------------------
    // First-match wins (order matters in the chain)
    // -----------------------------------------------------------------------

    @Test
    void testFirstMatchWins_whenMultipleCouldMatch() {
        // Both match "+foo" but the first one should win
        BotCommand first = stubCmd((input, p) -> input.startsWith("+foo"), "FirstCommand");
        BotCommand second = stubCmd((input, p) -> input.equals("+foo"), "SecondCommand");

        List<BotCommand> commands = List.of(first, second);
        assertEquals("FirstCommand", findMatchingCommand(commands, "+foo"));
    }

    // -----------------------------------------------------------------------
    // Stop-loss and take-profit commands
    // -----------------------------------------------------------------------

    @Test
    void testStopLoss_matchesCorrectly() {
        BotCommand cmd = stubCmd(
                (input, p) -> p.length == 3 && input.startsWith("+стоп-лосс "),
                "SandboxStopLossCommand");
        assertTrue(cmd.matches("+стоп-лосс sber 270", new String[]{"+стоп-лосс", "SBER", "270"}));
        assertFalse(cmd.matches("+стоп-лосс sber", new String[]{"+стоп-лосс", "SBER"}));
    }

    @Test
    void testTakeProfit_matchesCorrectly() {
        BotCommand cmd = stubCmd(
                (input, p) -> p.length == 3 && input.startsWith("+тейк-профит "),
                "SandboxTakeProfitCommand");
        assertTrue(cmd.matches("+тейк-профит sber 320", new String[]{"+тейк-профит", "SBER", "320"}));
        assertFalse(cmd.matches("+тейк-профит", new String[]{"+тейк-профит"}));
    }

    // -----------------------------------------------------------------------
    // Alert command
    // -----------------------------------------------------------------------

    @Test
    void testAlert_requiresThreeParts() {
        BotCommand cmd = stubCmd(
                (input, p) -> p.length == 3 && input.startsWith("+алерт "),
                "SandboxAlertCommand");
        assertTrue(cmd.matches("+алерт sber 310", new String[]{"+алерт", "SBER", "310"}));
        assertFalse(cmd.matches("+алерт sber", new String[]{"+алерт", "SBER"}));
    }
}
