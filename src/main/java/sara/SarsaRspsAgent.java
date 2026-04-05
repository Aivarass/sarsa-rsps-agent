package sara;

import ann.TinyQNetwork;
import org.junit.jupiter.api.Test;
import sara.client.GameActionExecutor;
import sara.client.StateTransformer;
import sara.client.dto.AttackResult;
import sara.client.dto.ConsumeFoodResponse;
import sara.client.dto.ConsumeFoodResult;
import sara.model.Actions;
import sara.model.EpisodeStats;
import sara.model.State;
import sara.model.StateReward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

public class SarsaRspsAgent {

    private int EPISODE_COUNT = 5000;
    private long SEED = 1234L;
    private int LOG_EVERY = 10;

    //HYPER PARAMS
    private double EPSILON = 0.025;
    private double GAMMA = 0.99;
    private double ALPHA = 0.01;

    //BEST-MODEL CHECKPOINT
    private double bestAvgR = Double.NEGATIVE_INFINITY;
    private double[][] bestWInputHidden;
    private double[] bestBHidden;
    private double[][] bestWHiddenQ;
    private double[] bestBQ;
    private double RELOAD_THRESHOLD = 10.0; // reload if avgR drops below bestAvgR - threshold
    private int RELOAD_COOLDOWN = 5;        // skip N windows after reload before checking again
    private int cooldownRemaining = 0;

    //ANN
    private TinyQNetwork ann;
    private int ANN_INPUTS = State.FEATURE_COUNT;
    private int ANN_NEURONS = 16;
    private int ANN_ACTIONS = Actions.ACTION_COUNT;

    //GAME CAPS
    private int KILLS_CAP = 2;
    private int DEATHS_CAP = 2;

    //SL IMITATION
    private boolean USE_IMITATION = false;

    // State
    StateTransformer stateTransformer;

    //Actions
    GameActionExecutor executor;

    // Tracking environment
    int prevKills = 0;
    int prevDeaths = 0;

    /** Current episode: eat attempts that failed (API 4xx). */
    private int episodeEatInvalid = 0;
    /** Current episode: eat when already at full HP. */
    private int episodeEatAtFullHp = 0;
    /** Current episode: eat that overhealed (currentHp + healed > maxHp). */
    private int episodeEatOvereat = 0;
    /** Current episode: eat when HP < 5. */
    private int episodeEatAtLowHp = 0;
    /** Current episode: well-executed eat (not invalid, not atFullHp, not overeat). */
    private int episodeEatSuccess = 0;
    /** Current episode: attack attempts that succeeded (server accepted). */
    private int episodeAttackSuccess = 0;
    /** Set by executeAction when action was ATTACK; used to count success in episode loop. */
    private boolean lastAttackSuccess = false;


    @Test
    public void executeSarsa() throws InterruptedException, IOException {
        executor = new GameActionExecutor();
        ann = new TinyQNetwork(ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS);
        loadAnn("src/main/resources/ann/run/ann_20260319_151430_20in_16n_3out_avgR9.712_ep5000.json");
        if(USE_IMITATION) {
            if (Files.exists(Path.of(SarsaRspsAgentPlayer.PRETRAINED_ANN_PATH))) {
                System.out.println("Using imitation");
                loadAnn(SarsaRspsAgentPlayer.PRETRAINED_ANN_PATH);
            }
        }
        stateTransformer = new StateTransformer();
        executeEpisodes(SEED);
        saveAnn("src/main/resources/ann/full/");
    }

    public void executeEpisodes(long seed) throws InterruptedException, IOException {
        Random rng = new Random(seed);

        // Window accumulators (reset every LOG_EVERY)
        double rewardWin = 0.0;
        int xpWin = 0;
        int killsWin = 0, deathsWin = 0, lvlsWin = 0;
        long stepsWin = 0;
        int epWinsWin = 0, epLossesWin = 0;
        int eatCntWin = 0, eatInvWin = 0, eatAtFullHpWin = 0, eatOvereatWin = 0, eatAtLowHpWin = 0, eatSuccessWin = 0;
        int attackSuccessWin = 0;
        long foodLeftWin = 0;
        int[] actionCountsWin = new int[ANN_ACTIONS];

        for (int i = 0; i < EPISODE_COUNT; i++){
            EpisodeStats e = executeEpisode(rng);

            rewardWin += e.totalReward();
            xpWin += e.sessionXp();
            killsWin += e.kills();
            deathsWin += e.deaths();
            lvlsWin += e.levelsIncreased();
            stepsWin += e.steps();
            eatCntWin += e.eatCount();
            eatInvWin += e.eatInvalidCount();
            eatAtFullHpWin += e.eatAtFullHpCount();
            eatOvereatWin += e.eatOvereatCount();
            eatAtLowHpWin += e.eatAtLowHpCount();
            eatSuccessWin += e.eatSuccessCount();
            foodLeftWin += e.foodLeft();
            actionCountsWin[0] += e.waitCount();
            actionCountsWin[1] += e.attackCount();
            actionCountsWin[2] += e.eatCount();
            attackSuccessWin += e.attackSuccessCount();

            if (e.kills() >= KILLS_CAP) epWinsWin++;
            else if (e.deaths() >= DEATHS_CAP) epLossesWin++;

            if ((i + 1) % LOG_EVERY == 0) {
                int n = LOG_EVERY;
                double avgR = rewardWin / n;
                double avgKills = killsWin / (double) n;
                double avgDeaths = deathsWin / (double) n;
                double kdRatio = killsWin / (double) Math.max(1, deathsWin);
                double winRate = (killsWin + deathsWin) == 0 ? 0.0 : killsWin / (double) (killsWin + deathsWin);
                double avgXP = xpWin / (double) n;
                double avgLvls = lvlsWin / (double) n;
                double avgSteps = stepsWin / (double) n;
                double epWinRate = (epWinsWin + epLossesWin) == 0 ? 0.0 : epWinsWin / (double) (epWinsWin + epLossesWin);
                double avgFoodLeft = foodLeftWin / (double) n;
                double eatInvalidPct = eatCntWin == 0 ? 0.0 : 100.0 * eatInvWin / (double) eatCntWin;
                double eatAtFullHpPct = eatCntWin == 0 ? 0.0 : 100.0 * eatAtFullHpWin / (double) eatCntWin;
                double eatOvereatPct = eatCntWin == 0 ? 0.0 : 100.0 * eatOvereatWin / (double) eatCntWin;
                double eatAtLowHpPct = eatCntWin == 0 ? 0.0 : 100.0 * eatAtLowHpWin / (double) eatCntWin;
                double eatSuccessPct = eatCntWin == 0 ? 0.0 : 100.0 * eatSuccessWin / (double) eatCntWin;

                State last = e.finalState();
                System.out.printf(
                    "[Ep %6d] | avgR=%7.3f | K/D=%5.2f  (K=%.2f D=%.2f, win=%.2f) | avgXP=%8.1f | avgLvl+=%.2f | Atk=%d Str=%d Def=%d%n",
                    i + 1, avgR, kdRatio, avgKills, avgDeaths, winRate,
                    avgXP, avgLvls,
                    last != null ? last.getAttackLvl() : 0,
                    last != null ? last.getStrLvl() : 0,
                    last != null ? last.getDefenceLvl() : 0
                );
                System.out.printf("           steps=%5.1f | epWin=%.3f | eat=%.1f/ep (invalid=%4.1f%%, atFullHp=%4.1f%%, overeat=%4.1f%%, atLowHp=%4.1f%%, success=%4.1f%%) | foodLeft=%.2f%n",
                    avgSteps, epWinRate, eatCntWin / (double) n, eatInvalidPct, eatAtFullHpPct, eatOvereatPct, eatAtLowHpPct, eatSuccessPct, avgFoodLeft);
                int att = actionCountsWin[1];
                double attackOkPct = att == 0 ? 0.0 : 100.0 * attackSuccessWin / (double) att;
                System.out.printf("           actions: WAIT=%d ATTACK=%d (ok=%d, %.1f%%) EAT=%d%n",
                    actionCountsWin[0], att, attackSuccessWin, attackOkPct, actionCountsWin[2]);
                System.out.println();

                // Best-model checkpoint + automated reload
                if (avgR > bestAvgR) {
                    bestAvgR = avgR;
                    double[][][] snap = ann.snapshotWeights();
                    bestWInputHidden = snap[0];
                    bestBHidden = snap[1][0];
                    bestWHiddenQ = snap[2];
                    bestBQ = snap[3][0];
                    cooldownRemaining = 0;
                    System.out.printf("           *** NEW BEST avgR=%.3f — weights saved ***%n", bestAvgR);
                } else if (cooldownRemaining > 0) {
                    cooldownRemaining--;
                } else if (bestWInputHidden != null && avgR < bestAvgR - RELOAD_THRESHOLD) {
                    double[][][] snapshot = { bestWInputHidden, { bestBHidden }, bestWHiddenQ, { bestBQ } };
                    ann.restoreWeights(snapshot);
                    cooldownRemaining = RELOAD_COOLDOWN;
                    System.out.printf("           <<< RELOAD best weights (bestAvgR=%.3f, current=%.3f) — cooldown %d windows >>>%n", bestAvgR, avgR, RELOAD_COOLDOWN);
                }

                rewardWin = 0.0; xpWin = 0; killsWin = 0; deathsWin = 0; lvlsWin = 0;
                stepsWin = 0; epWinsWin = 0; epLossesWin = 0;
                eatCntWin = 0; eatInvWin = 0; eatAtFullHpWin = 0; eatOvereatWin = 0; eatAtLowHpWin = 0; eatSuccessWin = 0; attackSuccessWin = 0; foodLeftWin = 0;
                Arrays.fill(actionCountsWin, 0);
                saveAnn("src/main/resources/ann/run", avgR, i + 1);
            }
        }
    }

    public EpisodeStats executeEpisode(Random rng) throws InterruptedException {
        prevDeaths = 0;
        prevKills = 0;
        episodeEatInvalid = 0;
        episodeEatAtFullHp = 0;
        episodeEatOvereat = 0;
        episodeEatAtLowHp = 0;
        episodeEatSuccess = 0;
        episodeAttackSuccess = 0;
        State currentState = initState();
        Thread.sleep(500);
        int currentAction = getAction(currentState, rng);
        double totalReward = 0;
        long steps = 0;
        int waitCount = 0, attackCount = 0, eatCount = 0;

        while(prevKills < KILLS_CAP && prevDeaths < DEATHS_CAP){
            steps++;
            if (currentAction == 0) waitCount++;
            else if (currentAction == 1) attackCount++;
            if (currentAction == 2) {
                eatCount++;
                if (currentState.getCurrentHp() < 5) episodeEatAtLowHp++;
            }

            StateReward sr = executeAction(currentAction);

            State nextState = sr.getState();
            if (currentAction == 1 && lastAttackSuccess) {
                episodeAttackSuccess++;
            }
            // Inventory-based "eat happened": stable buckets (invalid / atFullHp / overeat / success)
            if (currentAction == 2) {
                int foodBefore = currentState.getFoodCountRemaining();
                int foodAfter = nextState.getFoodCountRemaining();
                boolean eatConsumed = foodAfter < foodBefore;

                if (!eatConsumed) {
                    episodeEatInvalid++;
                } else {
                    int nominalHealPerFood = foodBefore > 0 ? currentState.getHealAmountRemaining() / foodBefore : 0;
                    boolean atFullHp = currentState.getCurrentHp() >= currentState.getMaxHp();
                    boolean overeat = currentState.getCurrentHp() < currentState.getMaxHp()
                        && nominalHealPerFood > 0
                        && currentState.getCurrentHp() + nominalHealPerFood > currentState.getMaxHp();

                    if (atFullHp) episodeEatAtFullHp++;
                    if (overeat) episodeEatOvereat++;
                    if (!atFullHp && !overeat) episodeEatSuccess++;
                }
            }

            totalReward += sr.getReward();
            boolean terminal = (prevKills >= KILLS_CAP || prevDeaths >= DEATHS_CAP);

            if (terminal) {
                ann.sarsaUpdate(currentState.scale(), currentAction, sr.getReward(), nextState.scale(), 0, true, ALPHA, GAMMA);
                break;
            }

            int nextAction = getAction(nextState, rng);
            ann.sarsaUpdate(currentState.scale(), currentAction, sr.getReward(), nextState.scale(), nextAction, false, ALPHA, GAMMA);

            currentState = nextState;
            currentAction = nextAction;
        }

        State finalState = currentState;
        return new EpisodeStats(
            totalReward, steps, prevKills, prevDeaths,
            finalState.getLevelsIncreased(),
            finalState.getSessionXp(),
            eatCount, episodeEatInvalid, episodeEatAtFullHp, episodeEatOvereat, episodeEatAtLowHp, episodeEatSuccess, waitCount, attackCount, episodeAttackSuccess,
            finalState
        );
    }

    public StateReward executeAction(int action) throws InterruptedException {
        double reward = 0;
        State newState;
        switch (action){
            case 0 :
                lastAttackSuccess = false;
                newState = stateTransformer.fetchCurrentState();
                if (newState.getInCombat() == 1 && newState.getCanEat() == 0 && newState.getCanAttack() == 0) {
                    reward -= 0.01;
                }
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
            case 1 :
                AttackResult attackResult = executor.attackNpc();
                lastAttackSuccess = attackResult.isSuccess();
                newState = stateTransformer.fetchCurrentState();
                if(newState.getCanAttack() == 1){
                    reward -= 0.01;
                }
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
            case 2: {
                lastAttackSuccess = false;
                newState = stateTransformer.fetchCurrentState();
                if (newState.getMaxHp() == newState.getCurrentHp()) {
                    reward -= 0.3;
                } else {
                    int eatFoodCount = newState.getFoodCountRemaining();
                    int eatHealPerFood = eatFoodCount > 0 ? newState.getHealAmountRemaining() / eatFoodCount : 0;
                    if (eatHealPerFood > 0 && newState.getCurrentHp() + eatHealPerFood > newState.getMaxHp()) {
                        reward -= 0.1;
                    }
                }
                ConsumeFoodResult response = executor.consumeFood();
                if (!response.isSuccess()) {
//                    reward -= 0.02;
                }
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
            }
            default:
                lastAttackSuccess = false;
                Thread.sleep(600);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
        }
        return new StateReward(reward, newState);
    }

    public double calculateReward(State state){
        double reward = 0;
        reward += (state.getNpcKillCount() - prevKills) * 5.0;
        reward -= (state.getDeathCount() - prevDeaths) * 5.0;

        //updates
        prevKills = state.getNpcKillCount();
        prevDeaths = state.getDeathCount();
        return reward;
    }

    public int getAction(State state, Random rng){
        boolean[] mask = actionMask(state);
        return ann.epsilonGreedyMasked(state.scale(), EPSILON, mask, rng);
    }

    private boolean[] actionMask(State state) {
        boolean[] mask = new boolean[ANN_ACTIONS];
        mask[0] = true;
        mask[1] = true;
        mask[2] = state.getFoodCountRemaining() > 0 && state.getCanEat() == 0;
        return mask;
    }

    private State initState() {
        executor.reset();
        return stateTransformer.fetchCurrentState();
    }

    /** Saves ANN weights to JSON at the given path (basic filename, no avgR). */
    public void saveAnn(String filePath) throws IOException {
        if (ann == null) return;
        ann.exportToJson(filePath);
    }

    /** Saves ANN weights to JSON under {@code directory} with suggested filename that includes avgR (e.g. …_avgR1.23.json). */
    public void saveAnn(String directory, double avgR) throws IOException {
        if (ann == null) return;
        String path = directory.endsWith("/") || directory.endsWith("\\") ? directory + ann.suggestFilename(avgR) : directory + "/" + ann.suggestFilename(avgR);
        ann.exportToJson(path);
    }

    /** Saves ANN weights to JSON under {@code directory} with suggested filename that includes avgR and episode count. */
    public void saveAnn(String directory, double avgR, int episode) throws IOException {
        if (ann == null) return;
        String filename = ann.suggestFilename(avgR, episode);
        String path = directory.endsWith("/") || directory.endsWith("\\") ? directory + filename : directory + "/" + filename;
        ann.exportToJson(path);
    }

    /**
     * Loads ANN weights from JSON. Creates ann with current ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS
     * then fills weights. Throws if file dimensions do not match (inputs, neurons, outputs).
     */
    public void loadAnn(String filePath) throws IOException {
        ann = new TinyQNetwork(ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS);
        ann.loadWeightsFromJson(filePath);
    }
}
