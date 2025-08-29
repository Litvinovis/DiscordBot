package games;

import java.util.Random;

public class NumberGuessingGame implements Game {
    private final Random random = new Random();

    @Override
    public String getName() {
        return "Угадай число";
    }

    @Override
    public String getDescription() {
        return "Угадайте число от 1 до 10. Проверьте свою интуицию!";
    }

    @Override
    public String play(String... args) {
        if (args.length == 0) {
            return "Пожалуйста, укажите число от 1 до 10. Пример: +таверна угадай число 5";
        }
        
        int secretNumber = random.nextInt(10) + 1;
        int playerGuess;
        
        try {
            playerGuess = Integer.parseInt(args[0]);
            if (playerGuess < 1 || playerGuess > 10) {
                return "Пожалуйста, выберите число от 1 до 10.";
            }
        } catch (NumberFormatException e) {
            return "Пожалуйста, введите корректное число от 1 до 10.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("🔢 Угадай число!\n");
        result.append("Вы выбрали: ").append(playerGuess).append("\n");
        result.append("Загаданное число: ").append(secretNumber).append("\n");
        
        if (playerGuess == secretNumber) {
            result.append("🎉 Поздравляем! Вы угадали число! Везунчик!");
        } else {
            result.append("😢 К сожалению, вы не угадали. Повезет в следующий раз!");
        }
        
        return result.toString();
    }
}