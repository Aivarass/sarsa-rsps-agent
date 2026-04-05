package sara;

import ann.TinyQNetwork;
import org.junit.jupiter.api.Test;
import sara.client.GameActionExecutor;
import sara.client.StateTransformer;
import sara.client.dto.AttackResult;
import sara.client.dto.ConsumeFoodResult;
import sara.model.Actions;
import sara.model.EpisodeStats;
import sara.model.State;
import sara.model.StateReward;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class SarsaRspsAgentPlayer {

    private int EPISODE_COUNT = 5000;
    private long SEED = 1234L;
    private int LOG_EVERY = 10;

    //HYPER PARAMS (match SarsaRspsAgent for consistency)
    private double EPSILON = 0.05;
    private double GAMMA = 1.0;
    private double ALPHA = 0.01;

    //ANN
    private TinyQNetwork ann;
    private int ANN_INPUTS = State.FEATURE_COUNT;
    private int ANN_NEURONS = 16;
    private int ANN_ACTIONS = Actions.ACTION_COUNT;

    //GAME CAPS
    private int KILLS_CAP = 2;
    private int DEATHS_CAP = 2;

    public static boolean IMITATION = true;

    public double IMITATION_LEARN_RATE = 0.05;

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
    private int episodeAttackSuccess = 0;
    private boolean lastAttackSuccess = false;

    private static final int CONSOLE_END_AND_EXPORT = 4;
    /** Directory for behaviour-cloning state dumps: {@code src/main/resources/imitation/state/}. */
    private static final String IMITATION_STATE_DIR = "src/main/resources/imitation/state/";
    /** Path to export BC-trained ANN; load this in SarsaRspsAgent to start RL from pretrained. */
    public static final String PRETRAINED_ANN_PATH = "src/main/resources/ann/imitation/pretrained.json";
    private final Scanner console = new Scanner(System.in);

    /** When true, run in imitation mode (dump state+action to JSON for behaviour cloning). Set before calling executeEpisodes. */
    private boolean imitationMode = false;

    public boolean isImitationMode() { return imitationMode; }
    public void setImitationMode(boolean imitationMode) { this.imitationMode = imitationMode; }

    /** Run this as an application (not test). Set imitationMode or pass arg "imitation" to dump state for BC. */
    public static void main(String[] args) throws InterruptedException, IOException {
        SarsaRspsAgentPlayer player = new SarsaRspsAgentPlayer();
        player.executor = new GameActionExecutor();
        player.ann = new TinyQNetwork(player.ANN_INPUTS, player.ANN_NEURONS, player.ANN_ACTIONS);
        player.stateTransformer = new StateTransformer();
        if (IMITATION) {
            player.setImitationMode(true);
        }
        if (player.isImitationMode()) {
            System.out.println("Running in imitation mode");
            player.executeEpisodesImitation();
        } else {
            System.out.println("Running in player mode");
            player.executeEpisodes();
        }
    }

    @Test
    public void executePlayer() throws InterruptedException, IOException {
        executor = new GameActionExecutor();
        ann = new TinyQNetwork(ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS);
        stateTransformer = new StateTransformer();
        executeEpisodes();
    }


    /**
     * Imitation mode: run episodes with console input and dump (state, action) to
     * {@value #IMITATION_STATE_DIR} as JSON for behaviour cloning. One file per episode: episode_001.json etc.
     */
    public void executeEpisodesImitation() throws InterruptedException, IOException {
        Path dir = Path.of(IMITATION_STATE_DIR);
        Files.createDirectories(dir);
        System.out.println("Imitation mode: state dump to " + dir.toAbsolutePath());
        System.out.println("Play as usual (1=WAIT 2=ATTACK 3=EAT 4=End). Each step is recorded.");
        System.out.flush();
        ObjectMapper mapper = new ObjectMapper();
        int episodeIndex = 0;
        while (true) {
            episodeIndex++;
            List<ImitationStep> steps = new ArrayList<>();
            EpisodeStats e = executeEpisode(steps);
            if (e == null) {
                System.out.println("Imitation dump finished. Episodes written: " + (episodeIndex - 1));
                break;
            }
            Path episodeFile = dir.resolve(String.format("episode_%03d.json", episodeIndex));
            mapper.writerWithDefaultPrettyPrinter().writeValue(episodeFile.toFile(), steps);
        }
    }

    /**
     * Run episodes until user chooses 4 (end and export).
     * Each step: console prompts 1=WAIT 2=ATTACK 3=EAT 4=End & export.
     */
    public void executeEpisodes() throws InterruptedException, IOException {
        int i = 0;
        double rewardWin = 0.0;
        int xpWin = 0;
        int killsWin = 0, deathsWin = 0, lvlsWin = 0;
        long stepsWin = 0;
        int epWinsWin = 0, epLossesWin = 0;
        int eatCntWin = 0, eatInvWin = 0, eatAtFullHpWin = 0, eatOvereatWin = 0, eatAtLowHpWin = 0, eatSuccessWin = 0;
        int attackSuccessWin = 0;
        long foodLeftWin = 0;
        int[] actionCountsWin = new int[ANN_ACTIONS];

        while (true) {
            EpisodeStats e = executeEpisode();
            if (e == null) {
                // User chose 4: end and export
                String path = "src/main/resources/ann/player/" + ann.suggestFilename();
                saveAnn(path);
                System.out.println("Exported ANN to " + path);
                break;
            }
            i++;

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

            if (i % LOG_EVERY == 0 && i > 0) {
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
                    i, avgR, kdRatio, avgKills, avgDeaths, winRate,
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

                rewardWin = 0.0; xpWin = 0; killsWin = 0; deathsWin = 0; lvlsWin = 0;
                stepsWin = 0; epWinsWin = 0; epLossesWin = 0;
                eatCntWin = 0; eatInvWin = 0; eatAtFullHpWin = 0; eatOvereatWin = 0; eatAtLowHpWin = 0; eatSuccessWin = 0; attackSuccessWin = 0; foodLeftWin = 0;
                Arrays.fill(actionCountsWin, 0);
            }
        }
    }

    /**
     * Reads one action from console: 1=WAIT, 2=ATTACK, 3=EAT, 4=End & export.
     * Returns 0, 1, 2 for actions; 4 means user requested end (caller should export and exit).
     */
    private int readActionFromConsole() {
        while (true) {
            System.out.print("1=WAIT 2=ATTACK 3=EAT 4=End & export > ");
            System.out.flush();
            if (!console.hasNextLine()) return CONSOLE_END_AND_EXPORT;
            String line = console.nextLine().trim();
            if (line.equals("1")) return 0;
            if (line.equals("2")) return 1;
            if (line.equals("3")) return 2;
            if (line.equals("4")) return CONSOLE_END_AND_EXPORT;
            System.out.println("Enter 1, 2, 3, or 4");
        }
    }

    /**
     * One episode: you choose actions via console. SARSA loop unchanged (execute, reward, sarsaUpdate).
     * Returns null if user chose 4 (end & export) during the episode.
     * When {@code imitationCollector} is non-null, each (state, action) is added for behaviour-cloning JSON dump.
     */
    public EpisodeStats executeEpisode(List<ImitationStep> imitationCollector) throws InterruptedException {
        prevDeaths = 0;
        prevKills = 0;
        episodeEatInvalid = 0;
        episodeEatAtFullHp = 0;
        episodeEatOvereat = 0;
        episodeEatAtLowHp = 0;
        episodeEatSuccess = 0;
        episodeAttackSuccess = 0;
        System.out.println("Resetting game & fetching state...");
        System.out.flush();
        State currentState = initState();
        Thread.sleep(500);
        System.out.println("Enter your action (1=WAIT 2=ATTACK 3=EAT 4=End & export):");
        System.out.flush();

        int currentAction = readActionFromConsole();
        if (currentAction == CONSOLE_END_AND_EXPORT) return null;

        double totalReward = 0;
        long steps = 0;
        int waitCount = 0, attackCount = 0, eatCount = 0;

        while (prevKills < KILLS_CAP && prevDeaths < DEATHS_CAP) {
            if (imitationCollector != null) {
                imitationCollector.add(new ImitationStep(currentState.scale(), currentAction));
            }
            steps++;
            if (currentAction == 0) waitCount++;
            else if (currentAction == 1) attackCount++;
            if (currentAction == 2) {
                eatCount++;
                if (currentState.getCurrentHp() < 5) episodeEatAtLowHp++;
            }

            StateReward sr = executeAction(currentAction);

            State nextState = sr.getState();
            if (currentAction == 1 && lastAttackSuccess) episodeAttackSuccess++;
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

            int nextAction = readActionFromConsole();
            if (nextAction == CONSOLE_END_AND_EXPORT) {
                ann.sarsaUpdate(currentState.scale(), currentAction, sr.getReward(), nextState.scale(), 0, true, ALPHA, GAMMA);
                return null;
            }

            totalReward += sr.getReward();

            boolean terminal = (prevKills >= KILLS_CAP || prevDeaths >= DEATHS_CAP);

            ann.sarsaUpdate(currentState.scale(), currentAction, sr.getReward(), nextState.scale(), nextAction, terminal, ALPHA, GAMMA);

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

    /** One episode without imitation dump (for normal play). */
    public EpisodeStats executeEpisode() throws InterruptedException {
        return executeEpisode(null);
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
                if (newState.getCanAttack() == 1) {
                    reward -= 0.01;
                }
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
            case 2 :
                lastAttackSuccess = false;
                ConsumeFoodResult response = executor.consumeFood();
                if (!response.isSuccess()) {
                    // optional penalty; match agent (commented out)
                }
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
            default:
                lastAttackSuccess = false;
                Thread.sleep(550);
                newState = stateTransformer.fetchCurrentState();
                reward += calculateReward(newState);
                break;
        }
        return new StateReward(reward, newState);
    }

    public double calculateReward(State state){
        double reward = 0;
        reward += (state.getNpcKillCount() - prevKills) * 5.0;
        reward -= (state.getDeathCount() - prevDeaths) * 4.0;

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

    /**
     * Loads ANN weights from JSON. Creates ann with current ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS
     * then fills weights. Throws if file dimensions do not match (inputs, neurons, outputs).
     */
    public void loadAnn(String filePath) throws IOException {
        ann = new TinyQNetwork(ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS);
        ann.loadWeightsFromJson(filePath);
    }

    /**
     * Reads all episode_*.json files from {@value #IMITATION_STATE_DIR} and runs behavioural-cloning
     * updates on the ANN for each (state, action). Logs steps applied and before/after accuracy.
     *
     * @param learningRate step size for each BC update
     * @return number of steps (state, action) applied
     */
    public int trainFromImitationData(double learningRate) throws IOException {
        if (ann == null) throw new IllegalStateException("ANN is null");
        Path dir = Path.of(IMITATION_STATE_DIR);
        if (!Files.isDirectory(dir)) {
            System.out.println("BC: no directory " + IMITATION_STATE_DIR + ", skipping.");
            return 0;
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Path> files = Files.list(dir)
            .filter(p -> p.getFileName().toString().startsWith("episode_") && p.getFileName().toString().endsWith(".json"))
            .sorted(Comparator.comparing(Path::getFileName))
            .toList();
        if (files.isEmpty()) {
            System.out.println("BC: no episode_*.json files in " + IMITATION_STATE_DIR);
            return 0;
        }
        int totalSteps = 0;
        int beforeCorrect = 0;
        for (Path file : files) {
            ImitationStep[] steps = mapper.readValue(file.toFile(), ImitationStep[].class);
            for (ImitationStep step : steps) {
                if (!isValidStep(step)) continue;
                if (ann.argmax(step.state) == step.action) beforeCorrect++;
                ann.updateBehavioralCloning(step.state, step.action, learningRate);
                totalSteps++;
            }
        }
        int afterCorrect = 0;
        for (Path file : files) {
            ImitationStep[] steps = mapper.readValue(file.toFile(), ImitationStep[].class);
            for (ImitationStep step : steps) {
                if (!isValidStep(step)) continue;
                if (ann.argmax(step.state) == step.action) afterCorrect++;
            }
        }
        double beforePct = totalSteps == 0 ? 0 : 100.0 * beforeCorrect / totalSteps;
        double afterPct = totalSteps == 0 ? 0 : 100.0 * afterCorrect / totalSteps;
        System.out.printf("BC: steps=%d, before accuracy=%.1f%%, after accuracy=%.1f%% (lr=%.3f)%n",
            totalSteps, beforePct, afterPct, learningRate);
        return totalSteps;
    }

    private static boolean isValidStep(ImitationStep step) {
        return step.state != null && step.state.length == State.FEATURE_COUNT
            && step.action >= 0 && step.action < Actions.ACTION_COUNT;
    }

    /** One (state, action) step for imitation JSON; used by Jackson. */
    @SuppressWarnings("unused")
    public static class ImitationStep {
        public double[] state;
        public int action;

        public ImitationStep() {}

        public ImitationStep(double[] state, int action) {
            this.state = state;
            this.action = action;
        }
    }



    /**
     * Train ANN from imitation JSON in {@value #IMITATION_STATE_DIR} and export to {@value #PRETRAINED_ANN_PATH}.
     * Run this test after you have episode_001.json, episode_002.json, ... from imitation mode.
     * Then in SarsaRspsAgent.executeSarsa() load that path before executeEpisodes() to start RL from pretrained.
     */
    @Test
    public void trainFromImitationDataAndExport() throws IOException {
        ann = new TinyQNetwork(ANN_INPUTS, ANN_NEURONS, ANN_ACTIONS);
        int steps = trainFromImitationData(IMITATION_LEARN_RATE);
        if (steps > 0) {
            saveAnn(PRETRAINED_ANN_PATH);
            System.out.println("Exported pretrained ANN to " + PRETRAINED_ANN_PATH);
        }
    }
}
