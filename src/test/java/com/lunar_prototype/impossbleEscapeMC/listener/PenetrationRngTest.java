package com.lunar_prototype.impossbleEscapeMC.listener;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

/** 下位クラスの弾の貫通抽選 (PRD) の確率・平均・戻り方・TTL・部位の分離を確かめる。サーバーは要らない */
public final class PenetrationRngTest {
    private static void check(boolean condition, String message) { org.junit.jupiter.api.Assertions.assertTrue(condition, message); }

    private static void close(double actual, double expected, double epsilon, String message) {
        if (Math.abs(actual - expected) > epsilon) throw new AssertionError(message + ": " + actual + " != " + expected);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("chance ramps up")
    void chanceRampsUp() throws Exception {
        PenetrationRng.reset();
        close(PenetrationRng.chance(1), Math.PI / 2.0 * 0.075 * 0.075, 1e-12, "first chance");
        check(PenetrationRng.chance(2) > PenetrationRng.chance(1), "chance ramps up");
        close(PenetrationRng.chance(1000), 1.0, 1e-12, "chance capped at 1");
        // 長期平均は 7.5% のまま (PRD は偏りだけを変える)
        PenetrationRng.reset();
        // 貫通したら連続回数が戻り、次の 1 発はまた低い確率から
        PenetrationRng.reset();
        // 4 秒当たらなければ連続回数を忘れる (3 発目なら通る確率でも、戻った 1 発目では通らない)
        PenetrationRng.reset();
        // 頭と胴は別々に数える
        PenetrationRng.reset();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("no armor always penetrates")
    void noArmorAlwaysPenetrates() throws Exception {
        UUID shooter = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        check(PenetrationRng.penetrates(1, 0, shooter, victim, false, 0L, 0.999), "no armor always penetrates");
        check(PenetrationRng.penetrates(5, 4, shooter, victim, false, 0L, 0.94), "higher class penetrates");
        check(!PenetrationRng.penetrates(5, 4, shooter, victim, false, 0L, 0.96), "higher class still fails sometimes");
        check(PenetrationRng.penetrates(4, 4, shooter, victim, false, 0L, 0.69), "equal class penetrates");
        check(!PenetrationRng.penetrates(4, 4, shooter, victim, false, 0L, 0.71), "equal class still fails sometimes");
        Random random = new Random(20261024L);
        int shots = 1_000_000;
        int pens = 0;
        for (int i = 0; i < shots; i++) {
            if (PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, random.nextDouble())) pens++;
        }
        close((double) pens / shots, 0.075, 0.003, "long-run average");
        check(PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.0), "forced penetration");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.009), "streak resets after a penetration");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.999), "first miss");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.999), "second miss");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L + 4_001L, 0.009), "ttl resets the streak");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.999), "body miss");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, false, 1000L, 0.999), "body miss again");
        check(!PenetrationRng.penetrates(1, 5, shooter, victim, true, 1000L, 0.009), "head starts fresh");
    }
}
