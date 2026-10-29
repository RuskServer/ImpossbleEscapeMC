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

    /** iem_sun.glsl と同じ読み方: 値 2 つ (下・上の 14bit) に 7 ブロック、4bit ずつ。下 3bit が倍率の段、上 1bit が太陽が当たっているか */
    private static int code(int[] values, int x, int y, int z) {
        int g = x + W * (z + D * y);
        int word = values[2 * (g / 7)] | values[2 * (g / 7) + 1] << 14;
        return (word >> ((g % 7) * 4)) & 15;
    }

    static int level(SunShadowService.Grids grids, int x, int y, int z) {
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
    static SunShadowService.Snapshot wall(int wall, int skyLight) {
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
        SunShadowService.Light day = new SunShadowService.Light(java.util.UUID.randomUUID(), new double[]{s, s, 0}, 1.0, 1.0);
        SunShadowService.Grids out = SunShadowService.computeLight(wall(0, 15), 0, 0, 0, day);
        check(out.light().length == 2 * ((W * H * D + 6) / 7), "light count " + out.light().length);
        for (int v : out.light()) check(v >= 0 && v < 1 << 14, "light value fits 14 bits " + v);
        // 全部 (ヘッダー・光の格子・光の種類・遠くの太陽の影とその外側) がデータの画素に入る
        int cells = SunShadowService.PAYLOAD + out.light().length + out.kinds().length
                + SunShadowService.FAR_COLUMNS * SunShadowService.FAR_COLUMNS + SunShadowService.OUTER_COLUMNS * SunShadowService.OUTER_COLUMNS;
        check(cells <= SunShadowService.DATA_CAPACITY, "data fits " + cells + " / " + SunShadowService.DATA_CAPACITY);
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
        SunShadowService.Grids night = SunShadowService.computeLight(dark, 0, 0, 0, new SunShadowService.Light(java.util.UUID.randomUUID(), null, 0.0, 0.2));
        int behind = level(night, 25, 2, 20), diagonal = level(night, 22, 3, 22);
        check(behind < SunShadowService.LEVEL_ONE, "behind the wall: darker than vanilla " + behind);
        check(diagonal > behind, "in sight brighter than behind the wall " + diagonal + " > " + behind);
        check(kind(night, 20, 3, 20) == SunShadowService.KIND_WARM, "near the light: warm kind " + kind(night, 20, 3, 20));
        check(!sunlit(night, 35, 1, 10), "night: no sun");
        // 遠くの太陽の影: 地面 (一番上のブロック y = 0) の上に、x = 60..63 (z は全部) の高さ 20 の建物。太陽は東の上 45°
        // → 建物の西 20 ブロック (列 20〜29) の地面は日陰 (日なたの始まる高さが地面より上)、建物の東 (列 40) は地面から日なた
        int width = SunShadowService.FAR_SPAN + 2 * SunShadowService.FAR_SUN_REACH;
        int[] heights = new int[width * SunShadowService.FAR_SPAN];
        for (int z = 0; z < SunShadowService.FAR_SPAN; z++) {
            for (int x = 60; x < 64; x++) heights[SunShadowService.FAR_SUN_REACH + x + width * z] = 20;
        }
        int[] farValues = SunShadowService.farColumns(heights, 0, new double[]{s, s, 0});
        check(farValues.length == SunShadowService.FAR_COLUMNS * SunShadowService.FAR_COLUMNS, "far count");
        int west = farValues[25 + SunShadowService.FAR_COLUMNS * 10], east = farValues[40 + SunShadowService.FAR_COLUMNS * 10];
        check((west & 127) == 1 && (west >> 7) > 1, "west of the building: ground in shade " + (west & 127) + " / " + (west >> 7));
        check((east & 127) == 1 && (east >> 7) == 1, "east of the building: ground in sun " + (east >> 7));
        int roof = farValues[30 + SunShadowService.FAR_COLUMNS * 10];
        check((roof & 127) == 21 && (roof >> 7) == 21, "roof: ground 21, sunlit");

        // 外側の遠くの太陽の影: 同じく x = 200..215 (z は全部) の高さ 40 の建物。太陽は東の上 45°。高さは 2 ブロックおき
        int ow = (SunShadowService.OUTER_SPAN + 2 * SunShadowService.OUTER_SUN_REACH) / SunShadowService.OUTER_SAMPLE;
        int od = SunShadowService.OUTER_SPAN / SunShadowService.OUTER_SAMPLE;
        int[] outerHeights = new int[ow * od];
        for (int z = 0; z < od; z++) {
            for (int x = 0; x < ow; x++) {
                int wx = x * SunShadowService.OUTER_SAMPLE - SunShadowService.OUTER_SUN_REACH;
                outerHeights[x + ow * z] = wx >= 200 && wx < 216 ? 40 : 0;
            }
        }
        int[] outerValues = SunShadowService.outerColumns(outerHeights, 0, new double[]{s, s, 0});
        check(outerValues.length == SunShadowService.OUTER_COLUMNS * SunShadowService.OUTER_COLUMNS, "outer count");
        // 列 x: 列 24 = 192..199 (建物のすぐ西、日陰)、列 20 = 160..167 (西 33〜40 ブロック、影の端あたり)、列 30 = 240.. (東、日なた)
        int shade = outerValues[24 + SunShadowService.OUTER_COLUMNS * 5], sunny = outerValues[30 + SunShadowService.OUTER_COLUMNS * 5];
        check((shade & 127) == 1 && (shade >> 7) > 30, "outer: west of the building in shade " + (shade >> 7));
        check((sunny & 127) == 1 && (sunny >> 7) == 1, "outer: east of the building in sun " + (sunny >> 7));
        int top = outerValues[25 + SunShadowService.OUTER_COLUMNS * 5];
        check((top & 127) == 41 && (top >> 7) == 41, "outer: building top " + (top & 127) + " / " + (top >> 7));
        // 読み込まれていない所だけの列は、地面も日なたも一番上 (何もしない)
        java.util.Arrays.fill(outerHeights, SunShadowService.NO_HEIGHT);
        int none = SunShadowService.outerColumns(outerHeights, 0, new double[]{s, s, 0})[0];
        check((none & 127) == SunShadowService.FAR_HEIGHT - 1, "outer: unloaded column is ignored " + (none & 127));
        System.out.println("SunShadowServiceTest OK");
    }
}
