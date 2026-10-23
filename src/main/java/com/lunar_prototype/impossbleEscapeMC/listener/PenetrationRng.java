package com.lunar_prototype.impossbleEscapeMC.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 防具の貫通の抽選。下位クラスの弾だけ、連続で外れるほど確率が上がる疑似乱数 (PRD) で引く。
 * <p>
 * 普通の乱数だと、フルオートで撃ち込んだ時に「3発続けて抜ける」「10発抜けない」のような偏りが出る。
 * 抜けるたびに連続回数を 0 に戻し、外れるたびに 1 増やして確率を上げることで、長期平均を
 * {@link #LOW_CLASS_CHANCE} に保ったまま、連続貫通と長い不貫通を抑える。式はジャム
 * ({@code DatapackGunCondition}) と同じ {@code 連続回数 x (PI/2) x 確率^2}。
 * <p>
 * 連続回数は「射手 x 被害者 x 頭か胴か x 防具クラス」ごとに覚える。胴で外した回数が頭の抽選に
 * 影響しないよう部位を分け、防具を持ち替えた時に前の防具の回数を引き継がないようクラスも入れる。
 * 一定時間 (TTL) 当たらなければ忘れる。ダメージイベントはメインスレッドなので HashMap でよい。
 */
final class PenetrationRng {

    /** 下位クラスの弾が上位の防具を抜く確率 (長期平均)。 */
    static final double LOW_CLASS_CHANCE = 0.075;
    /** 同じクラスの弾が防具を抜く確率。 */
    static final double EQUAL_CLASS_CHANCE = 0.70;
    /** 上のクラスの弾が防具を抜く確率。 */
    static final double HIGHER_CLASS_CHANCE = 0.95;
    /** これ以上当たらなければ連続回数を忘れる (ms)。 */
    private static final long TTL_MS = 4_000L;

    private static final Map<Key, Entry> STREAKS = new HashMap<>();
    private static int calls;

    private PenetrationRng() {
    }

    private record Key(UUID shooter, UUID victim, boolean head, int armorClass) {
    }

    private static final class Entry {
        int streak;
        long lastHitMillis;
    }

    static boolean penetrates(int ammoClass, int armorClass, UUID shooter, UUID victim, boolean head) {
        return penetrates(ammoClass, armorClass, shooter, victim, head,
                System.currentTimeMillis(), ThreadLocalRandom.current().nextDouble());
    }

    /** 抽選 (テスト用に時刻と乱数を渡せる)。 */
    static boolean penetrates(int ammoClass, int armorClass, UUID shooter, UUID victim, boolean head,
                              long now, double roll) {
        if (armorClass <= 0) return true;
        if (ammoClass > armorClass) return roll < HIGHER_CLASS_CHANCE;
        if (ammoClass == armorClass) return roll < EQUAL_CLASS_CHANCE;
        if (shooter == null || victim == null) return roll < LOW_CLASS_CHANCE;

        Key key = new Key(shooter, victim, head, armorClass);
        Entry entry = STREAKS.get(key);
        if (entry == null || now - entry.lastHitMillis > TTL_MS) {
            entry = new Entry();
            STREAKS.put(key, entry);
        }
        entry.lastHitMillis = now;
        entry.streak++;
        boolean penetrated = roll < chance(entry.streak);
        if (penetrated) entry.streak = 0;
        if ((++calls & 0xFF) == 0) STREAKS.entrySet().removeIf(e -> now - e.getValue().lastHitMillis > TTL_MS);
        return penetrated;
    }

    /** 連続 streak 回外した後の貫通率。1 回目がもっとも低く、外れるほど上がる。 */
    static double chance(int streak) {
        double target = LOW_CLASS_CHANCE;
        return Math.min(1.0, Math.max(0, streak) * (Math.PI / 2.0) * target * target);
    }

    /** テスト用。覚えている連続回数を消す。 */
    static void reset() {
        STREAKS.clear();
        calls = 0;
    }
}
