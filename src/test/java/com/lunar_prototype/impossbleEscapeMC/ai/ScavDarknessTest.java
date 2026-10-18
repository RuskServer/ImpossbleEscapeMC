package com.lunar_prototype.impossbleEscapeMC.ai;

/** 空の光の暗くなり方 (バニラと同じ式) と、明るさによる見える距離の倍率を確かめる。サーバーは要らない */
public final class ScavDarknessTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // 昼は暗くならず、真夜中は 11 (空の光 15 が 4 になる)。雨・雷雨の昼は少し暗い
        check(ScavVision.skyDarken(6000, false, false) == 0, "noon " + ScavVision.skyDarken(6000, false, false));
        check(ScavVision.skyDarken(18000, false, false) == 11, "midnight " + ScavVision.skyDarken(18000, false, false));
        check(ScavVision.skyDarken(6000, true, false) == 3, "rainy noon " + ScavVision.skyDarken(6000, true, false));
        check(ScavVision.skyDarken(6000, true, true) > ScavVision.skyDarken(6000, true, false), "storm darker than rain");
        check(ScavVision.skyDarken(18000 + 24000 * 3, false, false) == 11, "later night");
        int last = -1;
        for (long t = 6000; t <= 18000; t += 500) {
            int d = ScavVision.skyDarken(t, false, false);
            check(d >= last, "darkens towards night at " + t);
            last = d;
        }
        // 暗いほど見える距離が短く、追っている相手は見つける時より見失いにくい
        check(ScavVision.spotVisibility(15) == 1.0 && ScavVision.spotVisibility(9) < 1.0, "bright");
        check(ScavVision.spotVisibility(2) < ScavVision.spotVisibility(5) && ScavVision.spotVisibility(5) < ScavVision.spotVisibility(9), "monotonic");
        for (int light = 0; light <= 15; light++) {
            check(ScavVision.trackingVisibility(light) >= ScavVision.spotVisibility(light), "tracking >= spotting at " + light);
        }
        check(ScavVision.trackingVisibility(0) == 0.12 && ScavVision.trackingVisibility(3) == 0.12, "dark tracking multiplier");
        System.out.println("ScavDarknessTest: sky darkening and visibility factors passed");
    }
}
