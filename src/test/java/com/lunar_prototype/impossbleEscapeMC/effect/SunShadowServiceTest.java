package com.lunar_prototype.impossbleEscapeMC.effect;

/**
 * サーバーの光: 太陽の角度・向きがクライアントの空 (タイムラインの visual/sun_angle) と同じか、
 * 日なた・日陰・室内・光源ブロックの周りで、バニラとの明るさの比がそれらしい向きになるか、詰め方がシェーダー (iem_sun.glsl) の読み方と合うか
 */
public final class SunShadowServiceTest {
    private static final int W = SunShadowService.W, H = SunShadowService.H, D = SunShadowService.D;
    private static final int M = SunShadowService.MARGIN;
    private static final int SX = SunShadowService.SPAN_X, SY = SunShadowService.SPAN_Y, SZ = SunShadowService.SPAN_Z;
    // 光の通り方の範囲 (格子の原点 (0, 0, 0) の周りに余白と、太陽へたどる分)
    private static final int TX0 = -M - 2, TY0 = -M, TZ0 = -M - 2, TNX = SX + 64, TNY = SY + 50, TNZ = SZ + 4;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** iem_sun.glsl と同じ読み方: 下 3bit が倍率の段、上 1bit が太陽が当たっているか */
    private static int code(int[] values, int x, int y, int z) {
        int g = x + W * (z + D * y);
        return (values[g / 3] >> ((g % 3) * 4)) & 15;
    }

    private static int level(SunShadowService.Grids grids, int x, int y, int z) {
        return code(grids.light(), x, y, z) & 7;
    }

    private static boolean sunlit(SunShadowService.Grids grids, int x, int y, int z) {
        return (code(grids.light(), x, y, z) & 8) != 0;
    }

    private static int kind(SunShadowService.Grids grids, int x, int y, int z) {
        int c = x / 2 + SunShadowService.CW * (z / 2 + SunShadowService.CD * (y / 2));
        return (grids.kinds()[c / 7] >> ((c % 7) * 2)) & 3;
    }

    private static void setTransmit(SunShadowService.Snapshot s, int x, int y, int z, int value) {
        s.transmit()[((y - TY0) * TNZ + (z - TZ0)) * TNX + (x - TX0)] = (byte) value;
    }

    /** 格子の原点 (0, 0, 0)。y <= 0 が地面、x = 30 に高さ 10 の壁 (z は全部、壁のブロックの光の通り方は wall)。空の光はどこも skyLight */
    private static SunShadowService.Snapshot wall(int wall, int skyLight) {
        byte[] transmit = new byte[TNX * TNY * TNZ];
        for (int y = 0; y < TNY; y++) {
            for (int z = 0; z < TNZ; z++) {
                for (int x = 0; x < TNX; x++) {
                    int wx = x + TX0, wy = y + TY0;
                    transmit[(y * TNZ + z) * TNX + x] = (byte) (wy <= 0 ? 0 : wx == 30 && wy <= 10 ? wall : 255);
                }
            }
        }
        int span = SX * SY * SZ;
        byte[] sky = new byte[span];
        java.util.Arrays.fill(sky, (byte) skyLight);
        byte[] albedo = new byte[span];
        java.util.Arrays.fill(albedo, (byte) 128);
        return new SunShadowService.Snapshot(TX0, TY0, TZ0, TNX, TNY, TNZ, transmit, -M, -M, -M, sky, new byte[span], albedo, new byte[span], 10);
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
        check(SunShadowService.lightLevel(1.0) == SunShadowService.LEVEL_ONE, "ratio 1 is level 5");

        // 昼の屋外 (太陽は東の上 45°、空の光 15): 壁の東は日なた (バニラより少し明るい)、壁の西の低い所は日陰 (暗い)
        double s = Math.sqrt(0.5);
        SunShadowService.Light day = new SunShadowService.Light(new double[]{s, s, 0}, 1.0, 1.0);
        SunShadowService.Grids out = SunShadowService.computeLight(wall(0, 15), 0, 0, 0, day);
        check(out.light().length == (W * H * D + 2) / 3, "light count " + out.light().length);
        check(out.kinds().length == (SunShadowService.CW * SunShadowService.CH * SunShadowService.CD + 6) / 7, "kind count");
        check(sunlit(out, 35, 1, 10) && level(out, 35, 1, 10) >= SunShadowService.LEVEL_ONE, "east of the wall: sunlit " + level(out, 35, 1, 10));
        check(!sunlit(out, 25, 1, 10) && level(out, 25, 1, 10) < SunShadowService.LEVEL_ONE, "west of the wall: shade " + level(out, 25, 1, 10));
        check(sunlit(out, 5, 1, 10), "far west (shadow 10 long): sunlit");
        check(kind(out, 36, 2, 10) == SunShadowService.KIND_SUN, "sunlit: sun kind " + kind(out, 36, 2, 10));
        // 固体のブロックは隣の空気の段の平均 (地面は上の空気と同じくらい)
        check(Math.abs(level(out, 36, 0, 10) - level(out, 36, 1, 10)) <= 1, "ground follows the air above");

        // 室内 (空の光 5): 日が差し込む所はバニラよりずっと明るい、日陰はバニラより暗い
        SunShadowService.Grids room = SunShadowService.computeLight(wall(0, 5), 0, 0, 0, day);
        check(level(room, 35, 1, 10) > SunShadowService.LEVEL_ONE, "indoor sunbeam: brighter than vanilla " + level(room, 35, 1, 10));
        check(level(room, 25, 1, 10) < SunShadowService.LEVEL_ONE, "indoor shade: darker than vanilla " + level(room, 25, 1, 10));

        // 光源ブロック (夜、空の光 0): (20, 2, 20) にレベル 14 の光源、(23, 1..4, 18..22) に壁。
        // バニラの光は壁を回り込んで届く (壁をよけて 1 ブロックごとに 1 減る) が、壁の裏 (25, 2, 20) には直接は届かない → 暗く。
        // 壁の無い向きの斜め (22, 3, 22) はまっすぐの距離の方が短い → 明るく
        SunShadowService.Snapshot dark = wall(0, 0);
        for (int y = 1; y <= 4; y++) for (int z = 18; z <= 22; z++) setTransmit(dark, 23, y, z, 0);
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        dark.blockLight()[dark.lightIndex(20, 2, 20)] = 14;
        queue.add(new int[]{20, 2, 20});
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int lv = dark.block(p[0], p[1], p[2]);
            for (int[] f : faces) {
                int x = p[0] + f[0], y = p[1] + f[1], z = p[2] + f[2];
                int i = dark.lightIndex(x, y, z);
                if (i < 0 || dark.transmit(x, y, z) == 0 || dark.blockLight()[i] >= lv - 1) continue;
                dark.blockLight()[i] = (byte) (lv - 1);
                queue.add(new int[]{x, y, z});
            }
        }
        SunShadowService.Grids night = SunShadowService.computeLight(dark, 0, 0, 0, new SunShadowService.Light(null, 0.0, 0.2));
        int behind = level(night, 25, 2, 20), diagonal = level(night, 22, 3, 22);
        check(behind < SunShadowService.LEVEL_ONE, "behind the wall: darker than vanilla " + behind);
        check(diagonal > behind, "in sight brighter than behind the wall " + diagonal + " > " + behind);
        check(kind(night, 20, 3, 20) == SunShadowService.KIND_WARM, "near the light: warm kind " + kind(night, 20, 3, 20));
        check(!sunlit(night, 35, 1, 10), "night: no sun");
        System.out.println("SunShadowServiceTest OK");
    }
}
