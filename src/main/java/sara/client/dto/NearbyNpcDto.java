package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NearbyNpcDto {
    private int id;
    private String name;
    private int distanceFromPlayer;
    private int npcCurrentHitpoints;
    private int npcMaxHitpoints;
    private int npcCombatLevel;
    private int npcAttackSpeed;
    private int npcMaxHit;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDistanceFromPlayer() { return distanceFromPlayer; }
    public void setDistanceFromPlayer(int distanceFromPlayer) { this.distanceFromPlayer = distanceFromPlayer; }
    public int getNpcCurrentHitpoints() { return npcCurrentHitpoints; }
    public void setNpcCurrentHitpoints(int npcCurrentHitpoints) { this.npcCurrentHitpoints = npcCurrentHitpoints; }
    public int getNpcMaxHitpoints() { return npcMaxHitpoints; }
    public void setNpcMaxHitpoints(int npcMaxHitpoints) { this.npcMaxHitpoints = npcMaxHitpoints; }
    public int getNpcCombatLevel() { return npcCombatLevel; }
    public void setNpcCombatLevel(int npcCombatLevel) { this.npcCombatLevel = npcCombatLevel; }
    public int getNpcAttackSpeed() { return npcAttackSpeed; }
    public void setNpcAttackSpeed(int npcAttackSpeed) { this.npcAttackSpeed = npcAttackSpeed; }
    public int getNpcMaxHit() { return npcMaxHit; }
    public void setNpcMaxHit(int npcMaxHit) { this.npcMaxHit = npcMaxHit; }
}
