/*
 * Decompiled with CFR 0.152.
 */
package services;

import org.springframework.stereotype.Service;

import services.tbank.TInvestApi;

/**
 * Сервис формирования справочного текста для команды «+помощь».
 *
 * <p>Возвращает статический список доступных команд бота.
 */
@Service
public class HelpInfoService {
	private final TInvestApi api;

	/**
	 * Создаёт сервис справки.
	 *
	 * @param api клиент T-Invest API (зарезервирован для будущего расширения)
	 */
	public HelpInfoService(TInvestApi api) {
		this.api = api;
	}

	/**
	 * Возвращает текст справки со списком всех доступных команд бота.
	 *
	 * @return строка со справочной информацией
	 */
	public String getHelpInfo() {
		return """
				Вас приветствует Stonks Bot!
				Базовые команды:
				+акция <тикер/название>
				+валюта <тикер>
				+помощь

				Песочница:
				+регистрация
				+активы
				+цена <TICKER>
				+купить <TICKER> <QTY>
				+продать <TICKER> <QTY>
				+портфель
				+баланс
				+маржа
				+топ день|неделя|месяц|все
				+история [СТРАНИЦА]

				DCA (автопокупки):
				+дка <TICKER> <СУММА_РУБ> [еженедельно|ежемесячно]
				+дка-список
				+дка-стоп <TICKER>""";
	}
}

