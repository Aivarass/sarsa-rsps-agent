package sara.model;

/**
 * Stats for one episode, used for windowed logging.
 */
public record EpisodeStats(
    double totalReward,
    long steps,
    int kills,
    int deaths,
    int levelsIncreased,
    int sessionXp,
    int eatCount,
    int eatInvalidCount,
    int eatAtFullHpCount,
    int eatOvereatCount,
    int eatAtLowHpCount,
    int eatSuccessCount,
    int waitCount,
    int attackCount,
    int attackSuccessCount,
    int pickupCount,
    State finalState
) {
    public int foodLeft() {
        return finalState != null ? finalState.getFoodCountRemaining() : 0;
    }
}
