package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameStateResponse {
    private PlayerDto player;
    private CombatDto combat;
    private InventoryDto inventory;
    /** Current NPC combat target; fields are -1 when no NPC. */
    private NpcCombatDto npc;
    private List<NearbyNpcDto> nearbyNpcs;

    public PlayerDto getPlayer() { return player; }
    public void setPlayer(PlayerDto player) { this.player = player; }
    public CombatDto getCombat() { return combat; }
    public void setCombat(CombatDto combat) { this.combat = combat; }
    public InventoryDto getInventory() { return inventory; }
    public void setInventory(InventoryDto inventory) { this.inventory = inventory; }
    public NpcCombatDto getNpc() { return npc; }
    public void setNpc(NpcCombatDto npc) { this.npc = npc; }
    public List<NearbyNpcDto> getNearbyNpcs() {
        return nearbyNpcs != null ? nearbyNpcs : Collections.emptyList();
    }
    public void setNearbyNpcs(List<NearbyNpcDto> nearbyNpcs) { this.nearbyNpcs = nearbyNpcs; }
}
