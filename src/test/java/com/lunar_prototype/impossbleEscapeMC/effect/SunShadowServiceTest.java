package com.lunar_prototype.impossbleEscapeMC.effect;

/**
 * サーバーの太陽の影: 太陽の角度・向きがクライアントの空 (タイムラインの visual/sun_angle) と同じか、
 * 壁の向こう側が日陰になるか、7 ブロックずつの詰め方がシェーダー (iem_sun.glsl) の読み方と合うか
 */
public final class SunShadowServiceTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** iem_sun.glsl の iemSunState と同じ読み方 (大きさ w x h x d の格子) */
    private static int state(int[] values, int w, int d, int x, int y, int z) {
        int index = x + w * (z + d * y);
        return (values[index / 7] >> ((index % 7) * 2)) & 3;
    }

    private static int near(int[] values, int x, int y, int z) {
        return state(values, SunShadowService.W, SunShadowService.D, x, y, z);
    }

    private static int far(int[] values, int x, int y, int z) {
        return state(values, SunShadowService.FW, SunShadowService.FD, x, y, z);
    }

    /** 計算する範囲の原点 (0, 0, 0) で、x = 30 に高さ 10 の壁 (z は全部、壁のブロックの光の通り方は wall)、y = 0 が地面 */
    private static SunShadowService.Snapshot wall(int wall, int skyLight) {
        int sx = SunShadowService.SPAN_X, sy = SunShadowService.SPAN_Y, sz = SunShadowService.SPAN_Z;
        int x0 = -2, y0 = 0, z0 = -2, nx = sx + 60, ny = sy + 50, nz = sz + 4;
        byte[] transmit = new byte[nx * ny * nz];
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    int wx = x + x0, wy = y + y0;
                    transmit[(y * nz + z) * nx + x] = (byte) (wy == 0 ? 0 : wx == 30 && wy <= 10 ? wall : 255);
                }
            }
        }
        byte[] sky = new byte[sx * sy * sz];
        java.util.Arrays.fill(sky, (byte) skyLight);
        return new SunShadowService.Snapshot(x0, y0, z0, nx, ny, nz, transmit, sky, 10);
    }

    public static void main(String[] args) {
        // 正午は真上、日の出 (0) は東 (+x)、日の入り (12000) は西
        check(Math.abs(SunShadowService.sunAngle(6000)) < 1e-6, "noon angle " + SunShadowService.sunAngle(6000));
        double[] noon = SunShadowService.sunDirection(SunShadowService.sunAngle(6000));
        check(noon[1] > 0.999, "noon is overhead");
        double[] rise = SunShadowService.sunDirection(SunShadowService.sunAngle(0));
        check(rise[0] > 0.95 && rise[1] > 0.0, "sunrise is east, a little above the horizon " + rise[0]);
        double[] set = SunShadowService.sunDirection(SunShadowService.sunAngle(12000));
        check(set[0] < -0.95, "sunset is west " + set[0]);
        check(SunShadowService.sunAngle(9000) > 0 && SunShadowService.sunAngle(9000) < 90, "afternoon in (0, 90)");

        // 太陽は東の上 (+x, +y、45°)。近くの格子は計算する範囲の (0, 0, 0) から
        double s = Math.sqrt(0.5);
        double[] sun = {s, s, 0};
        byte[] fine = SunShadowService.fineStates(wall(0, 15), 0, 0, 0, sun);
        int[] values = SunShadowService.packNear(fine, 0, 0, 0);
        int W = SunShadowService.W, H = SunShadowService.H, D = SunShadowService.D;
        check(values.length == (W * H * D + 6) / 7, "value count " + values.length);
        check(near(values, 25, 1, 10) == 1, "west of the wall, low: shade " + near(values, 25, 1, 10));
        check(near(values, 35, 1, 10) == 2, "east of the wall: sun " + near(values, 35, 1, 10));
        check(near(values, 5, 1, 10) == 2, "far west (shadow 10 long): sun " + near(values, 5, 1, 10));
        check(near(values, 25, 12, 10) == 2, "above the wall: sun");
        check(near(values, 30, 5, 10) == 0, "inside the wall: none");
        check(near(values, 10, 0, 10) == 0, "ground: none");
        for (int v : values) check(v >= 0 && v < (1 << 14), "14 bit");

        // 近くの格子をずらして詰めても同じブロックを指す
        int[] shifted = SunShadowService.packNear(fine, 8, 0, 8);
        check(near(shifted, 25 - 8, 1, 10 - 8) == 1 && near(shifted, 35 - 8, 1, 10 - 8) == 2, "near offset");

        // 粗い格子 (2 x 2 x 2 ブロック): 壁の西の低い所は日陰、東は日なた、地面の高さのマス (地面 + 空気) は空気の側で決まる
        int[] coarse = SunShadowService.packFar(fine);
        check(coarse.length == (SunShadowService.FW * SunShadowService.FH * SunShadowService.FD + 6) / 7, "far count");
        check(far(coarse, 13, 1, 5) == 1, "far: west of the wall, shade " + far(coarse, 13, 1, 5));
        check(far(coarse, 17, 1, 5) == 2, "far: east of the wall, sun " + far(coarse, 17, 1, 5));
        check(far(coarse, 17, 0, 5) == 2, "far: ground cell follows its air");
        check(far(coarse, 13, 7, 5) == 2, "far: above the wall, sun");

        // 空の光が暗い所 (室内): 日陰は対象外、日が差し込む所は室内の日なた
        int[] indoor = SunShadowService.packNear(SunShadowService.fineStates(wall(0, 5), 0, 0, 0, sun), 0, 0, 0);
        check(near(indoor, 25, 1, 10) == 0, "indoor shade: none");
        check(near(indoor, 35, 1, 10) == 3, "indoor sunbeam");

        // 光を半分通す壁 (鉄格子): 45° の光は壁を 2 ブロック (ブロックの角を通る) 通って 1/4 → 2 x 2 x 2 ブロックのうち 2 か所が日なた。
        // 壁のブロックそのものは空気と同じに扱う
        int[] bars = SunShadowService.packNear(SunShadowService.fineStates(wall(128, 15), 0, 0, 0, sun), 0, 0, 0);
        int lit = 0;
        for (int i = 0; i < 8; i++) lit += near(bars, 24 + (i & 1), 2 + (i >> 1 & 1), 10 + (i >> 2)) == 2 ? 1 : 0;
        check(lit == 2, "half-transparent wall: a quarter lit " + lit);
        check(near(bars, 30, 5, 10) != 0 && near(bars, 30, 12, 10) == 2, "bars blocks are air, not none");
        // 水平の光は 1 ブロックだけ通る → 半分
        double once = SunShadowService.sunTransmit(wall(128, 15), 20.5, 1.5, 10.5, new double[]{1, 0, 0});
        check(Math.abs(once - 128 / 255.0) < 1e-9, "one bars block on a horizontal ray " + once);
        System.out.println("SunShadowServiceTest OK");
    }
}
