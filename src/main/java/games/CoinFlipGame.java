package games;

import java.util.Random;

public class CoinFlipGame implements Game {
    private final Random random = new Random();
    private final String[] sides = {"Орел", "Решка"};

    @Override
    public String getName() {
        return "Монетка";
    }

    @Override
    public String getDescription() {
        return "Игра в монетку. Выберите сторону и проверьте свою удачу!";
    }

    @Override
    public String play(String... args) {
        String playerChoice = args.length > 0 ? args[0].toLowerCase() : "";
        
        if (!playerChoice.equals("орел") && !playerChoice.equals("решка")) {
            return "Пожалуйста, выберите 'орел' или 'решка'. Пример: +таверна монетка орел";
        }
        
        String resultSide = sides[random.nextInt(2)];
        boolean win = playerChoice.equals(resultSide.toLowerCase());
        
        StringBuilder result = new StringBuilder();
        result.append("🪙 Игра в монетку!\n");
        result.append("Вы выбрали: ").append(playerChoice).append("\n");
        result.append("Результат: ").append(resultSide).append("\n");
        
        if (win) {
            result.append("🎉 Поздравляем! Вы угадали!");
        } else {
            result.append("😢 К сожалению, вы не угадали. Повезет в следующий раз!");
        }
        
        return result.toString();
    }
}