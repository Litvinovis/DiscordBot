package services;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.tinkoff.piapi.core.InvestApi;

public class HelpInfoService {
    private final InvestApi api;

    public HelpInfoService(InvestApi api) {
        this.api = api;
    }

    public String getHelpInfo() {
        return "Вас приветствует Stonks Bot!\n" +
        "Вы можете использовать следующие команды: \n\n" +
        "+акция (имя или часть имени компании) - информация о стоимости акции\n\n" +
        "+валюта (тикер валюты например USD) - информация о курсе валюты к рублю\n\n" +
        "+помощь - вывод информации о доступных командах\n\n" +
        "\nСоздатель бота - L4rover";
    }
}
