package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Current NPC combat target. When there is no NPC, the API returns -1 for each field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpcCombatDto {
    private int npcId = -1;
    private int npcCurrentHp = -1;
    private int npcMaxHp = -1;
    private int npcMaxHit = -1;
    private int npcCombatLevel = -1;
    private int npcAttackSpeed = -1;

    public int getNpcId() { return npcId; }
    public void setNpcId(int npcId) { this.npcId = npcId; }
    public int getNpcCurrentHp() { return npcCurrentHp; }
    public void setNpcCurrentHp(int npcCurrentHp) { this.npcCurrentHp = npcCurrentHp; }
    public int getNpcMaxHp() { return npcMaxHp; }
    public void setNpcMaxHp(int npcMaxHp) { this.npcMaxHp = npcMaxHp; }
    public int getNpcMaxHit() { return npcMaxHit; }
    public void setNpcMaxHit(int npcMaxHit) { this.npcMaxHit = npcMaxHit; }
    public int getNpcCombatLevel() { return npcCombatLevel; }
    public void setNpcCombatLevel(int npcCombatLevel) { this.npcCombatLevel = npcCombatLevel; }
    public int getNpcAttackSpeed() { return npcAttackSpeed; }
    public void setNpcAttackSpeed(int npcAttackSpeed) { this.npcAttackSpeed = npcAttackSpeed; }

    /** True when there is a valid NPC (e.g. npcMaxHp >= 0). */
    public boolean hasNpc() {
        return npcMaxHp >= 0;
    }
}
