package sara.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import sara.client.dto.CombatDto;
import sara.client.dto.GameStateResponse;
import sara.client.dto.GroundItemsDto;
import sara.client.dto.InventoryDto;
import sara.client.dto.NpcCombatDto;
import sara.client.dto.PlayerDto;
import sara.model.State;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Transforms REST game state JSON into a {@link State}.
 * Can fetch from the game API or parse a JSON string.
 */
public class StateTransformer {

    private static final String STATE_URL = "http://localhost:8081/api/game/state/Rl%20Agent";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StateTransformer() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Fetches current game state from the REST API and converts it to {@link State}.
     * Safe to call repeatedly as the game state changes.
     *
     * @return current state, or null if fetch/parse failed
     */
    public State fetchCurrentState() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STATE_URL))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return fromJson(response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Parses a game state JSON string and converts it to {@link State}.
     * Use this when you already have the JSON (e.g. from another source).
     *
     * @param json REST response body matching the game state schema
     * @return state, or null if parse failed
     */
    public State fromJson(String json) {
        try {
            GameStateResponse response = objectMapper.readValue(json, GameStateResponse.class);
            return toState(response);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Converts a deserialized {@link GameStateResponse} to {@link State}.
     */
    public State toState(GameStateResponse response) {
        if (response == null) {
            return null;
        }

        PlayerDto player = response.getPlayer();
        CombatDto combat = response.getCombat();
        InventoryDto inventory = response.getInventory();

        int currentHp = player != null ? player.getCurrentHp() : 0;
        int maxHp = player != null ? player.getMaxHp() : 1;
        int strLvl = player != null ? player.getStrLvl() : 0;
        int attackLvl = player != null ? player.getAttackLvl() : 0;
        int defenceLvl = player != null ? player.getDefenceLvl() : 0;
        int sessionXp = player != null ? player.getSessionXp() : 0;
        int levelsIncreased = player != null ? player.getLevelsIncreased() : 0;

        int npcKillCount = combat != null ? combat.getNpcKillCount() : 0;
        int deathCount = combat != null ? combat.getDeathCount() : 0;
        int inCombat = (combat != null && combat.isInCombat()) ? 1 : 0;
        int npcLastHit = combat != null ? combat.getNpcLastHit() : -1;
        int combatTicks = combat != null ? Math.max(0, combat.getCombatTicks()) : 0;
        int eatTicks = combat != null ? Math.max(0, combat.getEatTicks()) : 0;
        // API: 0 = can, 1 = can't. Default 0 (can) when no combat block
        int canAttack = combat != null ? (combat.getCanAttack() != 0 ? 1 : 0) : 0;
        int canEat = combat != null ? (combat.getCanEat() != 0 ? 1 : 0) : 0;

        int foodCountRemaining = inventory != null ? inventory.getFoodCountRemaining() : 0;
        int healAmountRemaining = inventory != null ? inventory.getHealAmountRemaining() : 0;

        // Use npc (npcCombat) object; when no NPC, API returns -1 for each field
        NpcCombatDto npc = response.getNpc();
        int npcCurrentHp = (npc != null && npc.hasNpc()) ? npc.getNpcCurrentHp() : 0;
        int npcMaxHp = (npc != null && npc.hasNpc()) ? npc.getNpcMaxHp() : 0;
        int npcMaxHit = (npc != null && npc.hasNpc()) ? npc.getNpcMaxHit() : -1;
        int npcCombatLevel = (npc != null && npc.hasNpc()) ? npc.getNpcCombatLevel() : -1;
        int npcAttackSpeed = (npc != null && npc.hasNpc()) ? npc.getNpcAttackSpeed() : -1;

        GroundItemsDto groundItems = response.getGroundItems();
        int itemOnGround = groundItems != null ? groundItems.getItemOnGround() : 0;

        return new State(
                currentHp, maxHp, strLvl, attackLvl, defenceLvl,
                sessionXp, levelsIncreased,
                npcKillCount, deathCount, inCombat, npcLastHit, combatTicks, eatTicks, canAttack, canEat,
                npcCurrentHp, npcMaxHp, npcMaxHit, npcCombatLevel, npcAttackSpeed,
                foodCountRemaining, healAmountRemaining, itemOnGround
        );
    }
}
