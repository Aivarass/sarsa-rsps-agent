package sara.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Low-level HTTP client for the game REST API.
 * Centralizes base URL, player id, and request/response handling.
 */
public class GameApiClient {

    /** statusCode 0 means request failed (e.g. IO). */
    private record PostResult(int statusCode, String body) {}

    /** Result of a POST with status and raw body (error messages are in body on 4xx). */
    public record PostOutcome(int statusCode, String body) {}

    private static final String BASE_URL = "http://localhost:8081/api/game";
    private static final String PLAYER_ID = "Rl%20Agent";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GameApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * POST to the given path (relative to base URL), no body.
     * @return true if response status is 2xx
     */
    public boolean post(String path) {
        int status = sendPost(path).statusCode();
        return status >= 200 && status < 300;
    }

    /**
     * POST and return status code and raw body. Use this when you need to distinguish
     * success vs failure and read error messages (e.g. 400 body "Player is stunned").
     * statusCode 0 means request failed (e.g. IO).
     */
    public PostOutcome postWithStatus(String path) {
        PostResult r = sendPost(path);
        return new PostOutcome(r.statusCode(), r.body() != null ? r.body() : "");
    }

    /**
     * POST to the given path and parse response body into the given type.
     * @return Optional with parsed body on 2xx and non-empty body, empty otherwise
     */
    public <T> Optional<T> postForResponse(String path, Class<T> responseClass) {
        PostResult result = sendPost(path);
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            return Optional.empty();
        }
        String body = result.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(body, responseClass));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Parses JSON body into the given type; returns null on parse failure. */
    public <T> T parseBody(String body, Class<T> responseClass) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readValue(body, responseClass);
        } catch (Exception e) {
            return null;
        }
    }

    private PostResult sendPost(String path) {
        String url = BASE_URL + "/" + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new PostResult(response.statusCode(), response.body() != null ? response.body() : "");
        } catch (Exception e) {
            return new PostResult(0, "");
        }
    }

    /** Path for consume food: {@value}. */
    public static String pathConsumeFood() {
        return PLAYER_ID + "/food/consume";
    }

    /** Path for attack npc: {@value}. */
    public static String pathAttackNpc() {
        return PLAYER_ID + "/attack/npc";
    }

    /** Path for reset: {@value}. */
    public static String pathReset() {
        return PLAYER_ID + "/reset";
    }
}
