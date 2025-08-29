package games;

public interface Game {
    String getName();
    String getDescription();
    String play(String... args);
}