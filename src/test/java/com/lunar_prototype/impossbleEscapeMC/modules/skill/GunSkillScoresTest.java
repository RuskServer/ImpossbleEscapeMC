package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import java.util.Arrays;

/**
 * 射撃のスキルの掛け方: データパックが読み込んだ値にだけ 1 回掛ける (掛けた値にもう一度掛けない)。
 * 読み込みは「持ち替えの残りが増えた」か「前に書いた値から変わった」で見つける
 */
public final class GunSkillScoresTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        double[] m = {0.9, 1.0};
        // 前の tick: 銃 A (40, 100) を読み込んで 36 にした。持ち替えの残りは -1 (終わっている)
        GunSkillScores.State state = new GunSkillScores.State(new int[]{36, 100}, -1);

        // 何も起きていない tick: 書き換えない
        int[] next = GunSkillScores.step(state, new int[]{36, 100}, -1, m, 0.5);
        check(Arrays.equals(next, new int[]{36, 100}), "idle tick keeps values " + Arrays.toString(next));

        // 銃 B を読み込んだ (40 → たまたま前に書いた値と違う): 掛ける。持ち替えの残り 11 は半分に
        next = GunSkillScores.step(state, new int[]{40, 100}, 11, m, 0.5);
        check(next[0] == 36 && state.swapTimer == 6, "load scales " + Arrays.toString(next) + " swap " + state.swapTimer);
        // 次の tick: 書いた値 (36) のままなので掛けない。持ち替えの残りは減っていくだけ
        next = GunSkillScores.step(state, new int[]{36, 100}, 5, m, 0.5);
        check(next[0] == 36 && state.swapTimer == 5, "no double scaling " + Arrays.toString(next));

        // 読み込んだ値が、たまたま前に書いた値と同じ (36) でも、持ち替えの残りが増えたので掛ける
        GunSkillScores.State s2 = new GunSkillScores.State(new int[]{36, 100}, -1);
        next = GunSkillScores.step(s2, new int[]{36, 100}, 11, m, 0.5);
        check(next[0] == 32, "same value but reloaded " + Arrays.toString(next));

        // スコアが無い所は触らない
        GunSkillScores.State s3 = new GunSkillScores.State(new int[]{GunSkillScores.UNSET, 100}, GunSkillScores.UNSET);
        next = GunSkillScores.step(s3, new int[]{GunSkillScores.UNSET, 100}, GunSkillScores.UNSET, m, 0.5);
        check(next[0] == GunSkillScores.UNSET, "unset stays unset");
        System.out.println("GunSkillScoresTest OK");
    }
}
