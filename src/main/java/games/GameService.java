package games;

import java.util.HashMap;
import java.util.Map;

public class GameService {
    private final Map<String, Game> games = new HashMap<>();

    public GameService() {
        // Register all tavern games here
        registerGame(new DiceGame());
        registerGame(new CoinFlipGame());
        registerGame(new NumberGuessingGame());
        registerGame(new RockPaperScissorsGame());
    }

    private void registerGame(Game game) {
        games.put(game.getName().toLowerCase(), game);
    }

    public String listGames() {
        StringBuilder sb = new StringBuilder();
        sb.append("Доступные таверные игры:\n");
        for (Game game : games.values()) {
            sb.append("- ").append(game.getName()).append(": ").append(game.getDescription()).append("\n");
        }
        return sb.toString();
    }

    public String playGame(String gameName, String... args) {
        Game game = games.get(gameName.toLowerCase());
        if (game == null) {
            // Try to find a game with a partial match
            for (Map.Entry<String, Game> entry : games.entrySet()) {
                if (entry.getKey().contains(gameName.toLowerCase()) || gameName.toLowerCase().contains(entry.getKey())) {
                    game = entry.getValue();
                    break;
                }
            }
            
            if (game == null) {
                return "Игра '" + gameName + "' не найдена. Используйте '+таверна список' для просмотра доступных игр.";
            }
        }
        return game.play(args);
    }
}