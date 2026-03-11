package com.lunar_prototype.impossbleEscapeMC.ai.util;

/**
 * 戦闘状況を評価し、AIの内部状態（感情）へのフィードバックを計算する
 */
public class CombatEvaluator {

    /**
     * 攻撃性(Aggression)の上昇量を計算する
     * 平地や被弾中は上昇を抑制し、有利な状況で急上昇させる
     */
    public static float calculateAggressionSpike(double distance, float suppression, double healthPercent, boolean canSee) {
        if (!canSee || suppression > 0.4f || healthPercent < 0.4) {
            return -0.05f; // 不利な状況では攻撃性を下げる
        }

        // 近距離（10ブロック以内）かつ安全な状況なら上昇
        float spike = 0.05f;
        if (distance < 10.0) {
            spike *= 1.5f;
        }
        
        return spike;
    }

    /**
     * 恐怖心(Fear)の上昇量を計算する
     * 被弾や制圧による非線形な上昇
     */
    public static float calculateFearSpike(float suppression, double healthPercent, double ammoPercent) {
        float spike = 0.0f;
        
        // 体力が低いほど、被弾による恐怖心の増加が加速する
        if (suppression > 0.2f) {
            spike += (float) (suppression * (1.5 - healthPercent) * 0.2);
        }

        if (healthPercent < 0.3) {
            spike += 0.1f;
        }

        if (ammoPercent < 0.1) {
            spike += 0.05f;
        }

        return spike;
    }
}
