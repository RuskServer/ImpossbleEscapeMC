package com.lunar_prototype.impossbleEscapeMC.effect;

/**
 * フラッシュライトを向けられた時のまぶしさの段: 近い・光の真ん中ほど強く、遠い・光の縁・明るい所・光が視線の外では弱いか
 */
public final class FlashlightDazzleTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int level(double angle, double distance, int light, double facingAngle) {
        return FlashlightDazzle.levelOf(FlashlightDazzle.strength(angle, distance, light) * FlashlightDazzle.facing(facingAngle));
    }

    public static void main(String[] args) {
        // 夜、光の真ん中で、光の方を見ている
        check(level(0, 3, 0, 0) == 3, "3m は一番強い段");
        check(level(0, 10, 0, 0) == 2, "10m は中の段");
        check(level(0, 20, 0, 0) == 1, "20m は弱い段");
        check(FlashlightDazzle.strength(0, FlashlightDazzle.RANGE, 0) >= FlashlightDazzle.CORE_MIN, "夜は届く所の端まで芯が見える");
        // 光の縁・外
        check(level(12, 3, 0, 0) < 3, "光の縁は弱く");
        check(level(FlashlightDazzle.EDGE_DEG, 3, 0, 0) == 0, "光の外は無し");
        // 昼 (明るさ 15) は近くても弱い
        check(level(0, 3, 15, 0) == 1, "昼は弱い段まで");
        // 光が視線の外 (横・後ろ) なら画面にはかけない
        check(level(0, 3, 0, 45) < 3, "視線から 45° は弱く");
        check(level(0, 3, 0, 90) == 0, "真横・後ろからは無し");
        // 強さが段を下回らない限り、段は強さの順
        int previous = 0;
        for (double p = 0; p <= 1.0; p += 0.01) {
            int l = FlashlightDazzle.levelOf(p);
            check(l >= previous && l <= 3, "段は強さの順");
            previous = l;
        }
        System.out.println("FlashlightDazzleTest ok");
    }
}
