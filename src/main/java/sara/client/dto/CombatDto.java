package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CombatDto {
    private int npcKillCount;
    private int deathCount;
    private boolean inCombat;
    private String fightStyle;
    /** Last hit dealt by NPC to player; -1 if none yet. */
    private int npcLastHit = -1;
    /** Ticks until next attack (4→3→2→1→0); 0 = ready. May vary by weapon. */
    private int combatTicks = 0;
    /** Ticks until next eat (3→2→1→0); 0 = ready. May vary by food. */
    private int eatTicks = 0;
    /** 0 = can attack now, 1 = can't (e.g. on cooldown). Default 0. */
    private int canAttack = 0;
    /** 0 = can eat now, 1 = can't (e.g. on cooldown). Default 0. */
    private int canEat = 0;

    public int getNpcKillCount() { return npcKillCount; }
    public void setNpcKillCount(int npcKillCount) { this.npcKillCount = npcKillCount; }
    public int getDeathCount() { return deathCount; }
    public void setDeathCount(int deathCount) { this.deathCount = deathCount; }
    public boolean isInCombat() { return inCombat; }
    public void setInCombat(boolean inCombat) { this.inCombat = inCombat; }
    public String getFightStyle() { return fightStyle; }
    public void setFightStyle(String fightStyle) { this.fightStyle = fightStyle; }
    public int getNpcLastHit() { return npcLastHit; }
    public void setNpcLastHit(int npcLastHit) { this.npcLastHit = npcLastHit; }
    public int getCombatTicks() { return combatTicks; }
    public void setCombatTicks(int combatTicks) { this.combatTicks = combatTicks; }
    public int getEatTicks() { return eatTicks; }
    public void setEatTicks(int eatTicks) { this.eatTicks = eatTicks; }
    public int getCanAttack() { return canAttack; }
    public void setCanAttack(int canAttack) { this.canAttack = canAttack; }
    public int getCanEat() { return canEat; }
    public void setCanEat(int canEat) { this.canEat = canEat; }
}
