package com.lunar_prototype.impossbleEscapeMC.effect;

/**
 * サーバーの太陽の影: 太陽の角度・向きがクライアントの空 (タイムラインの visual/sun_angle) と同じか、
 * 壁の向こう側が日陰になるか、7 ブロックずつの詰め方がシェーダー (iem_sun.glsl) の読み方と合うか
 */
public final class SunShadowServiceTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** iem_sun.glsl の iemSunState と同じ読み方 */
    private static int state(int[] values, int x, int y, int z) {
        int index = x + SunShadowService.W * (z + SunShadowService.D * y);
        return (values[index / 7] >> ((index % 7) * 2)) & 3;
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

        // 格子 (原点 0,0,0) の x = 30 に高さ 10 の壁 (z は全部)。太陽は東の上 (+x, +y、45°)
        int W = SunShadowService.W, H = SunShadowService.H, D = SunShadowService.D;
        int x0 = -2, y0 = 0, z0 = -2, nx = W + 60, ny = H + 50, nz = D + 4;
        boolean[] solid = new boolean[nx * ny * nz];
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                // 地面 (y = 0) と壁
                solid[(y * nz + z) * nx + (0 - x0)] = false;
                for (int x = 0; x < nx; x++) {
                    int wx = x + x0, wy = y + y0;
                    if (wy == 0 || (wx == 30 && wy <= 10)) solid[(y * nz + z) * nx + x] = true;
                }
            }
        }
        byte[] sky = new byte[W * H * D];
        java.util.Arrays.fill(sky, (byte) 15);
        SunShadowService.Snapshot snapshot = new SunShadowService.Snapshot(x0, y0, z0, nx, ny, nz, solid, sky);
        double s = Math.sqrt(0.5);
        int[] values = SunShadowService.compute(snapshot, 0, 0, 0, new double[]{s, s, 0});
        check(values.length == (W * H * D + 6) / 7, "value count " + values.length);
        check(state(values, 25, 1, 10) == 1, "west of the wall, low: shade " + state(values, 25, 1, 10));
        check(state(values, 35, 1, 10) == 2, "east of the wall: sun " + state(values, 35, 1, 10));
        check(state(values, 5, 1, 10) == 2, "far west (shadow 10 long): sun " + state(values, 5, 1, 10));
        check(state(values, 25, 12, 10) == 2, "above the wall: sun");
        check(state(values, 30, 5, 10) == 0, "inside the wall: none");
        check(state(values, 10, 0, 10) == 0, "ground: none");
        for (int v : values) check(v >= 0 && v < (1 << 14), "14 bit");

        // 空の光が暗い所 (室内) は対象外
        java.util.Arrays.fill(sky, (byte) 5);
        int[] indoor = SunShadowService.compute(snapshot, 0, 0, 0, new double[]{s, s, 0});
        check(state(indoor, 35, 1, 10) == 0, "indoor: none");
        System.out.println("SunShadowServiceTest OK");
    }
}
