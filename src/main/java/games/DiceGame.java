package games;

import java.util.Random;

public class DiceGame implements Game {
    private final Random random = new Random();

    @Override
    public String getName() {
        return "Кости";
    }

    @Override
    public String getDescription() {
        return "Игра в кости. Бросьте кости и проверьте свою удачу!";
    }

    @Override
    public String play(String... args) {
        int playerRoll = random.nextInt(6) + 1;
        int botRoll = random.nextInt(6) + 1;
        
        StringBuilder result = new StringBuilder();
        result.append("🎲 Игра в кости!\n");
        result.append("Ваш бросок: ").append(playerRoll).append("\n");
        result.append("Бросок бота: ").append(botRoll).append("\n");
        
        if (playerRoll > botRoll) {
            result.append("🎉 Поздравляем! Вы выиграли!");
        } else if (playerRoll < botRoll) {
            result.append("😢 К сожалению, вы проиграли. Повезет в следующий раз!");
        } else {
            result.append("🤝 Ничья! Попробуйте еще раз!");
        }
        
        return result.toString();
    }
}