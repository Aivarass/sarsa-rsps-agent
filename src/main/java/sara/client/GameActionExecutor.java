package sara.client;

import sara.client.dto.AttackResult;
import sara.client.dto.ConsumeFoodResponse;
import sara.client.dto.ConsumeFoodResult;

/**
 * Executes game actions via the REST API.
 * Use this from the agent or any code that needs to perform in-game actions.
 * Use {@link ConsumeFoodResult#isSuccess()} and {@link AttackResult#isSuccess()} to apply negative reward on failure.
 */
public class GameActionExecutor {

    private final GameApiClient client;

    public GameActionExecutor() {
        this.client = new GameApiClient();
    }

    public GameActionExecutor(GameApiClient client) {
        this.client = client;
    }

    /**
     * Consume one food item. Call whenever the agent chooses the "eat food" action.
     *
     * @return result with success/failure; on failure use {@link ConsumeFoodResult#getErrorMessage()} for server message
     *         (e.g. "Player is stunned", "Item not in inventory", "Could not consume food")
     */
    public ConsumeFoodResult consumeFood() {
        GameApiClient.PostOutcome outcome = client.postWithStatus(GameApiClient.pathConsumeFood());
        if (outcome.statusCode() >= 200 && outcome.statusCode() < 300) {
            ConsumeFoodResponse response = client.parseBody(outcome.body(), ConsumeFoodResponse.class);
            return response != null ? ConsumeFoodResult.success(response) : ConsumeFoodResult.failure("Invalid response");
        }
        return ConsumeFoodResult.failure(outcome.body());
    }

    /**
     * Attack the current NPC target. Call when the agent chooses the "attack" action.
     *
     * @return result with success/failure; on failure use {@link AttackResult#getErrorMessage()}
     *         (e.g. "No attackable NPC with ID ... found nearby")
     */
    public AttackResult attackNpc() {
        GameApiClient.PostOutcome outcome = client.postWithStatus(GameApiClient.pathAttackNpc());
        if (outcome.statusCode() >= 200 && outcome.statusCode() < 300) {
            return AttackResult.success();
        }
        return AttackResult.failure(outcome.body());
    }

    /**
     * Reset game state (e.g. new episode). Mostly for internal use.
     *
     * @return true if the server returned 2xx
     */
    public boolean reset() {
        return client.post(GameApiClient.pathReset());
    }
}
