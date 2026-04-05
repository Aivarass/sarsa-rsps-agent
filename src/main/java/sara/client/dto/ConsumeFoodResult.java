package sara.client.dto;

import java.util.Optional;

/**
 * Result of a consume-food request. Use {@link #isSuccess()} to apply reward;
 * on failure, {@link #getErrorMessage()} contains the server message (e.g. "Player is stunned").
 */
public final class ConsumeFoodResult {

    private final boolean success;
    private final ConsumeFoodResponse response;
    private final String errorMessage;

    private ConsumeFoodResult(boolean success, ConsumeFoodResponse response, String errorMessage) {
        this.success = success;
        this.response = response;
        this.errorMessage = errorMessage;
    }

    public static ConsumeFoodResult success(ConsumeFoodResponse response) {
        return new ConsumeFoodResult(true, response, null);
    }

    public static ConsumeFoodResult failure(String errorMessage) {
        return new ConsumeFoodResult(false, null, errorMessage != null ? errorMessage : "Unknown error");
    }

    public boolean isSuccess() {
        return success;
    }

    /** Present only when {@link #isSuccess()} is true. */
    public Optional<ConsumeFoodResponse> getResponse() {
        return Optional.ofNullable(response);
    }

    /** Present only when {@link #isSuccess()} is false (e.g. "Player is stunned", "Item not in inventory"). */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
