package services.sandbox.api;

/**
 * Interface for sandbox rating operations: leaderboard, personal ranking, statistics.
 */
public interface ISandboxRatingService {

    String top(String period);

    String myRank(String userId);

    String stats(String userId);
}
