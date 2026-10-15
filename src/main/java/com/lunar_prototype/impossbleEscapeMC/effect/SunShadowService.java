package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバーで計算した太陽の影を、リソースパックのシェーダーへ送る。レイド中 (生きている間) だけ送る ({@link #setInRaid}。
 * ScreenEffectService が知らせる)。/shaderdebug sun で付け外しを決められる (確認用。{@link #toggle}、{@link #clearOverride} で自動に戻す)。
 * <p>
 * プレイヤーの周りの {@link #SPAN_X} x {@link #SPAN_Y} x {@link #SPAN_Z} ブロックの空気のブロックごとに、太陽の向きへブロックをたどって
 * 日なた・日陰を決め (屋外の日なた・屋外の日陰・室内の日なた・室内の日陰。室内の日陰はシェーダーが控えめに暗くする)、2 段の格子にする:
 * 近く ({@link #W} x {@link #H} x {@link #D} ブロック) はブロックごと、その外 (全体) は 2 x 2 x 2 ブロックをまとめた粗い格子。
 * 鉄格子・葉・柵などは光を一部だけ通す (通る割合を掛けていき、残った割合でブロックごとに日なた・日陰を振り分ける。
 * シェーダーが周りのブロックと混ぜるので、木漏れ日のような薄い影になる)。
 * データの画素 (リソースパックの iem_control.glsl の IEM_DATA_*・iem_sun.glsl。並びはそちらと同じ) として、
 * 本人にだけ見えるテキストディスプレイの文字で送る。ポストエフェクト (light.fsh・grade.fsh・shafts.fsh) が
 * 画面の画素の位置からこの格子を読んで日陰を暗くする。シェーダーは正しく届いたデータをフレームをまたいで覚えておく (ちらつかないように) ので、
 * 止める時は「影なし」(ヘッダーの印のマスを 0 にしたもの) を送ってから消す。
 * <p>
 * 光源ブロックの影 ({@link #blockGrid}): 近くの {@link #BW} x {@link #BH} x {@link #BD} ブロックの空気ごとに、周りの光源ブロック
 * (たいまつ・ランタン・グロウストーンなど。ブロックの光の山の頂点) から光が直接届くかをたどり、バニラの光 (壁の角を回り込んで届く) と比べた
 * 明るさの倍率を 4 段階で送る。光源から見えない所 (木箱の裏・柱の陰) は暗く、斜めに見える所は少し明るくなる。夜は太陽の格子は送らず、これだけ送る。
 * <p>
 * 太陽の向きはクライアントの空と同じ: 26.3 のタイムライン (data/minecraft/timeline/day.json の visual/sun_angle) の角度で、
 * 正午 (6000) に 0° から 1 日で 360°、3 次ベジェの緩急。向きは (-sin 角度, cos 角度, 0) (SkyRenderer: Y で -90°、X で角度)
 */
public final class SunShadowService {

    // ---- データの文字 (ShaderDebugCommand・tools/data_font.py と同じ) ----
    private static final Key DATA_FONT = Key.key("iem", "data");
    private static final TextColor DATA_COLOR = TextColor.color(212, 122, 1);
    private static final int DATA_BASE = 0x4E00;
    private static final char DATA_GAP = '　';
    private static final int DATA_PITCH = 8;
    private static final float DATA_SCALE = 1f / (256f * DATA_PITCH * 0.025f);
    /** データの画素の数 (iem_control.glsl の IEM_DATA_CAPACITY) */
    private static final int DATA_CAPACITY = 512 * 32;
    /** テキストの部品 1 つの文字の数 (ネットワークでは部品ごとに 65535 バイトまでの文字列 (NBT) になる。1 文字 3 バイト) */
    private static final int CELLS_PER_PART = 4096;

    // ---- 太陽の影の格子 (iem_sun.glsl と同じ) ----
    private static final int MAGIC = 0x2B6C;
    private static final int HEADER = 3, PAYLOAD = 22, PER_VALUE = 7;
    /** 近くの格子 (ブロックごと) の大きさ */
    public static final int W = 48, H = 24, D = 48;
    /** 外の粗い格子: 1 マスが FAR_SCALE x FAR_SCALE x FAR_SCALE ブロック、FW x FH x FD マス */
    static final int FAR_SCALE = 2, FW = 44, FH = 22, FD = 44;
    /** 光源ブロックの影の格子 (ブロックごと) の大きさ */
    public static final int BW = 32, BH = 16, BD = 32;
    /** 光源ブロックの影の倍率 (送る値 0〜3。iem_sun.glsl の IEM_BLOCK_FACTORS と同じ) */
    static final double[] BLOCK_FACTORS = {1.0, 0.45, 0.72, 1.3};
    /** 光源ブロックをたどる数の上限 (格子の真ん中に近い順) */
    private static final int MAX_LIGHTS = 96;
    /** 計算する範囲 (ブロック。粗い格子の範囲。近くの格子はこの中) */
    public static final int SPAN_X = FW * FAR_SCALE, SPAN_Y = FH * FAR_SCALE, SPAN_Z = FD * FAR_SCALE;
    /** ブロックごとの計算の状態 (送る状態 0〜3 のほかに、固体) */
    static final byte SOLID = 4;
    /** 太陽へたどる長さ (ブロック) */
    private static final int REACH = 48;
    /** この空の光より暗い空気のブロックは室内 (日が差し込めば室内の日なた) */
    private static final int OUTDOOR_SKY_LIGHT = 12;
    /**
     * 日の当たらない空気のうち、空の光がこれ以上の所は「屋外の日陰」、ほかは「室内の日陰」(状態 0)。
     * 室内の奥の暗さは光の表 (空の光が 1 ブロックごとに下がる) がもう表しているので、シェーダーは室内の日陰を控えめに暗くする
     * (ブロックごとに振り分けて薄くすると、まだら模様に見える)
     */
    private static final int SHADE_SKY_LIGHT = 14;
    /** 太陽がこれより低い (向きの y) 時は送らない */
    private static final double MIN_SUN_Y = 0.08;
    /** 一部だけ光を通すブロックを通った後、これより暗ければ日陰 (先をたどらない) */
    private static final double MIN_TRANSMIT = 0.02;
    /** 太陽の角度がこれだけ動いたら作り直す (度) */
    private static final double ANGLE_STEP = 0.75;
    private static final long REFRESH_MILLIS = 30_000;

    private static final class State {
        TextDisplay display;
        int ox = Integer.MIN_VALUE, oy, oz;   // 近くの格子の原点
        int fx, fy, fz;                       // 粗い格子の原点
        int bx, by, bz;                       // 光源ブロックの影の格子の原点
        double angle = Double.NaN;
        long computedAt;
        boolean computing;
    }

    private static final Map<UUID, State> states = new ConcurrentHashMap<>();
    /** /shaderdebug sun で決めた付け外し (無ければレイド中だけ自動) */
    private static final Map<UUID, Boolean> overrides = new ConcurrentHashMap<>();
    private static BukkitTask task;
    /** 種類ごとの光の通り方 (0 = さえぎる、255 = 通す。間は通る割合) */
    private static byte[] transmitTable;

    private SunShadowService() {
    }

    /**
     * レイド中 (生きていて画作りがある間) かどうかを知らせる (ScreenEffectService が毎 tick)。レイド中だけ送り、外れたら止める
     * (死亡中・観戦中は画作りが無く、データの画素を隠すものが無いため)。/shaderdebug sun で決めた時はそちらに従う
     */
    public static void setInRaid(Player player, boolean inRaid) {
        Boolean override = overrides.get(player.getUniqueId());
        boolean want = override != null ? override : inRaid;
        if (want != isEnabled(player)) setEnabled(player, want);
    }

    /** 確認用の付け外し (/shaderdebug sun)。レイドに関係なくこの状態にする ({@link #clearOverride} まで)。付けたら true */
    public static boolean toggle(Player player) {
        boolean on = !isEnabled(player);
        overrides.put(player.getUniqueId(), on);
        setEnabled(player, on);
        return on;
    }

    /** 確認用の付け外しをやめて、レイド中だけ自動で送るのに戻す */
    public static void clearOverride(Player player) {
        overrides.remove(player.getUniqueId());
    }

    /** 退出した時 */
    public static void forget(UUID playerId) {
        overrides.remove(playerId);
    }

    private static void setEnabled(Player player, boolean on) {
        if (!on) {
            State old = states.remove(player.getUniqueId());
            if (old == null) return;
            // 覚えている影を消させてから (1 秒後に) 表示を消す
            setData(old, null, null, null);
            TextDisplay display = old.display;
            if (display != null) Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), display::remove, 20L);
            return;
        }
        states.putIfAbsent(player.getUniqueId(), new State());
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), SunShadowService::tick, 1L, 10L);
    }

    public static boolean isEnabled(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    private static void tick() {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null) {
                if (state.display != null) state.display.remove();
                states.remove(entry.getKey());
                continue;
            }
            update(player, state);
        }
    }

    private static void update(Player player, State state) {
        Location at = player.getLocation();
        World world = player.getWorld();
        ensureDisplay(player, state, at);

        double angle = sunAngle(world.getTime());
        double[] sun = sunDirection(angle);
        // 近くの格子は 8 ブロック、粗い格子は 16 ブロックごとに動かす (近くの格子はいつも粗い格子の範囲の中)
        int ox = Math.floorDiv(at.getBlockX(), 8) * 8 - 20;
        int oy = Math.floorDiv(at.getBlockY(), 8) * 8 - 8;
        int oz = Math.floorDiv(at.getBlockZ(), 8) * 8 - 20;
        int fx = Math.floorDiv(at.getBlockX(), 16) * 16 - 36;
        int fy = Math.floorDiv(at.getBlockY(), 16) * 16 - 14;
        int fz = Math.floorDiv(at.getBlockZ(), 16) * 16 - 36;
        // 光源ブロックの影の格子も 8 ブロックごと (粗い格子の範囲の中、横と上下に 10 ブロック以上の余白。そこにある光源もたどる)
        int bx = Math.floorDiv(at.getBlockX(), 8) * 8 - 12;
        int by = Math.floorDiv(at.getBlockY(), 8) * 8 - 4;
        int bz = Math.floorDiv(at.getBlockZ(), 8) * 8 - 12;
        boolean moved = ox != state.ox || oy != state.oy || oz != state.oz;
        boolean turned = Double.isNaN(state.angle) || Math.abs(angle - state.angle) > ANGLE_STEP;
        boolean stale = System.currentTimeMillis() - state.computedAt > REFRESH_MILLIS;
        if (state.computing || !(moved || turned || stale)) return;

        state.ox = ox;
        state.oy = oy;
        state.oz = oz;
        state.fx = fx;
        state.fy = fy;
        state.fz = fz;
        state.bx = bx;
        state.by = by;
        state.bz = bz;
        state.angle = angle;
        state.computedAt = System.currentTimeMillis();
        // 夜・朝夕は太陽の格子を送らない (光源ブロックの影だけ)
        boolean day = sun[1] >= MIN_SUN_Y;
        double daylight = daylight(sun[1]);
        // チャンクの写しだけをメインスレッドで取り、ブロックを読むのと計算は別スレッドで
        Snapshot.Area area = Snapshot.area(world, fx, fy, fz, day ? sun : new double[]{0, 1, 0});
        Map<Long, ChunkSnapshot> chunks = Snapshot.chunks(world, area);
        state.computing = true;
        Bukkit.getScheduler().runTaskAsynchronously(ImpossbleEscapeMC.getInstance(), () -> {
            Snapshot snapshot = Snapshot.of(chunks, area, fx, fy, fz);
            int[] near = new int[0], far = new int[0];
            if (day) {
                byte[] fine = fineStates(snapshot, fx, fy, fz, sun);
                near = packNear(fine, ox - fx, oy - fy, oz - fz);
                far = packFar(fine);
            }
            int[] block = blockGrid(snapshot, fx, fy, fz, bx - fx, by - fy, bz - fz, daylight);
            int[] sendNear = near, sendFar = far;
            Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> {
                state.computing = false;
                if (states.get(player.getUniqueId()) == state) setData(state, sendNear, sendFar, block);
            });
        });
    }

    // ---- 太陽 ----

    /** 太陽の角度 (度)。タイムラインの visual/sun_angle と同じ */
    static double sunAngle(long dayTime) {
        double progress = Math.floorMod(dayTime - 6000L, 24000L) / 24000.0;
        return cubicBezier(progress, 0.362, 0.241, 0.638, 0.759) * 360.0;
    }

    /** 太陽の向き (ワールド、長さ 1) */
    static double[] sunDirection(double angleDegrees) {
        double a = Math.toRadians(angleDegrees);
        return new double[]{-Math.sin(a), Math.cos(a), 0.0};
    }

    /** CSS と同じ cubic-bezier(x1, y1, x2, y2) の緩急 (x から y) */
    static double cubicBezier(double x, double x1, double y1, double x2, double y2) {
        double lo = 0, hi = 1, t = x;
        for (int i = 0; i < 40; i++) {
            t = (lo + hi) / 2;
            double bx = bezier(t, x1, x2);
            if (bx < x) lo = t;
            else hi = t;
        }
        return bezier(t, y1, y2);
    }

    private static double bezier(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    // ---- 計算 (別スレッド) ----

    /** 計算に使うブロックの写し。transmit は光の通り方 ({@link #transmitTable})、skyLight・blockLight は計算する範囲の空の光・ブロックの光、
     * top はさえぎるブロックのある一番上の y (これより上へ出た光はもうさえぎられない) */
    record Snapshot(int x0, int y0, int z0, int nx, int ny, int nz, byte[] transmit, byte[] skyLight, byte[] blockLight, int top) {

        /** 写す範囲: 計算する範囲と、そこから太陽の向きへたどる分 */
        record Area(int x0, int y0, int z0, int x1, int y1, int z1) {
        }

        static Area area(World world, int fx, int fy, int fz, double[] sun) {
            int x0 = fx + (int) Math.floor(Math.min(0, sun[0] * REACH)) - 1;
            int x1 = fx + SPAN_X + (int) Math.ceil(Math.max(0, sun[0] * REACH)) + 1;
            int z0 = fz + (int) Math.floor(Math.min(0, sun[2] * REACH)) - 1;
            int z1 = fz + SPAN_Z + (int) Math.ceil(Math.max(0, sun[2] * REACH)) + 1;
            int y0 = Math.max(world.getMinHeight(), fy);
            int y1 = Math.min(world.getMaxHeight(), fy + SPAN_Y + (int) Math.ceil(sun[1] * REACH) + 1);
            return new Area(x0, y0, z0, x1, Math.max(y0 + 1, y1), z1);
        }

        /** チャンクの写し (メインスレッドで取る。読み込まれていないチャンクは無し = 空気) */
        static Map<Long, ChunkSnapshot> chunks(World world, Area a) {
            transmitTable();
            Map<Long, ChunkSnapshot> chunks = new HashMap<>();
            for (int cx = a.x0() >> 4; cx <= (a.x1() - 1) >> 4; cx++) {
                for (int cz = a.z0() >> 4; cz <= (a.z1() - 1) >> 4; cz++) {
                    if (world.isChunkLoaded(cx, cz)) chunks.put(key(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
                }
            }
            return chunks;
        }

        /** チャンクの写しからブロックを読む (別スレッドでよい) */
        static Snapshot of(Map<Long, ChunkSnapshot> chunks, Area a, int fx, int fy, int fz) {
            int nx = a.x1() - a.x0(), ny = a.y1() - a.y0(), nz = a.z1() - a.z0();
            byte[] transmit = new byte[nx * ny * nz];
            byte[] sky = new byte[SPAN_X * SPAN_Y * SPAN_Z];
            byte[] block = new byte[SPAN_X * SPAN_Y * SPAN_Z];
            byte[] table = transmitTable();
            int top = Integer.MIN_VALUE;
            for (int x = a.x0(); x < a.x1(); x++) {
                for (int z = a.z0(); z < a.z1(); z++) {
                    ChunkSnapshot chunk = chunks.get(key(x >> 4, z >> 4));
                    if (chunk == null) {
                        for (int y = a.y0(); y < a.y1(); y++) transmit[((y - a.y0()) * nz + (z - a.z0())) * nx + (x - a.x0())] = (byte) 255;
                        continue;
                    }
                    boolean inSpan = x >= fx && x < fx + SPAN_X && z >= fz && z < fz + SPAN_Z;
                    for (int y = a.y0(); y < a.y1(); y++) {
                        byte t = table[chunk.getBlockType(x & 15, y, z & 15).ordinal()];
                        transmit[((y - a.y0()) * nz + (z - a.z0())) * nx + (x - a.x0())] = t;
                        if (t != (byte) 255 && y > top) top = y;
                        if (inSpan && y >= fy && y < fy + SPAN_Y) {
                            int i = x - fx + SPAN_X * ((z - fz) + SPAN_Z * (y - fy));
                            sky[i] = (byte) chunk.getBlockSkyLight(x & 15, y, z & 15);
                            block[i] = (byte) chunk.getBlockEmittedLight(x & 15, y, z & 15);
                        }
                    }
                }
            }
            return new Snapshot(a.x0(), a.y0(), a.z0(), nx, ny, nz, transmit, sky, block, top);
        }

        /** 光の通る割合 (0〜255。範囲の外は 255) */
        int transmit(int x, int y, int z) {
            int ix = x - x0, iy = y - y0, iz = z - z0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) return 255;
            return transmit[(iy * nz + iz) * nx + ix] & 0xFF;
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /**
     * 種類ごとの光の通り方 (0 = さえぎる、255 = 通す、間は通る割合 x 255)。
     * さえぎる: 光を通さないブロック、当たり判定のあるもの (ハーフブロック・階段・扉など)。
     * 一部だけ通す: 鉄格子・銅の格子・銅の網 (半分)、葉、柵・フェンスゲート、塀、鎖、ランタン、はしご、足場、クモの巣、避雷針。
     * 通す: ガラス・板ガラス・氷、当たり判定の無いもの (草・花・つた・松明など)
     */
    static synchronized byte[] transmitTable() {
        if (transmitTable != null) return transmitTable;
        Material[] all = Material.values();
        byte[] table = new byte[all.length];
        for (Material m : all) {
            double t = 1.0;
            if (m.isBlock() && !m.isAir()) {
                String name = m.name();
                boolean glassy = name.contains("GLASS") || name.endsWith("ICE");
                if (name.endsWith("_BARS") || name.endsWith("COPPER_GRATE")) t = 0.5;
                else if (Tag.LEAVES.isTagged(m)) t = 0.35;
                else if (Tag.WALLS.isTagged(m)) t = 0.4;
                else if (Tag.FENCES.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) t = 0.7;
                else if (m == Material.SCAFFOLDING || m == Material.COBWEB) t = 0.6;
                else if (m == Material.LADDER) t = 0.8;
                else if (name.endsWith("_CHAIN") || (name.endsWith("LANTERN") && m != Material.JACK_O_LANTERN && m != Material.SEA_LANTERN)) t = 0.85;
                else if (m == Material.END_ROD || name.endsWith("LIGHTNING_ROD")) t = 0.9;
                else if (m.isOccluding() || (m.isSolid() && !glassy)) t = 0.0;
            }
            table[m.ordinal()] = (byte) Math.round(t * 255);
        }
        transmitTable = table;
        return table;
    }

    /** ブロックごとに日なた・日陰を振り分けるしきい値 (2 x 2 x 2 ブロックで 8 段。通る割合が半分なら市松模様) */
    private static final int[] DITHER_RANK = {0, 6, 5, 3, 4, 2, 1, 7};

    static double ditherThreshold(int x, int y, int z) {
        return (DITHER_RANK[(x & 1) | (y & 1) << 1 | (z & 1) << 2] + 0.5) / 8.0;
    }

    /**
     * 計算する範囲 (原点 fx, fy, fz、{@link #SPAN_X} x {@link #SPAN_Y} x {@link #SPAN_Z} ブロック) のブロックごとの状態
     * (0 = 室内の日陰、1 = 屋外の日陰、2 = 屋外の日なた、3 = 室内の日なた (窓などから差し込む日)、{@link #SOLID} = 光をさえぎるブロック)。
     * 並びは x + SPAN_X x (z + SPAN_Z x y)
     */
    static byte[] fineStates(Snapshot s, int fx, int fy, int fz, double[] sun) {
        byte[] fine = new byte[SPAN_X * SPAN_Y * SPAN_Z];
        for (int y = 0; y < SPAN_Y; y++) {
            for (int z = 0; z < SPAN_Z; z++) {
                for (int x = 0; x < SPAN_X; x++) {
                    int index = x + SPAN_X * (z + SPAN_Z * y);
                    int wx = fx + x, wy = fy + y, wz = fz + z;
                    if (s.transmit(wx, wy, wz) == 0) {
                        fine[index] = SOLID;
                        continue;
                    }
                    int sky = s.skyLight()[index];
                    if (sunTransmit(s, wx + 0.5, wy + 0.5, wz + 0.5, sun) > ditherThreshold(wx, wy, wz)) {
                        fine[index] = (byte) (sky >= OUTDOOR_SKY_LIGHT ? 2 : 3);
                    } else {
                        fine[index] = (byte) (sky >= SHADE_SKY_LIGHT ? 1 : 0);
                    }
                }
            }
        }
        return fine;
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** 固体のブロックの状態を、隣の空気で同じ数の時にどれを選ぶか (前ほど優先: 屋外の日陰、室内の日陰、室内の日なた、屋外の日なた) */
    private static final int[] SOLID_PREFERENCE = {1, 0, 3, 2};

    /**
     * 固体のブロックの送る状態: 隣 (6 方向) の空気の状態で一番多いもの (同じ数なら SOLID_PREFERENCE の順)。隣に空気が無ければ 0。
     * シェーダーは面の前の周り 8 ブロックを混ぜるので、固体に周りと違う値があると、出っ張り (トラップドア・ハーフブロックなど) の近くだけ
     * 影が薄くなったり濃くなったりして浮いて見える
     */
    static int solidState(byte[] fine, int x, int y, int z) {
        int[] count = new int[4];
        int air = 0;
        for (int[] f : FACES) {
            int nx = x + f[0], ny = y + f[1], nz = z + f[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= SPAN_X || ny >= SPAN_Y || nz >= SPAN_Z) continue;
            int state = fine[nx + SPAN_X * (nz + SPAN_Z * ny)];
            if (state == SOLID) continue;
            count[state]++;
            air++;
        }
        if (air == 0) return 0;
        int best = SOLID_PREFERENCE[0];
        for (int state : SOLID_PREFERENCE) if (count[state] > count[best]) best = state;
        return best;
    }

    /** 近くの格子 (計算する範囲の中の dx, dy, dz から {@link #W} x {@link #H} x {@link #D} ブロック) を 7 個ずつ 14bit に詰める。
     * 固体のブロックは {@link #solidState}。並びは x + W x (z + D x y) */
    static int[] packNear(byte[] fine, int dx, int dy, int dz) {
        int[] values = new int[(W * H * D + PER_VALUE - 1) / PER_VALUE];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    int state = fine[(dx + x) + SPAN_X * ((dz + z) + SPAN_Z * (dy + y))];
                    if (state == SOLID) state = solidState(fine, dx + x, dy + y, dz + z);
                    int index = x + W * (z + D * y);
                    values[index / PER_VALUE] |= state << ((index % PER_VALUE) * 2);
                }
            }
        }
        return values;
    }

    /**
     * 粗い格子: 2 x 2 x 2 ブロックの空気の状態で一番多いもの (同じ数なら SOLID_PREFERENCE の順。1 ブロックの壁をまたぐマスでも、
     * 室内の空気が多ければ室内の日陰になり、外の日陰が部屋の中へ漏れない)。全部固体のマスは中のブロックの固体の状態から。
     * 並びは x + FW x (z + FD x y)
     */
    static int[] packFar(byte[] fine) {
        int[] values = new int[(FW * FH * FD + PER_VALUE - 1) / PER_VALUE];
        for (int y = 0; y < FH; y++) {
            for (int z = 0; z < FD; z++) {
                for (int x = 0; x < FW; x++) {
                    int air = 0;
                    int[] count = new int[4];
                    for (int i = 0; i < 8; i++) {
                        int bx = x * FAR_SCALE + (i & 1), by = y * FAR_SCALE + (i >> 1 & 1), bz = z * FAR_SCALE + (i >> 2);
                        int state = fine[bx + SPAN_X * (bz + SPAN_Z * by)];
                        if (state == SOLID) continue;
                        air++;
                        count[state]++;
                    }
                    if (air == 0) {
                        // 全部固体のマス: 中のブロックの固体の状態 (solidState) から (近くの格子と同じ理由)
                        for (int i = 0; i < 8; i++) {
                            int bx = x * FAR_SCALE + (i & 1), by = y * FAR_SCALE + (i >> 1 & 1), bz = z * FAR_SCALE + (i >> 2);
                            count[solidState(fine, bx, by, bz)]++;
                        }
                    }
                    int state = SOLID_PREFERENCE[0];
                    for (int candidate : SOLID_PREFERENCE) if (count[candidate] > count[state]) state = candidate;
                    if (state == 0) continue;
                    int index = x + FW * (z + FD * y);
                    values[index / PER_VALUE] |= state << ((index % PER_VALUE) * 2);
                }
            }
        }
        return values;
    }

    /** ブロックの真ん中から太陽の向きへ、ブロックをひとつずつたどり (Amanatides-Woo)、届く光の割合を返す (0〜1) */
    static double sunTransmit(Snapshot s, double px, double py, double pz, double[] d) {
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int sx = d[0] > 0 ? 1 : -1, sy = d[1] > 0 ? 1 : -1, sz = d[2] > 0 ? 1 : -1;
        double tdx = d[0] != 0 ? Math.abs(1 / d[0]) : Double.MAX_VALUE;
        double tdy = d[1] != 0 ? Math.abs(1 / d[1]) : Double.MAX_VALUE;
        double tdz = d[2] != 0 ? Math.abs(1 / d[2]) : Double.MAX_VALUE;
        double tx = d[0] != 0 ? ((sx > 0 ? x + 1 - px : px - x) * tdx) : Double.MAX_VALUE;
        double ty = d[1] != 0 ? ((sy > 0 ? y + 1 - py : py - y) * tdy) : Double.MAX_VALUE;
        double tz = d[2] != 0 ? ((sz > 0 ? z + 1 - pz : pz - z) * tdz) : Double.MAX_VALUE;
        int maxY = Math.min(s.y0() + s.ny(), s.top() + 1);
        double light = 1.0;
        while (true) {
            double t;
            if (tx < ty && tx < tz) {
                t = tx;
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                t = ty;
                y += sy;
                ty += tdy;
            } else {
                t = tz;
                z += sz;
                tz += tdz;
            }
            if (t > REACH || y >= maxY) return light;
            int transmit = s.transmit(x, y, z);
            if (transmit == 255) continue;
            light *= transmit / 255.0;
            if (light < MIN_TRANSMIT) return 0.0;
        }
    }

    // ---- 光源ブロックの影 ----

    /** 夜の空の明るさ (光の表の空の光の強さ。昼 1、夜は 0.2 ほど)。太陽の向きの y から */
    static double daylight(double sunY) {
        double t = Math.clamp((sunY + 0.1) / 0.4, 0.0, 1.0);
        return 0.2 + 0.8 * t * t * (3 - 2 * t);
    }

    /** バニラの光の表の明るさの曲線 (レベル 0〜15) */
    static double lightCurve(double level) {
        double l = Math.clamp(level / 15.0, 0.0, 1.0);
        return l / (4.0 - 3.0 * l);
    }

    /** 空の光の明るさ (リソースパックの core/lightmap.fsh の iem_sky_brightness と同じ曲線) */
    static double skyCurve(int level) {
        double l = level / 15.0;
        return lightCurve(level) * 0.4 + Math.pow(l, 1.4) * 0.6;
    }

    /** 光源ブロック: ブロックの光が周り 6 方向のどれよりも小さくない所 (光は 1 ブロックごとに 1 以上減るので、山の頂点は光源)。
     * 計算する範囲の縁は、外の明るい所が見えないので除く。{x, y, z, レベル} (計算する範囲の座標) */
    static List<int[]> lightSources(Snapshot s) {
        List<int[]> lights = new ArrayList<>();
        byte[] light = s.blockLight();
        for (int y = 1; y < SPAN_Y - 1; y++) {
            for (int z = 1; z < SPAN_Z - 1; z++) {
                for (int x = 1; x < SPAN_X - 1; x++) {
                    int level = light[x + SPAN_X * (z + SPAN_Z * y)];
                    if (level <= 1) continue;
                    boolean top = true;
                    for (int[] f : FACES) {
                        if (light[(x + f[0]) + SPAN_X * ((z + f[2]) + SPAN_Z * (y + f[1]))] > level) {
                            top = false;
                            break;
                        }
                    }
                    if (top) lights.add(new int[]{x, y, z, level});
                }
            }
        }
        return lights;
    }

    /** ブロックの真ん中 a から b まで、通ったブロックの光の通り方を掛けた割合 (a と b のブロック自身は数えない) */
    static double transmitBetween(Snapshot s, int ax, int ay, int az, int bx, int by, int bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return 1.0;
        double[] d = {dx / len, dy / len, dz / len};
        int x = ax, y = ay, z = az;
        int sx = d[0] > 0 ? 1 : -1, sy = d[1] > 0 ? 1 : -1, sz = d[2] > 0 ? 1 : -1;
        double tdx = d[0] != 0 ? Math.abs(1 / d[0]) : Double.MAX_VALUE;
        double tdy = d[1] != 0 ? Math.abs(1 / d[1]) : Double.MAX_VALUE;
        double tdz = d[2] != 0 ? Math.abs(1 / d[2]) : Double.MAX_VALUE;
        double tx = d[0] != 0 ? 0.5 * tdx : Double.MAX_VALUE;
        double ty = d[1] != 0 ? 0.5 * tdy : Double.MAX_VALUE;
        double tz = d[2] != 0 ? 0.5 * tdz : Double.MAX_VALUE;
        double light = 1.0;
        while (true) {
            if (tx < ty && tx < tz) {
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
            if (x == bx && y == by && z == bz) return light;
            if (Math.abs(x - ax) > Math.abs(bx - ax) + 1 || Math.abs(y - ay) > Math.abs(by - ay) + 1 || Math.abs(z - az) > Math.abs(bz - az) + 1) {
                return light;   // 角をかすめて目的のブロックを飛ばした (もう着いている)
            }
            int transmit = s.transmit(x, y, z);
            if (transmit == 255) continue;
            light *= transmit / 255.0;
            if (light < MIN_TRANSMIT) return 0.0;
        }
    }

    /** 光源ブロックの明るさの倍率を送る値 (0〜3) にする: BLOCK_FACTORS の近い方 (比で) */
    static int blockClass(double ratio) {
        if (ratio < 0.57) return 1;
        if (ratio < 0.85) return 2;
        if (ratio > 1.14) return 3;
        return 0;
    }

    /**
     * 光源ブロックの影の格子 (計算する範囲の中の dx, dy, dz から {@link #BW} x {@link #BH} x {@link #BD} ブロック) を 7 個ずつ 14bit に詰める。
     * 空気のブロックごとに、光源から直接届く明るさ (光源のレベル - まっすぐの距離 を光の表の曲線にし、間のブロックの光の通り方を掛け、多い方) と、
     * 跳ね返りで回り込む分 (バニラの光 - 4) の多い方を、バニラの光と比べる。空の光の分 (daylight は今の空の明るさ) も足して比べるので、
     * 昼の屋外ではほとんど変わらない。固体のブロックは隣の空気の値の多い方 (シェーダーが混ぜる時に浮かないように)。並びは x + BW x (z + BD x y)
     */
    static int[] blockGrid(Snapshot s, int fx, int fy, int fz, int dx, int dy, int dz, double daylight) {
        List<int[]> lights = lightSources(s);
        double cx = dx + BW / 2.0, cy = dy + BH / 2.0, cz = dz + BD / 2.0;
        lights.removeIf(l -> l[0] < dx - l[3] || l[0] >= dx + BW + l[3] || l[1] < dy - l[3] || l[1] >= dy + BH + l[3]
                || l[2] < dz - l[3] || l[2] >= dz + BD + l[3]);
        lights.sort(java.util.Comparator.comparingDouble(l -> (l[0] - cx) * (l[0] - cx) + (l[1] - cy) * (l[1] - cy) + (l[2] - cz) * (l[2] - cz)));
        if (lights.size() > MAX_LIGHTS) lights = lights.subList(0, MAX_LIGHTS);
        byte[] cls = new byte[BW * BH * BD];
        boolean[] solid = new boolean[BW * BH * BD];
        for (int y = 0; y < BH; y++) {
            for (int z = 0; z < BD; z++) {
                for (int x = 0; x < BW; x++) {
                    int i = x + BW * (z + BD * y);
                    int px = dx + x, py = dy + y, pz = dz + z;
                    if (s.transmit(fx + px, fy + py, fz + pz) == 0) {
                        solid[i] = true;
                        continue;
                    }
                    int span = px + SPAN_X * (pz + SPAN_Z * py);
                    int vanilla = s.blockLight()[span];
                    if (vanilla <= 0) continue;
                    double direct = lightCurve(Math.max(vanilla - 4, 0));
                    for (int[] l : lights) {
                        double ex = l[0] - px, ey = l[1] - py, ez = l[2] - pz;
                        double reach = l[3] - Math.sqrt(ex * ex + ey * ey + ez * ez);
                        if (reach <= 0) continue;
                        double bright = lightCurve(reach);
                        if (bright <= direct) continue;
                        double t = transmitBetween(s, fx + px, fy + py, fz + pz, fx + l[0], fy + l[1], fz + l[2]);
                        direct = Math.max(direct, bright * t);
                    }
                    double sky = skyCurve(s.skyLight()[span]) * daylight;
                    double ambient = 0.04;
                    double want = Math.min(1.0, ambient + sky + direct);
                    double have = Math.min(1.0, ambient + sky + lightCurve(vanilla));
                    cls[i] = (byte) blockClass(want / have);
                }
            }
        }
        int[] values = new int[(BW * BH * BD + PER_VALUE - 1) / PER_VALUE];
        for (int y = 0; y < BH; y++) {
            for (int z = 0; z < BD; z++) {
                for (int x = 0; x < BW; x++) {
                    int i = x + BW * (z + BD * y);
                    int value = cls[i];
                    if (solid[i]) {
                        int[] count = new int[4];
                        for (int[] f : FACES) {
                            int nx = x + f[0], ny = y + f[1], nz = z + f[2];
                            if (nx < 0 || ny < 0 || nz < 0 || nx >= BW || ny >= BH || nz >= BD) continue;
                            int n = nx + BW * (nz + BD * ny);
                            if (!solid[n]) count[cls[n]]++;
                        }
                        value = 0;
                        for (int c = 1; c < 4; c++) if (count[c] > count[value]) value = c;
                    }
                    values[i / PER_VALUE] |= value << ((i % PER_VALUE) * 2);
                }
            }
        }
        return values;
    }

    // ---- 送る ----

    /** 格子の値をデータの文字にしてテキストディスプレイに書く (block が null なら「影なし」: ヘッダーの印のマスを 0 にして、覚えている影を消させる)。
     * 中身は PAYLOAD 番から近くの格子、粗い格子、光源ブロックの影の格子 (太陽の格子は夜は無し = 大きさ 0) */
    private static void setData(State state, int[] near, int[] far, int[] block) {
        if (state.display == null) return;
        if (block == null) {
            near = new int[0];
            far = new int[0];
            block = new int[0];
        }
        int cells = Math.min(DATA_CAPACITY, PAYLOAD + near.length + far.length + block.length);
        int[] data = new int[cells];
        data[HEADER] = block.length > 0 ? MAGIC : 0;
        data[HEADER + 1] = state.ox & 1023;
        data[HEADER + 2] = state.oy & 1023;
        data[HEADER + 3] = state.oz & 1023;
        data[HEADER + 4] = near.length > 0 ? W : 0;
        data[HEADER + 5] = H;
        data[HEADER + 6] = D;
        data[HEADER + 7] = state.fx & 1023;
        data[HEADER + 8] = state.fy & 1023;
        data[HEADER + 9] = state.fz & 1023;
        data[HEADER + 10] = far.length > 0 ? FW : 0;
        data[HEADER + 11] = FH;
        data[HEADER + 12] = FD;
        data[HEADER + 13] = state.bx & 1023;
        data[HEADER + 14] = state.by & 1023;
        data[HEADER + 15] = state.bz & 1023;
        data[HEADER + 16] = block.length > 0 ? BW : 0;
        data[HEADER + 17] = BH;
        data[HEADER + 18] = BD;
        int at = PAYLOAD;
        for (int[] part : new int[][]{near, far, block}) {
            int n = Math.min(part.length, cells - at);
            System.arraycopy(part, 0, data, at, n);
            at += n;
        }
        // 部品に分ける (1 つの文字列が長すぎると送れない)
        Component text = Component.empty();
        for (int start = 0; start < cells; start += CELLS_PER_PART) {
            int end = Math.min(cells, start + CELLS_PER_PART);
            StringBuilder part = new StringBuilder((end - start) * 2);
            for (int i = start; i < end; i++) part.append((char) (DATA_BASE + (data[i] & 0x3FFF))).append(DATA_GAP);
            text = text.append(Component.text(part.toString()));
        }
        state.display.text(text.color(DATA_COLOR).font(DATA_FONT));
        state.display.setTransformation(transformation(cells));
    }

    /** 0 番の値の文字の左の端が置いた所に来るよう、(幅 / 2 - 1) だけ +x にずらす (ShaderDebugCommand と同じ) */
    private static Transformation transformation(int cells) {
        float shift = (cells * DATA_PITCH / 2f - 1f) * 0.025f * DATA_SCALE;
        return new Transformation(new Vector3f(shift, 0f, 0f), new Quaternionf(),
                new Vector3f(DATA_SCALE, DATA_SCALE, DATA_SCALE), new Quaternionf());
    }

    /** テキストディスプレイを、x = 64 の倍数 (一番近い所) で、プレイヤーの近くに置いておく */
    private static void ensureDisplay(Player player, State state, Location at) {
        double anchorX = Math.round(at.getX() / 64.0) * 64.0;
        Location want = new Location(at.getWorld(), anchorX, at.getY() + 1.5, at.getZ(), 0f, 0f);
        TextDisplay display = state.display;
        if (display == null || !display.isValid() || display.getWorld() != at.getWorld()) {
            if (display != null) display.remove();
            state.display = at.getWorld().spawn(want, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setSeeThrough(true);
                d.setShadowed(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setLineWidth(1_000_000);
                d.setAlignment(TextDisplay.TextAlignment.LEFT);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(4f);
            });
            player.showEntity(ImpossbleEscapeMC.getInstance(), state.display);
            state.ox = Integer.MIN_VALUE;   // 作り直す
            return;
        }
        Location now = display.getLocation();
        if (now.getX() != anchorX || Math.abs(now.getY() - want.getY()) > 16 || Math.abs(now.getZ() - want.getZ()) > 16) {
            display.teleport(want);
        }
    }
}
