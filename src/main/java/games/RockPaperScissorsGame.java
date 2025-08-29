package games;

import java.util.Random;

public class RockPaperScissorsGame implements Game {
    private final Random random = new Random();
    private final String[] choices = {"камень", "ножницы", "бумага"};

    @Override
    public String getName() {
        return "Камень ножницы бумага";
    }

    @Override
    public String getDescription() {
        return "Классическая игра камень-ножницы-бумага. Сыграйте против бота!";
    }

    @Override
    public String play(String... args) {
        if (args.length == 0) {
            return "Пожалуйста, выберите 'камень', 'ножницы' или 'бумага'. Пример: +таверна камень ножницы бумага камень";
        }
        
        String playerChoice = args[0].toLowerCase();
        
        if (!playerChoice.equals("камень") && !playerChoice.equals("ножницы") && !playerChoice.equals("бумага")) {
            return "Пожалуйста, выберите 'камень', 'ножницы' или 'бумага'.";
        }
        
        String botChoice = choices[random.nextInt(3)];
        String result = determineWinner(playerChoice, botChoice);
        
        StringBuilder response = new StringBuilder();
        response.append("✂️ Игра камень-ножницы-бумага!\n");
        response.append("Вы выбрали: ").append(playerChoice).append("\n");
        response.append("Бот выбрал: ").append(botChoice).append("\n");
        response.append(result);
        
        return response.toString();
    }
    
    private String determineWinner(String player, String bot) {
        if (player.equals(bot)) {
            return "🤝 Ничья!";
        }
        
        boolean playerWins = (player.equals("камень") && bot.equals("ножницы")) ||
                             (player.equals("ножницы") && bot.equals("бумага")) ||
                             (player.equals("бумага") && bot.equals("камень"));
        
        if (playerWins) {
            return "🎉 Поздравляем! Вы выиграли!";
        } else {
            return "😢 К сожалению, вы проиграли. Повезет в следующий раз!";
        }
    }
}