package sara.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class State {

    public static int FEATURE_COUNT = 21;

    //Player
    private int currentHp;
    private int maxHp;
    private int strLvl;
    private int attackLvl;
    private int defenceLvl;
    private int sessionXp;
    private int levelsIncreased;

    //Combat
    private int npcKillCount;
    private int deathCount;
    private int inCombat;
    private int npcLastHit;
//    private int combatTicks;
//    private int eatTicks;
    private int canAttack;
    private int canEat;

    //Npc
    private int npcCurrentHp;
    private int npcMaxHp;
    private int npcMaxHit;
    private int npcCombatLevel;
    private int npcAttackSpeed;

    //Heal
    private int foodCountRemaining;
    private int healAmountRemaining;

    //Items
    private int itemOnGround;

    public State(int currentHp, int maxHp, int strLvl, int attackLvl, int defenceLvl, int sessionXp, int levelsIncreased, int npcKillCount, int deathCount, int inCombat, int npcLastHit, int combatTicks, int eatTicks, int canAttack, int canEat, int npcCurrentHp, int npcMaxHp, int npcMaxHit, int npcCombatLevel, int npcAttackSpeed, int foodCountRemaining, int healAmountRemaining, int itemOnGround) {
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.strLvl = strLvl;
        this.attackLvl = attackLvl;
        this.defenceLvl = defenceLvl;
        this.sessionXp = sessionXp;
        this.levelsIncreased = levelsIncreased;
        this.npcKillCount = npcKillCount;
        this.deathCount = deathCount;
        this.inCombat = inCombat;
        this.npcLastHit = npcLastHit;
//        this.combatTicks = combatTicks;
//        this.eatTicks = eatTicks;
        this.canAttack = canAttack;
        this.canEat = canEat;
        this.npcCurrentHp = npcCurrentHp;
        this.npcMaxHp = npcMaxHp;
        this.npcMaxHit = npcMaxHit;
        this.npcCombatLevel = npcCombatLevel;
        this.npcAttackSpeed = npcAttackSpeed;
        this.foodCountRemaining = foodCountRemaining;
        this.healAmountRemaining = healAmountRemaining;
        this.itemOnGround = itemOnGround;
    }

    public double[] scale() {
        // ---- Tunable caps (pick conservative defaults) ----
        final double LEVEL_CAP = 99.0;
        final double HP_CAP = 99.0;
        final double KILL_CAP = 10.0;
        final double DEATH_CAP = 10.0;
        final double LEVELS_INCREASED_CAP = 10.0;

        final double FOOD_CAP = 28.0;         // inventory

        final double BEST_FOOD_HEAL = 20.0;
        final double TOTAL_HEAL_CAP = FOOD_CAP * BEST_FOOD_HEAL;


        final double XP_CAP = 50_000.0;
        final double NPC_MAX_HIT_CAP = 20.0;
        final double NPC_ATTACK_SPEED_CAP = 10.0;
        final double COMBAT_TICKS_CAP = 6.0;
        final double EAT_TICKS_CAP = 5.0;

        double[] f = new double[FEATURE_COUNT];
        int i = 0;

        // helpers
        java.util.function.DoubleUnaryOperator clip01 = x -> x < 0 ? 0 : (x > 1 ? 1 : x);
        java.util.function.DoubleBinaryOperator safeDiv = (num, den) -> den <= 0 ? 0.0 : (num / den);
        java.util.function.DoubleBinaryOperator scaleByCap = (val, cap) -> {
            if (cap <= 0) return 0.0;
            double v = Math.max(0.0, Math.min(val, cap));
            return v / cap;
        };
        // -1 (no value) from API -> 0 in features
        java.util.function.DoubleBinaryOperator scaleOrZero = (val, cap) ->
            val < 0 ? 0.0 : scaleByCap.applyAsDouble(val, cap);
        java.util.function.DoubleBinaryOperator logScale = (val, cap) -> {
            if (cap <= 0) return 0.0;
            double v = Math.max(0.0, val);
            double c = Math.max(1.0, cap);
            return Math.log1p(v) / Math.log1p(c); // in [0,1] if v<=cap, but can exceed 1 if v>cap
        };

        // ---- Player ----
        double hpRatio = safeDiv.applyAsDouble(currentHp, maxHp);
        f[i++] = clip01.applyAsDouble(hpRatio);

        f[i++] = scaleByCap.applyAsDouble(maxHp, HP_CAP); // optional; gives agent context of absolute HP pool

        f[i++] = scaleByCap.applyAsDouble(strLvl, LEVEL_CAP);
        f[i++] = scaleByCap.applyAsDouble(attackLvl, LEVEL_CAP);
        f[i++] = scaleByCap.applyAsDouble(defenceLvl, LEVEL_CAP);

        f[i++] = clip01.applyAsDouble(logScale.applyAsDouble(sessionXp, XP_CAP)); // log-scaled

        f[i++] = scaleByCap.applyAsDouble(levelsIncreased, LEVELS_INCREASED_CAP);

        // ---- Combat ----
        f[i++] = scaleByCap.applyAsDouble(npcKillCount, KILL_CAP);
        f[i++] = scaleByCap.applyAsDouble(deathCount, DEATH_CAP);
        f[i++] = (inCombat != 0) ? 1.0 : 0.0;
        f[i++] = scaleOrZero.applyAsDouble(npcLastHit, HP_CAP);
//        f[i++] = scaleByCap.applyAsDouble(Math.max(0, combatTicks), COMBAT_TICKS_CAP);
//        f[i++] = scaleByCap.applyAsDouble(Math.max(0, eatTicks), EAT_TICKS_CAP);
        // API: 0 = can, 1 = can't. Scale to 1.0 = can act, 0.0 = can't (for ANN)
        f[i++] = (canAttack == 0) ? 1.0 : 0.0;
        f[i++] = (canEat == 0) ? 1.0 : 0.0;

        // ---- NPC (from npc object; -1 when no NPC) ----
        double npcHpRatio = safeDiv.applyAsDouble(npcCurrentHp, npcMaxHp);
        f[i++] = clip01.applyAsDouble(npcHpRatio);
        f[i++] = scaleByCap.applyAsDouble(Math.max(0, npcMaxHp), HP_CAP);
        f[i++] = scaleOrZero.applyAsDouble(npcMaxHit, NPC_MAX_HIT_CAP);
        f[i++] = scaleOrZero.applyAsDouble(npcCombatLevel, LEVEL_CAP);
        f[i++] = scaleOrZero.applyAsDouble(npcAttackSpeed, NPC_ATTACK_SPEED_CAP);

        // ---- Heal (inventory) ----
        f[i++] = scaleByCap.applyAsDouble(foodCountRemaining, FOOD_CAP);
        f[i++] = scaleByCap.applyAsDouble(healAmountRemaining, TOTAL_HEAL_CAP);

        // Items
        f[i++] = (itemOnGround == 0) ? 0 : 1.0;

        return f;
    }


    // Helpers (also used by tracking/logging)
    public int getCurrentHp() { return currentHp; }
    public int getMaxHp() { return maxHp; }
    public int getFoodCountRemaining() { return foodCountRemaining; }
    public int getNpcMaxHp() { return npcMaxHp; }
    public int getNpcKillCount() { return npcKillCount; }
    public int getDeathCount() { return deathCount; }
    public int getLevelsIncreased() { return levelsIncreased; }
    public int getSessionXp() { return sessionXp; }
    public int getAttackLvl() { return attackLvl; }
    public int getStrLvl() { return strLvl; }
    public int getDefenceLvl() { return defenceLvl; }
    public int getHealAmountRemaining() { return healAmountRemaining; }
    public int getInCombat() { return inCombat; }
    public int getCanEat() { return canEat; }
    public int getCanAttack() { return canAttack; }
    public int getItemOnGround() { return itemOnGround; }
}
