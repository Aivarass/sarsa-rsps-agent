package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerDto {
    private int currentHp;
    private int maxHp;
    private int strLvl;
    private int attackLvl;
    private int defenceLvl;
    private int sessionXp;
    private int levelsIncreased;
    private int healedHp;

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getStrLvl() { return strLvl; }
    public void setStrLvl(int strLvl) { this.strLvl = strLvl; }
    public int getAttackLvl() { return attackLvl; }
    public void setAttackLvl(int attackLvl) { this.attackLvl = attackLvl; }
    public int getDefenceLvl() { return defenceLvl; }
    public void setDefenceLvl(int defenceLvl) { this.defenceLvl = defenceLvl; }
    public int getSessionXp() { return sessionXp; }
    public void setSessionXp(int sessionXp) { this.sessionXp = sessionXp; }
    public int getLevelsIncreased() { return levelsIncreased; }
    public void setLevelsIncreased(int levelsIncreased) { this.levelsIncreased = levelsIncreased; }
    public int getHealedHp() { return healedHp; }
    public void setHealedHp(int healedHp) { this.healedHp = healedHp; }
}
