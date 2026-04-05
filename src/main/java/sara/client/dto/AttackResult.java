package sara.client.dto;

import java.util.Optional;

/**
 * Result of an attack-NPC request. Use {@link #isSuccess()} to apply reward;
 * on failure, {@link #getErrorMessage()} contains the server message (e.g. "No attackable NPC ... found nearby").
 */
public final class AttackResult {

    private final boolean success;
    private final String errorMessage;

    private AttackResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static AttackResult success() {
        return new AttackResult(true, null);
    }

    public static AttackResult failure(String errorMessage) {
        return new AttackResult(false, errorMessage != null ? errorMessage : "Unknown error");
    }

    public boolean isSuccess() {
        return success;
    }

    /** Present only when {@link #isSuccess()} is false. */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
