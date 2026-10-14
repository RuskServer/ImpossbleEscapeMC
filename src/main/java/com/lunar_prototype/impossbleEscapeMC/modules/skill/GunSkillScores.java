package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 射撃のスキルを、データパック (Toi's Armory) の銃の数値に掛ける (データパックは変えない)。
 * <p>
 * データパックは銃を持ち替えた時に、銃の数値をスコア toisarm.state.* に読み込み、持ち替えの残り toisarm.timer.swap を
 * -1 に戻してから銃の値にする (これらのスコアは読み込みの時にしか書かない)。プラグインの処理はデータパックより先に動くので、
 * 毎 tick 前の tick に読み込まれた値を見つけて倍率を掛ける (読み込まれた tick だけは元の値のまま)。
 * 読み込まれたかは、持ち替えの残りが増えたか、自分が書いた値から変わったかで見る
 */
final class GunSkillScores implements Runnable {

    /** 倍率を掛けるスコアと、そのスキルの値 */
    record Target(String objective, SkillStat stat) {
    }

    static final List<Target> TARGETS = List.of(
            new Target("toisarm.state.reload", SkillStat.RELOAD_TIME),
            new Target("toisarm.state.reload_loop", SkillStat.RELOAD_TIME),
            new Target("toisarm.state.empty_reload", SkillStat.EMPTY_RELOAD_TIME),
            new Target("toisarm.state.swap", SkillStat.SWAP_TIME),
            new Target("toisarm.state.ads", SkillStat.ADS_TIME),
            new Target("toisarm.state.sprint", SkillStat.SPRINT_TO_FIRE),
            new Target("toisarm.state.bolt_action", SkillStat.BOLT_ACTION),
            new Target("toisarm.state.pitch_recoil_multiplier", SkillStat.RECOIL_PITCH),
            new Target("toisarm.state.yaw_recoil_multiplier", SkillStat.RECOIL_YAW),
            new Target("toisarm.state.accuracy_idle", SkillStat.HIPFIRE_SPREAD),
            new Target("toisarm.state.accuracy_multiplier_move", SkillStat.MOVE_SPREAD),
            new Target("toisarm.state.bloom_increase_rate", SkillStat.BLOOM_INCREASE),
            new Target("toisarm.state.bloom_decay", SkillStat.BLOOM_DECAY),
            new Target("toisarm.state.sway_yaw", SkillStat.SWAY),
            new Target("toisarm.state.sway_pitch", SkillStat.SWAY),
            new Target("toisarm.state.shake_yaw", SkillStat.SHAKE),
            new Target("toisarm.state.shake_pitch", SkillStat.SHAKE),
            new Target("toisarm.state.shake_yaw_random", SkillStat.SHAKE),
            new Target("toisarm.state.shake_pitch_random", SkillStat.SHAKE));
    /** 持ち替えの残り (読み込みで銃の値に戻る) */
    static final String SWAP_TIMER = "toisarm.timer.swap";
    /** スコアが無い */
    static final int UNSET = Integer.MIN_VALUE;

    /** プレイヤーごとの、前の tick に見た (書いた) 値 */
    static final class State {
        final int[] written;
        int swapTimer;

        State(int[] written, int swapTimer) {
            this.written = written;
            this.swapTimer = swapTimer;
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    void forget(UUID player) {
        states.remove(player);
    }

    @Override
    public void run() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            double[] multipliers = new double[TARGETS.size()];
            boolean any = false;
            for (int i = 0; i < multipliers.length; i++) {
                multipliers[i] = Math.max(0.05, SkillModule.multiplier(player, TARGETS.get(i).stat()));
                any |= multipliers[i] != 1.0;
            }
            if (!any) {   // 射撃のスキルが無い人は触らない
                states.remove(player.getUniqueId());
                continue;
            }
            int[] current = new int[TARGETS.size()];
            for (int i = 0; i < current.length; i++) current[i] = read(board, TARGETS.get(i).objective(), player);
            int swap = read(board, SWAP_TIMER, player);
            double swapMultiplier = Math.max(0.05, SkillModule.multiplier(player, SkillStat.SWAP_TIME));

            State state = states.get(player.getUniqueId());
            if (state == null) {
                // 初めて見た時は今の値を覚えるだけ (前に書いた値かもしれないので掛けない。次の読み込みから掛ける)
                states.put(player.getUniqueId(), new State(current, swap));
                continue;
            }
            int[] next = step(state, current, swap, multipliers, swapMultiplier);
            for (int i = 0; i < next.length; i++) {
                if (next[i] != current[i]) write(board, TARGETS.get(i).objective(), player, next[i]);
            }
            if (state.swapTimer != swap) write(board, SWAP_TIMER, player, state.swapTimer);
        }
    }

    /**
     * 1 tick ぶんの判定 (サーバーなしで確かめられる)。読み込まれた値 (持ち替えの残りが増えた・前に書いた値から変わった) に倍率を掛け、
     * state を書いた値にする
     *
     * @return 書く値 (変えない所は current のまま)
     */
    static int[] step(State state, int[] current, int swap, double[] multipliers, double swapMultiplier) {
        boolean loaded = swap != UNSET && state.swapTimer != UNSET && swap > state.swapTimer;
        int[] next = current.clone();
        for (int i = 0; i < current.length; i++) {
            if (current[i] == UNSET) continue;
            if (loaded || current[i] != state.written[i]) next[i] = (int) Math.round(current[i] * multipliers[i]);
            state.written[i] = next[i];
        }
        state.swapTimer = loaded && swap > 0 ? (int) Math.round(swap * swapMultiplier) : swap;
        return next;
    }

    private static int read(Scoreboard board, String name, Player player) {
        Objective objective = board.getObjective(name);
        if (objective == null) return UNSET;
        Score score = objective.getScore(player);
        return score.isScoreSet() ? score.getScore() : UNSET;
    }

    private static void write(Scoreboard board, String name, Player player, int value) {
        Objective objective = board.getObjective(name);
        if (objective != null) objective.getScore(player).setScore(value);
    }
}
