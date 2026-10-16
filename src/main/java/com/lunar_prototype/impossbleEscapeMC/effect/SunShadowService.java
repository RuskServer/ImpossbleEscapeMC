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
 * サーバーで計算した光を、リソースパックのシェーダーへ送る。レイド中 (生きている間) だけ送る ({@link #setInRaid}。
 * ScreenEffectService が知らせる)。/shaderdebug sun で付け外しを決められる (確認用。{@link #toggle}、{@link #clearOverride} で自動に戻す)。
 * <p>
 * プレイヤーの近くの {@link #W} x {@link #H} x {@link #D} ブロック (格子。{@link #STEP} ブロック動くごとにずらす) の空気のブロックごとに、
 * パストレーシングのような光の量を求め ({@link #computeLight})、バニラの明るさ (光の表) との比を送る。シェーダーはこの倍率を面の色に掛ける:
 * <ul>
 *   <li>直接光: 太陽 (太陽の向きへブロックをたどって見えるか x 日差しの強さ。葉は一部だけ通す、鉄格子・柵などは素通し) と、
 *       光源ブロック (ブロックの光の山の頂点。まっすぐ見えるか x 距離による減り方)</li>
 *   <li>空の光: バニラの空の光 x 空の明るさ (空が見える量の目安)</li>
 *   <li>跳ね返り (1 回): ブロックごとに決まった向きへ {@link #BOUNCE_RAYS} 本たどり、当たった面の直接光 x 面の色の明るさ (地図の色) を集める</li>
 * </ul>
 * ほかに、太陽が直接当たっているか (光の筋・面の向きの明暗に使う) と、2 ブロックごとの光の種類 (空・太陽・暖かい光源・冷たい光源。
 * 色はシェーダーが決める) を送る。データの画素 (リソースパックの iem_control.glsl の IEM_DATA_*・iem_sun.glsl。並びはそちらと同じ) として、
 * 本人にだけ見えるテキストディスプレイの文字で送る。クライアントの mod (IEM_Compatibility) が {@link #CHANNEL} を受け取れると知らせてきたら、
 * テキストディスプレイは出さず、同じ値をそのチャンネルで送る (mod がテクスチャに書き、シェーダーはそちらを読む)。
 * シェーダーは正しく届いたデータをフレームをまたいで覚えておく (ちらつかないように) ので、止める時は「光なし」(ヘッダーの印のマスを 0 にしたもの) を送ってから消す。
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
    /** mod (IEM_Compatibility) へ送るチャンネル。中身は値を 0 番から 2 バイトずつ (ビッグエンディアン) */
    static final String CHANNEL = "iem:light";

    // ---- 格子 (iem_sun.glsl と同じ) ----
    private static final int MAGIC = 0x2B6C;
    /**
     * ヘッダー: 3 = 印、4〜6 = 格子の原点 (1024 で割った余り)、7〜9 = 光の格子の大きさ (ブロック)、10〜12 = 光の種類の格子の大きさ (マス)、
     * 13〜15 = 遠くの太陽の影の原点 (1024 で割った余り)、16・17 = 遠くの太陽の影の列の数 (x・z。夜は 0)。
     * 中身は PAYLOAD 番から光の格子 (値 1 つに 3 ブロック、4bit ずつ: 下 3bit が倍率の段、上 1bit が太陽が当たっているか)、
     * 光の種類の格子 (値 1 つに 7 マス、2bit ずつ)、遠くの太陽の影 (値 1 つに 1 列: 下 7bit が地面の上の高さ、上 7bit が日なたの始まる高さ。原点の y から)
     */
    private static final int HEADER = 3, PAYLOAD = 18;
    /** 格子の大きさ (ブロック) */
    public static final int W = 48, H = 16, D = 48;
    /** 光の種類の格子の 1 マスの大きさ (ブロック) と、マスの数 */
    static final int COLOR_SCALE = 2, CW = W / COLOR_SCALE, CH = H / COLOR_SCALE, CD = D / COLOR_SCALE;
    /** 格子をずらす間隔 (ブロック) */
    static final int STEP = 4;
    /** 空の光・ブロックの光などを読む、格子の周りの余白 (格子の外の光源・跳ね返る面も拾う) */
    static final int MARGIN = 12;
    /** 空の光・ブロックの光などを読む範囲 (格子 + 余白) */
    public static final int SPAN_X = W + 2 * MARGIN, SPAN_Y = H + 2 * MARGIN, SPAN_Z = D + 2 * MARGIN;
    /** 倍率の段 (0〜7): 1.25 の (段 - 5) 乗。段 5 が 1 倍 (iem_sun.glsl の iemLightFactor と同じ) */
    static final double LEVEL_BASE = 1.25;
    static final int LEVEL_ONE = 5;
    /** 光源ブロックをたどる数の上限 (格子の真ん中に近い順) */
    private static final int MAX_LIGHTS = 96;
    /** 太陽へたどる長さ (ブロック) */
    private static final int REACH = 48;
    /** 跳ね返りをたどる長さ (ブロック。余白より短く) と、1 ブロックあたりの本数 */
    static final int BOUNCE_REACH = 8, BOUNCE_RAYS = 12;
    /** 太陽がこれより低い (向きの y) 時は太陽の光を 0 にする */
    private static final double MIN_SUN_Y = 0.08;
    /** 一部だけ光を通すブロックを通った後、これより暗ければ影 (先をたどらない) */
    private static final double MIN_TRANSMIT = 0.02;
    /** 光の量の釣り合い (バニラの明るさ 1 = 昼の屋外): 基本、空の光、太陽の直接光、跳ね返り */
    static final double AMBIENT = 0.04, SKY = 0.55, SUN = 0.55, BOUNCE = 1.0;
    /** 雨の間の太陽の直接光の強さ */
    private static final double RAIN_SUN = 0.25;
    /** 太陽の角度がこれだけ動いたら作り直す (度) */
    private static final double ANGLE_STEP = 0.75;
    /** 動かなくてもこの間隔で作り直す (光源ブロックを置いた・壊したのを反映する) */
    private static final long REFRESH_MILLIS = 10_000;
    /**
     * 遠くの太陽の影 (屋外だけ。光の格子の外): FAR_SPAN x FAR_SPAN ブロックを FAR_COLUMN ブロック四方の列ごとに、地面 (一番上のブロック) の上の高さと、
     * 日なたの始まる高さ (それより下の地面の上は日陰)。高さは原点の y から FAR_HEIGHT ブロックまで。列の高さはワールドの高さマップから取り
     * (FAR_STEP ブロック動くか FAR_REFRESH ごと)、影は太陽の側の列から順に伝える (太陽の側へ FAR_SUN_REACH ブロックまで見る)
     */
    static final int FAR_COLUMN = 2, FAR_SPAN = 112, FAR_COLUMNS = FAR_SPAN / FAR_COLUMN, FAR_HEIGHT = 128;
    static final int FAR_SUN_REACH = 64, FAR_STEP = 8;
    private static final long FAR_REFRESH = 30_000;
    /** 光の種類 (シェーダーが色を決める): 空・跳ね返り、太陽、暖かい光源、冷たい光源 */
    static final int KIND_SKY = 0, KIND_SUN = 1, KIND_WARM = 2, KIND_COOL = 3;
    /** 太陽・光源ブロックの光をその種類にする、光の量に対する割合 */
    private static final double KIND_SHARE = 0.35;

    private static final class State {
        final UUID playerId;
        TextDisplay display;
        int ox = Integer.MIN_VALUE, oy, oz;   // 格子の原点
        int fx = Integer.MIN_VALUE, fy, fz;   // 遠くの太陽の影の原点
        int[] heights;                        // 遠くの太陽の影の列の高さ (高さマップ。原点 fx - FAR_SUN_REACH, fz)
        long heightsAt;
        double angle = Double.NaN;
        long computedAt;
        boolean computing;

        State(UUID playerId) {
            this.playerId = playerId;
        }
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
            setData(old, null, null);
            TextDisplay display = old.display;
            if (display != null) Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), display::remove, 20L);
            return;
        }
        states.putIfAbsent(player.getUniqueId(), new State(player.getUniqueId()));
        if (task == null) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(ImpossbleEscapeMC.getInstance(), CHANNEL);
            task = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), SunShadowService::tick, 1L, 10L);
        }
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
        if (!viaChannel(player)) {
            ensureDisplay(player, state, at);
        } else if (state.display != null) {
            state.display.remove();
            state.display = null;
        }

        double angle = sunAngle(world.getTime());
        double[] sun = sunDirection(angle);
        // 格子は STEP ブロックごとにずらす (プレイヤーが真ん中あたりに来るように。上下は足元が下から 6〜10 ブロック)
        int ox = Math.floorDiv(at.getBlockX(), STEP) * STEP - (W - STEP) / 2;
        int oy = Math.floorDiv(at.getBlockY(), STEP) * STEP - 6;
        int oz = Math.floorDiv(at.getBlockZ(), STEP) * STEP - (D - STEP) / 2;
        boolean moved = ox != state.ox || oy != state.oy || oz != state.oz;
        boolean turned = Double.isNaN(state.angle) || Math.abs(angle - state.angle) > ANGLE_STEP;
        boolean stale = System.currentTimeMillis() - state.computedAt > REFRESH_MILLIS;
        if (state.computing || !(moved || turned || stale)) return;

        state.ox = ox;
        state.oy = oy;
        state.oz = oz;
        state.angle = angle;
        state.computedAt = System.currentTimeMillis();
        boolean day = sun[1] >= MIN_SUN_Y;
        // 遠くの太陽の影: 原点は FAR_STEP ブロックごと。列の高さ (高さマップ) は原点が動いたか古くなった時だけ取り直す
        int fx = Math.floorDiv(at.getBlockX(), FAR_STEP) * FAR_STEP - FAR_SPAN / 2 + FAR_STEP / 2;
        int fz = Math.floorDiv(at.getBlockZ(), FAR_STEP) * FAR_STEP - FAR_SPAN / 2 + FAR_STEP / 2;
        int fy = oy + H / 2 - FAR_HEIGHT / 2;
        boolean farMoved = fx != state.fx || fz != state.fz;
        int[] heights = farMoved ? null : state.heights;
        if (day && (heights == null || System.currentTimeMillis() - state.heightsAt > FAR_REFRESH)) {
            heights = heights(world, fx, fz);
            state.heightsAt = System.currentTimeMillis();
        }
        int[] farHeights = heights;
        Light light = new Light(world.getUID(), day ? sun : null, sunStrength(sun[1]) * (world.hasStorm() ? RAIN_SUN : 1.0), daylight(sun[1]));
        // チャンクの写しだけをメインスレッドで取り、ブロックを読むのと計算は別スレッドで
        Snapshot.Area area = Snapshot.area(world, ox, oy, oz, day ? sun : new double[]{0, 1, 0});
        Map<Long, ChunkSnapshot> chunks = Snapshot.chunks(world, area);
        state.computing = true;
        Bukkit.getScheduler().runTaskAsynchronously(ImpossbleEscapeMC.getInstance(), () -> {
            Snapshot snapshot = Snapshot.of(chunks, area, ox, oy, oz);
            Grids grids = computeLight(snapshot, ox, oy, oz, light);
            int[] far = day && farHeights != null ? farColumns(farHeights, fy, sun) : new int[0];
            Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> {
                state.computing = false;
                if (states.get(player.getUniqueId()) != state) return;
                state.fx = fx;
                state.fy = fy;
                state.fz = fz;
                state.heights = farHeights;
                setData(state, grids, far);
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

    /**
     * 計算に使うブロックの写し。transmit は光の通り方 ({@link #transmitTable}。範囲は x0.. の nx x ny x nz)、
     * skyLight・blockLight・albedo・cool は空の光・ブロックの光・面の色の明るさ (0〜255)・冷たい光源か (範囲は格子 + 余白、原点 sx0..)、
     * top はさえぎるブロックのある一番上の y (これより上へ出た光はもうさえぎられない)
     */
    record Snapshot(int x0, int y0, int z0, int nx, int ny, int nz, byte[] transmit,
                    int sx0, int sy0, int sz0, byte[] skyLight, byte[] blockLight, byte[] albedo, byte[] cool, int top) {

        /** 写す範囲: 格子と余白、そこから太陽の向きへたどる分 */
        record Area(int x0, int y0, int z0, int x1, int y1, int z1) {
        }

        static Area area(World world, int ox, int oy, int oz, double[] sun) {
            int x0 = ox - MARGIN + (int) Math.floor(Math.min(0, sun[0] * REACH)) - 1;
            int x1 = ox - MARGIN + SPAN_X + (int) Math.ceil(Math.max(0, sun[0] * REACH)) + 1;
            int z0 = oz - MARGIN + (int) Math.floor(Math.min(0, sun[2] * REACH)) - 1;
            int z1 = oz - MARGIN + SPAN_Z + (int) Math.ceil(Math.max(0, sun[2] * REACH)) + 1;
            int y0 = Math.max(world.getMinHeight(), oy - MARGIN);
            int y1 = Math.min(world.getMaxHeight(), oy - MARGIN + SPAN_Y + (int) Math.ceil(Math.max(0, sun[1]) * REACH) + 1);
            return new Area(x0, y0, z0, x1, Math.max(y0 + 1, y1), z1);
        }

        /** チャンクの写し (メインスレッドで取る。読み込まれていないチャンクは無し = 空気) */
        static Map<Long, ChunkSnapshot> chunks(World world, Area a) {
            transmitTable();
            materialTables();
            Map<Long, ChunkSnapshot> chunks = new HashMap<>();
            for (int cx = a.x0() >> 4; cx <= (a.x1() - 1) >> 4; cx++) {
                for (int cz = a.z0() >> 4; cz <= (a.z1() - 1) >> 4; cz++) {
                    if (world.isChunkLoaded(cx, cz)) chunks.put(key(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
                }
            }
            return chunks;
        }

        /** チャンクの写しからブロックを読む (別スレッドでよい) */
        static Snapshot of(Map<Long, ChunkSnapshot> chunks, Area a, int ox, int oy, int oz) {
            int nx = a.x1() - a.x0(), ny = a.y1() - a.y0(), nz = a.z1() - a.z0();
            int sx0 = ox - MARGIN, sy0 = oy - MARGIN, sz0 = oz - MARGIN;
            int span = SPAN_X * SPAN_Y * SPAN_Z;
            byte[] transmit = new byte[nx * ny * nz];
            byte[] sky = new byte[span], block = new byte[span], albedo = new byte[span], cool = new byte[span];
            byte[] table = transmitTable();
            byte[][] materials = materialTables();
            int top = Integer.MIN_VALUE;
            for (int x = a.x0(); x < a.x1(); x++) {
                for (int z = a.z0(); z < a.z1(); z++) {
                    ChunkSnapshot chunk = chunks.get(key(x >> 4, z >> 4));
                    if (chunk == null) {
                        for (int y = a.y0(); y < a.y1(); y++) transmit[((y - a.y0()) * nz + (z - a.z0())) * nx + (x - a.x0())] = (byte) 255;
                        continue;
                    }
                    boolean inSpan = x >= sx0 && x < sx0 + SPAN_X && z >= sz0 && z < sz0 + SPAN_Z;
                    for (int y = a.y0(); y < a.y1(); y++) {
                        int type = chunk.getBlockType(x & 15, y, z & 15).ordinal();
                        byte t = table[type];
                        transmit[((y - a.y0()) * nz + (z - a.z0())) * nx + (x - a.x0())] = t;
                        if (t != (byte) 255 && y > top) top = y;
                        if (inSpan && y >= sy0 && y < sy0 + SPAN_Y) {
                            int i = x - sx0 + SPAN_X * ((z - sz0) + SPAN_Z * (y - sy0));
                            sky[i] = (byte) chunk.getBlockSkyLight(x & 15, y, z & 15);
                            block[i] = (byte) chunk.getBlockEmittedLight(x & 15, y, z & 15);
                            albedo[i] = materials[0][type];
                            cool[i] = materials[1][type];
                        }
                    }
                }
            }
            return new Snapshot(a.x0(), a.y0(), a.z0(), nx, ny, nz, transmit, sx0, sy0, sz0, sky, block, albedo, cool, top);
        }

        /** 光の通る割合 (0〜255。範囲の外は 255) */
        int transmit(int x, int y, int z) {
            int ix = x - x0, iy = y - y0, iz = z - z0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) return 255;
            return transmit[(iy * nz + iz) * nx + ix] & 0xFF;
        }

        /** 空の光などの並びの番号 (範囲の外は -1) */
        int lightIndex(int x, int y, int z) {
            int ix = x - sx0, iy = y - sy0, iz = z - sz0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= SPAN_X || iy >= SPAN_Y || iz >= SPAN_Z) return -1;
            return ix + SPAN_X * (iz + SPAN_Z * iy);
        }

        int sky(int x, int y, int z) {
            int i = lightIndex(x, y, z);
            return i < 0 ? 0 : skyLight[i];
        }

        int block(int x, int y, int z) {
            int i = lightIndex(x, y, z);
            return i < 0 ? 0 : blockLight[i];
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /**
     * 種類ごとの光の通り方 (0 = さえぎる、255 = 通す、間は通る割合 x 255)。
     * さえぎる: 光を通さないブロック、当たり判定のあるもの (ハーフブロック・階段・扉・塀など)。
     * 一部だけ通す: 葉 (木漏れ日)。
     * 通す: 光が隙間を抜けるブロック (鉄格子・銅の格子・銅の網・鎖・柵・フェンスゲート・はしご・足場・クモの巣・ランタン・エンドロッド・避雷針。
     * 隙間の形はこの格子 (1 ブロックごと) では表せないので、光は抜けるものとする)、ガラス・板ガラス・氷、当たり判定の無いもの (草・花・つた・松明など)
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
                boolean lattice = name.endsWith("_BARS") || name.endsWith("COPPER_GRATE") || name.endsWith("_CHAIN")
                        || Tag.FENCES.isTagged(m) || Tag.FENCE_GATES.isTagged(m)
                        || m == Material.LADDER || m == Material.SCAFFOLDING || m == Material.COBWEB
                        || (name.endsWith("LANTERN") && m != Material.JACK_O_LANTERN && m != Material.SEA_LANTERN)
                        || m == Material.END_ROD || name.endsWith("LIGHTNING_ROD");
                if (lattice || glassy) t = 1.0;
                else if (Tag.LEAVES.isTagged(m)) t = 0.35;
                else if (m.isOccluding() || m.isSolid()) t = 0.0;
            }
            table[m.ordinal()] = (byte) Math.round(t * 255);
        }
        transmitTable = table;
        return table;
    }

    /** 種類ごとの面の色の明るさ ([0]、0〜255。地図の色の明るさ) と、冷たい色の光源か ([1]、1 なら冷たい) */
    private static byte[][] materialTables;

    /**
     * 種類ごとの面の色の明るさ (地図の色。跳ね返りの強さ) と、冷たい色の光源か (魂の炎・シーランタン・エンドロッドなど)。
     * ブロックの状態を作るのでメインスレッドで先に作っておく
     */
    static synchronized byte[][] materialTables() {
        if (materialTables != null) return materialTables;
        Material[] all = Material.values();
        byte[] albedo = new byte[all.length], cool = new byte[all.length];
        for (Material m : all) {
            double a = 0.5;
            if (m.isBlock() && !m.isAir()) {
                try {
                    Color c = m.createBlockData().getMapColor();
                    a = (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
                } catch (RuntimeException ignored) {
                    // 状態を作れない種類は真ん中の明るさ
                }
            }
            albedo[m.ordinal()] = (byte) Math.round(Math.clamp(a, 0.08, 0.9) * 255);
            String name = m.name();
            boolean isCool = name.startsWith("SOUL_") && (name.endsWith("LANTERN") || name.endsWith("TORCH") || name.endsWith("FIRE") || name.endsWith("CAMPFIRE"))
                    || m == Material.SEA_LANTERN || m == Material.END_ROD || m == Material.BEACON || m == Material.CONDUIT
                    || name.endsWith("AMETHYST_BUD") || m == Material.AMETHYST_CLUSTER || m == Material.SCULK_CATALYST || m == Material.SCULK_SENSOR;
            cool[m.ordinal()] = (byte) (isCool ? 1 : 0);
        }
        materialTables = new byte[][]{albedo, cool};
        return materialTables;
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

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

    // ---- 光 ----

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

    /** レベル 0〜15 の明るさの表 (ブロックごとの計算で毎回求めない) */
    private static final double[] LIGHT_CURVE = new double[16], SKY_CURVE = new double[16];

    static {
        for (int level = 0; level < 16; level++) {
            LIGHT_CURVE[level] = lightCurve(level);
            SKY_CURVE[level] = skyCurve(level);
        }
    }

    /** 光源ブロック: ブロックの光が周り 6 方向のどれよりも小さくない所 (光は 1 ブロックごとに 1 以上減るので、山の頂点は光源)。
     * 読んだ範囲の縁は、外の明るい所が見えないので除く。{x, y, z, レベル} (ワールドの座標) */
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
                    if (top) lights.add(new int[]{s.sx0() + x, s.sy0() + y, s.sz0() + z, level});
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

    /** 跳ね返りをたどる向き (球の上に均等に 2 組。ブロックごとに組を替えて偏りを減らす) */
    private static final double[][][] BOUNCE_DIRS = bounceDirections();

    private static double[][][] bounceDirections() {
        double[][][] sets = new double[2][BOUNCE_RAYS][];
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int set = 0; set < 2; set++) {
            for (int i = 0; i < BOUNCE_RAYS; i++) {
                double y = 1 - (i + 0.5) / BOUNCE_RAYS * 2;
                double r = Math.sqrt(1 - y * y);
                double phi = i * golden + set * golden * 0.5;
                sets[set][i] = new double[]{Math.cos(phi) * r, y, Math.sin(phi) * r};
            }
        }
        return sets;
    }

    /** 光の向き・強さ: world はワールド (光源ブロックの見え方を覚えておく鍵)、sun は太陽の向き (夜は null)、
     * sunStrength は太陽の直接光の強さ (雨で弱い)、daylight は空の明るさ */
    record Light(UUID world, double[] sun, double sunStrength, double daylight) {
    }

    /** 送る 2 つの格子 (詰めた値) */
    record Grids(int[] light, int[] kinds) {
    }

    /** 太陽の直接光の強さ (太陽の高さ: 地平線の近くで 0、高いと 1) */
    static double sunStrength(double sunY) {
        double t = Math.clamp((sunY - MIN_SUN_Y) / 0.25, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    /** 倍率を段 (0〜7) にする: 1.25 の何乗に近いか */
    static int lightLevel(double ratio) {
        int level = (int) Math.round(Math.log(Math.max(ratio, 1e-3)) / Math.log(LEVEL_BASE)) + LEVEL_ONE;
        return Math.clamp(level, 0, 7);
    }

    static double levelFactor(int level) {
        return Math.pow(LEVEL_BASE, level - LEVEL_ONE);
    }

    /**
     * 格子 (原点 ox, oy, oz) の光を計算して詰める。
     * 1. 格子と跳ね返りの分の余白 (BOUNCE_REACH) の空気ごとに直接光: 太陽 ({@link #sunSweep}。層ごとに光の残りを伝える) と
     *    光源ブロック (光源ごとに見え方の表 ({@link #lightVisibility}。覚えておく) から周りへ配る)
     * 2. 2 ブロックごとに跳ね返り (BOUNCE_RAYS 本。当たった面の手前の空気の直接光 x 面の色の明るさの平均)。ブロックごとの値は周りのマスから混ぜる
     * 3. 光の量 (基本 + 空の光 + 直接光 + 跳ね返り) とバニラの明るさの比を段にし、太陽が当たっているかと一緒に詰める。
     *    固体のブロックは隣の空気の段の平均 (シェーダーが面の近くで混ぜても浮かないように)
     * 4. 2 ブロックごとに光の種類 (空・跳ね返り、太陽、暖かい光源、冷たい光源。ブロックごとに目立つ光 (KIND_SHARE 以上) の種類の、多い方)
     */
    static Grids computeLight(Snapshot s, int ox, int oy, int oz, Light light) {
        // 1. 直接光 (格子 + 余白 BOUNCE_REACH の箱)
        int bx0 = ox - BOUNCE_REACH, by0 = oy - BOUNCE_REACH, bz0 = oz - BOUNCE_REACH;
        int bw = W + 2 * BOUNCE_REACH, bh = H + 2 * BOUNCE_REACH, bd = D + 2 * BOUNCE_REACH;
        float[] sunLight = new float[bw * bh * bd], blockLight = new float[bw * bh * bd];
        boolean[] coolLight = new boolean[bw * bh * bd];
        if (light.sun() != null) {
            float[] visible = sunSweep(s, bx0, by0, bz0, bw, bh, bd, light.sun());
            float strength = (float) (SUN * light.sunStrength());
            for (int i = 0; i < visible.length; i++) sunLight[i] = visible[i] * strength;
        }
        List<int[]> lights = lightSources(s);
        double cx = ox + W / 2.0, cy = oy + H / 2.0, cz = oz + D / 2.0;
        lights.removeIf(l -> l[0] < bx0 - l[3] || l[0] >= bx0 + bw + l[3] || l[1] < by0 - l[3] || l[1] >= by0 + bh + l[3]
                || l[2] < bz0 - l[3] || l[2] >= bz0 + bd + l[3]);
        lights.sort(java.util.Comparator.comparingDouble(l -> (l[0] - cx) * (l[0] - cx) + (l[1] - cy) * (l[1] - cy) + (l[2] - cz) * (l[2] - cz)));
        if (lights.size() > MAX_LIGHTS) lights = lights.subList(0, MAX_LIGHTS);
        // 光源ごとに、見え方の表から周りへ配る (多い方)
        for (int[] l : lights) {
            int r = l[3];
            int li = s.lightIndex(l[0], l[1], l[2]);
            boolean cool = li >= 0 && s.cool()[li] != 0;
            byte[] vis = lightVisibility(s, light.world(), l);
            for (int dy = -r; dy <= r; dy++) {
                int y = l[1] + dy - by0;
                if (y < 0 || y >= bh) continue;
                for (int dz = -r; dz <= r; dz++) {
                    int z = l[2] + dz - bz0;
                    if (z < 0 || z >= bd) continue;
                    for (int dx = -r; dx <= r; dx++) {
                        int x = l[0] + dx - bx0;
                        if (x < 0 || x >= bw) continue;
                        double reach = r - Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (reach <= 0) continue;
                        int v = vis[(dx + r) + (2 * r + 1) * ((dz + r) + (2 * r + 1) * (dy + r))] & 0xFF;
                        if (v == 0) continue;
                        float bright = (float) (lightCurve(reach) * v / 255.0);
                        int i = x + bw * (z + bd * y);
                        if (bright > blockLight[i]) {
                            blockLight[i] = bright;
                            coolLight[i] = cool;
                        }
                    }
                }
            }
        }
        // バニラの光の回り込みの分を少し残す (跳ね返りの足し)。固体のブロックは 0
        for (int y = 0; y < bh; y++) {
            for (int z = 0; z < bd; z++) {
                for (int x = 0; x < bw; x++) {
                    int i = x + bw * (z + bd * y);
                    int px = bx0 + x, py = by0 + y, pz = bz0 + z;
                    if (s.transmit(px, py, pz) == 0) {
                        sunLight[i] = 0;
                        blockLight[i] = 0;
                        continue;
                    }
                    int vanilla = s.block(px, py, pz);
                    if (vanilla <= 0) {
                        blockLight[i] = 0;
                        continue;
                    }
                    blockLight[i] = Math.max(blockLight[i], (float) LIGHT_CURVE[Math.max(vanilla - 5, 0)]);
                }
            }
        }
        // 2. 跳ね返り (2 ブロックごと。マスの中の空気のブロック 1 つから)
        float[] bounceCell = new float[CW * CH * CD];
        for (int y = 0; y < CH; y++) {
            for (int z = 0; z < CD; z++) {
                for (int x = 0; x < CW; x++) {
                    int c = x + CW * (z + CD * y);
                    bounceCell[c] = Float.NaN;
                    for (int i = 0; i < 8; i++) {
                        int px = ox + x * COLOR_SCALE + (i & 1), py = oy + y * COLOR_SCALE + (i >> 1 & 1), pz = oz + z * COLOR_SCALE + (i >> 2);
                        if (s.transmit(px, py, pz) == 0) continue;
                        double sum = 0;
                        for (double[] dir : BOUNCE_DIRS[(px + py + pz) & 1]) {
                            sum += bounceRay(s, px, py, pz, dir, bx0, by0, bz0, bw, bh, bd, sunLight, blockLight);
                        }
                        bounceCell[c] = (float) (sum * BOUNCE / BOUNCE_RAYS);
                        break;
                    }
                }
            }
        }
        // 2・3. 格子の空気ごとの光の量と、バニラとの比
        byte[] level = new byte[W * H * D];
        boolean[] sunlit = new boolean[W * H * D], solid = new boolean[W * H * D];
        int[] kind = new int[W * H * D];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    int g = x + W * (z + D * y);
                    int px = ox + x, py = oy + y, pz = oz + z;
                    if (s.transmit(px, py, pz) == 0) {
                        solid[g] = true;
                        continue;
                    }
                    int b = (x + BOUNCE_REACH) + bw * ((z + BOUNCE_REACH) + bd * (y + BOUNCE_REACH));
                    double bounce = bounceAt(bounceCell, x, y, z);
                    double sky = SKY_CURVE[s.sky(px, py, pz)] * light.daylight();
                    double skyPart = AMBIENT + SKY * sky + bounce;
                    double sunPart = sunLight[b], blockPart = blockLight[b];
                    double want = skyPart + sunPart + blockPart;
                    double have = Math.min(1.0, AMBIENT + sky + LIGHT_CURVE[s.block(px, py, pz)]);
                    level[g] = (byte) lightLevel(want / Math.max(have, AMBIENT));
                    sunlit[g] = sunPart > SUN * light.sunStrength() * 0.5 && sunPart > 0;
                    // 光の種類: 太陽・光源ブロックが光の量の KIND_SHARE 以上なら (多い方)、ほかは空 (空の光は弱い色なので、目立つ光を優先する)
                    if (Math.max(sunPart, blockPart) < KIND_SHARE * want) kind[g] = KIND_SKY;
                    else kind[g] = sunPart >= blockPart ? KIND_SUN : coolLight[b] ? KIND_COOL : KIND_WARM;
                }
            }
        }
        // 固体のブロック: 隣の空気の段の平均、太陽が当たっているかは多い方
        int[] values = new int[(W * H * D + 2) / 3];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    int g = x + W * (z + D * y);
                    int lv = level[g];
                    boolean lit = sunlit[g];
                    if (solid[g]) {
                        int sum = 0, count = 0, litCount = 0;
                        for (int[] f : FACES) {
                            int nx = x + f[0], ny = y + f[1], nz = z + f[2];
                            if (nx < 0 || ny < 0 || nz < 0 || nx >= W || ny >= H || nz >= D) continue;
                            int n = nx + W * (nz + D * ny);
                            if (solid[n]) continue;
                            sum += level[n];
                            count++;
                            if (sunlit[n]) litCount++;
                        }
                        lv = count > 0 ? Math.round((float) sum / count) : LEVEL_ONE;
                        lit = count > 0 && litCount * 2 > count;
                    }
                    values[g / 3] |= (lv | (lit ? 8 : 0)) << ((g % 3) * 4);
                }
            }
        }
        // 4. 光の種類 (2 ブロックごと。空気の中で一番多い種類。全部固体なら空)
        int[] kinds = new int[(CW * CH * CD + 6) / 7];
        for (int y = 0; y < CH; y++) {
            for (int z = 0; z < CD; z++) {
                for (int x = 0; x < CW; x++) {
                    int[] count = new int[4];
                    for (int i = 0; i < 8; i++) {
                        int gx = x * COLOR_SCALE + (i & 1), gy = y * COLOR_SCALE + (i >> 1 & 1), gz = z * COLOR_SCALE + (i >> 2);
                        int g = gx + W * (gz + D * gy);
                        if (!solid[g]) count[kind[g]]++;
                    }
                    int best = KIND_SKY;
                    for (int k = 1; k < 4; k++) if (count[k] > count[best]) best = k;
                    int c = x + CW * (z + CD * y);
                    kinds[c / 7] |= best << ((c % 7) * 2);
                }
            }
        }
        return new Grids(values, kinds);
    }

    /** ブロック (格子の座標 x, y, z) の跳ね返り: 2 ブロックごとのマスの値を、マスの真ん中からの位置で混ぜる (空気の無いマスは除く) */
    private static double bounceAt(float[] cells, int x, int y, int z) {
        double gx = (x + 0.5) / COLOR_SCALE - 0.5, gy = (y + 0.5) / COLOR_SCALE - 0.5, gz = (z + 0.5) / COLOR_SCALE - 0.5;
        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy), z0 = (int) Math.floor(gz);
        double fx = gx - x0, fy = gy - y0, fz = gz - z0;
        double sum = 0, weights = 0;
        for (int i = 0; i < 8; i++) {
            int cx = Math.clamp(x0 + (i & 1), 0, CW - 1), cy = Math.clamp(y0 + (i >> 1 & 1), 0, CH - 1), cz = Math.clamp(z0 + (i >> 2), 0, CD - 1);
            float v = cells[cx + CW * (cz + CD * cy)];
            if (Float.isNaN(v)) continue;
            double w = ((i & 1) != 0 ? fx : 1 - fx) * ((i >> 1 & 1) != 0 ? fy : 1 - fy) * ((i >> 2) != 0 ? fz : 1 - fz);
            sum += v * w;
            weights += w;
        }
        return weights > 1e-6 ? sum / weights : 0;
    }

    /**
     * 太陽の直接光の届く割合 (箱 bx0.. の bw x bh x bd ブロック。0〜1。固体のブロックの値は使わない)。
     * 太陽の向きは東西と上下の面の中 (z は 0) なので、z ごとに 2 次元で、太陽の側から 1 層ずつ「その位置に届く光の残り」を伝える:
     * 太陽が高い時は上の層から下へ (横のずれは隣の 2 列を混ぜる)、低い時は太陽の側の列から反対側へ。
     * ブロックごとに光線をたどるより速い。影の縁は少しやわらかくなる。写した範囲の外 (上・太陽の側) は光が通るものとする
     */
    static float[] sunSweep(Snapshot s, int bx0, int by0, int bz0, int bw, int bh, int bd, double[] sun) {
        float[] out = new float[bw * bh * bd];
        java.util.Arrays.fill(out, 1f);   // 写した範囲より上 (ふつうは無い) は光が届く
        int sx0 = s.x0(), sx1 = s.x0() + s.nx(), top = s.y0() + s.ny();
        boolean vertical = Math.abs(sun[0]) <= sun[1];
        for (int z = 0; z < bd; z++) {
            int wz = bz0 + z;
            if (vertical) {
                // 上の層から: V(x, y) = 混ぜる(x + shift) [V(x', y + 1) x 通り方(x', y + 1)]
                double shift = sun[0] / sun[1];
                int n = sx1 - sx0;
                float[] above = new float[n], here = new float[n];
                java.util.Arrays.fill(above, 1f);
                for (int y = top - 2; y >= by0; y--) {
                    for (int i = 0; i < n; i++) {
                        double xf = i + shift;
                        int i0 = (int) Math.floor(xf);
                        double f = xf - i0;
                        here[i] = (float) ((1 - f) * passed(s, above, i0, sx0, y + 1, wz) + f * passed(s, above, i0 + 1, sx0, y + 1, wz));
                    }
                    if (y < by0 + bh) {
                        for (int x = 0; x < bw; x++) out[x + bw * (z + bd * (y - by0))] = here[bx0 + x - sx0];
                    }
                    float[] t = above;
                    above = here;
                    here = t;
                }
            } else {
                // 太陽の側の列から: V(x, y) = 混ぜる(y + shift) [V(x + step, y') x 通り方(x + step, y')]
                int step = sun[0] > 0 ? 1 : -1;
                double shift = sun[1] / Math.abs(sun[0]);
                int n = top - by0;
                float[] prev = new float[n], here = new float[n];
                java.util.Arrays.fill(prev, 1f);
                int start = step > 0 ? sx1 - 2 : sx0 + 1, end = step > 0 ? bx0 : bx0 + bw - 1;
                for (int x = start; step > 0 ? x >= end : x <= end; x -= step) {
                    for (int j = 0; j < n; j++) {
                        double yf = j + shift;
                        int j0 = (int) Math.floor(yf);
                        double f = yf - j0;
                        here[j] = (float) ((1 - f) * passedColumn(s, prev, j0, by0, x + step, wz) + f * passedColumn(s, prev, j0 + 1, by0, x + step, wz));
                    }
                    if (x >= bx0 && x < bx0 + bw) {
                        for (int y = 0; y < bh; y++) out[(x - bx0) + bw * (z + bd * y)] = here[y];
                    }
                    float[] t = prev;
                    prev = here;
                    here = t;
                }
            }
        }
        return out;
    }

    /** 上の層の列 i (ワールドの x = sx0 + i、高さ y) を通った後の光の残り (範囲の外は 1) */
    private static double passed(Snapshot s, float[] layer, int i, int sx0, int y, int z) {
        if (i < 0 || i >= layer.length) return 1.0;
        return layer[i] * (s.transmit(sx0 + i, y, z) / 255.0);
    }

    /** 太陽の側の列 (ワールドの x、高さ by0 + j) を通った後の光の残り (範囲の外は 1) */
    private static double passedColumn(Snapshot s, float[] column, int j, int by0, int x, int z) {
        if (j < 0 || j >= column.length) return 1.0;
        return column[j] * (s.transmit(x, by0 + j, z) / 255.0);
    }

    /** 光源ブロックの見え方の表 (光源ごと): 光源から半径 (レベル) の立方体の、ブロックごとの光の届く割合 (0〜255) */
    private record Visibility(long fingerprint, byte[] visible) {
    }

    private static final int VISIBILITY_CACHE = 512;
    private static final Map<String, Visibility> visibilityCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Visibility> eldest) {
                    return size() > VISIBILITY_CACHE;
                }
            });

    /**
     * 光源 l ({x, y, z, レベル}) から周りのブロックへ光が届く割合の表 (立方体 (2 x レベル + 1)^3。並びは x + 辺 x (z + 辺 x y))。
     * 光源はめったに変わらないので覚えておき、立方体の中のブロックの通り方の指紋が変わった時だけ作り直す
     */
    static byte[] lightVisibility(Snapshot s, UUID world, int[] l) {
        int r = l[3], side = 2 * r + 1;
        long fingerprint = 1469598103934665603L;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    fingerprint = (fingerprint ^ s.transmit(l[0] + dx, l[1] + dy, l[2] + dz)) * 1099511628211L;
                }
            }
        }
        String key = world + ":" + l[0] + ":" + l[1] + ":" + l[2] + ":" + r;
        Visibility cached = visibilityCache.get(key);
        if (cached != null && cached.fingerprint() == fingerprint) return cached.visible();
        byte[] visible = new byte[side * side * side];
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy + dz * dz >= r * r) continue;
                    double t = (dx | dy | dz) == 0 ? 1.0 : transmitBetween(s, l[0] + dx, l[1] + dy, l[2] + dz, l[0], l[1], l[2]);
                    visible[(dx + r) + side * ((dz + r) + side * (dy + r))] = (byte) Math.round(t * 255);
                }
            }
        }
        visibilityCache.put(key, new Visibility(fingerprint, visible));
        return visible;
    }

    /** 跳ね返り: 空気のブロック (px, py, pz) から dir の向きへたどり、当たった面の手前の空気の直接光 x 面の色の明るさ (当たらなければ 0) */
    private static double bounceRay(Snapshot s, int px, int py, int pz, double[] d, int bx0, int by0, int bz0, int bw, int bh, int bd,
                                    float[] sunLight, float[] blockLight) {
        int x = px, y = py, z = pz;
        int sx = d[0] > 0 ? 1 : -1, sy = d[1] > 0 ? 1 : -1, sz = d[2] > 0 ? 1 : -1;
        double tdx = d[0] != 0 ? Math.abs(1 / d[0]) : Double.MAX_VALUE;
        double tdy = d[1] != 0 ? Math.abs(1 / d[1]) : Double.MAX_VALUE;
        double tdz = d[2] != 0 ? Math.abs(1 / d[2]) : Double.MAX_VALUE;
        double tx = 0.5 * tdx, ty = 0.5 * tdy, tz = 0.5 * tdz;
        int lastX = x, lastY = y, lastZ = z;
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
            if (t > BOUNCE_REACH) return 0;
            if (s.transmit(x, y, z) == 0) {
                int ix = lastX - bx0, iy = lastY - by0, iz = lastZ - bz0;
                if (ix < 0 || iy < 0 || iz < 0 || ix >= bw || iy >= bh || iz >= bd) return 0;
                int i = ix + bw * (iz + bd * iy);
                int li = s.lightIndex(x, y, z);
                double albedo = li < 0 ? 0.5 : (s.albedo()[li] & 0xFF) / 255.0;
                return (sunLight[i] + blockLight[i]) * albedo;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    // ---- 遠くの太陽の影 ----

    /**
     * 遠くの太陽の影の、列ごとの一番上のブロックの高さ (ワールドの高さマップ。メインスレッドで。x は fx - FAR_SUN_REACH から、z は fz から)。
     * チャンクを丸ごと写すより軽い。読み込まれていないチャンクは読み込ませず、とても低いものとする。並びは x + 幅 x z
     */
    static int[] heights(World world, int fx, int fz) {
        int width = FAR_SPAN + 2 * FAR_SUN_REACH, x0 = fx - FAR_SUN_REACH;
        int[] heights = new int[width * FAR_SPAN];
        for (int z = 0; z < FAR_SPAN; z++) {
            for (int x = 0; x < width; x++) {
                int wx = x0 + x, wz = fz + z;
                heights[x + width * z] = world.isChunkLoaded(wx >> 4, wz >> 4)
                        ? world.getHighestBlockYAt(wx, wz, org.bukkit.HeightMap.MOTION_BLOCKING) : -100_000;
            }
        }
        return heights;
    }

    /**
     * 遠くの太陽の影の列 (FAR_COLUMNS x FAR_COLUMNS) を詰める。太陽の向きは東西と上下の面の中なので、z ごとに太陽の側の列から順に
     * 「その列で日なたの始まる高さ」を伝える (隣の列の高さ - 1 ブロックあたりの太陽の傾き と、自分の地面の上の多い方)。
     * 列 (FAR_COLUMN ブロック四方) の地面は一番高いもの、日なたの始まる高さは平均。高さは原点 fy からの値 (0〜127)
     */
    static int[] farColumns(int[] heights, int fy, double[] sun) {
        int width = FAR_SPAN + 2 * FAR_SUN_REACH;
        double slope = Math.abs(sun[0]) < 1e-3 ? 1e9 : sun[1] / Math.abs(sun[0]);
        int step = sun[0] > 0 ? 1 : -1;   // 太陽のある側
        double[] start = new double[width * FAR_SPAN];
        for (int z = 0; z < FAR_SPAN; z++) {
            double shadow = -1e9;
            for (int k = 0; k < width; k++) {
                int x = step > 0 ? width - 1 - k : k;
                int ground = heights[x + width * z] + 1;
                shadow = Math.max(ground, shadow - slope);
                start[x + width * z] = shadow;
            }
        }
        int[] values = new int[FAR_COLUMNS * FAR_COLUMNS];
        for (int cz = 0; cz < FAR_COLUMNS; cz++) {
            for (int cx = 0; cx < FAR_COLUMNS; cx++) {
                int ground = Integer.MIN_VALUE;
                double sum = 0;
                for (int i = 0; i < FAR_COLUMN * FAR_COLUMN; i++) {
                    int x = FAR_SUN_REACH + cx * FAR_COLUMN + i % FAR_COLUMN, z = cz * FAR_COLUMN + i / FAR_COLUMN;
                    ground = Math.max(ground, heights[x + width * z] + 1);
                    sum += start[x + width * z];
                }
                int g = Math.clamp(ground - fy, 0, FAR_HEIGHT - 1);
                int lit = Math.clamp((int) Math.round(sum / (FAR_COLUMN * FAR_COLUMN)) - fy, g, FAR_HEIGHT - 1);
                values[cx + FAR_COLUMNS * cz] = g | lit << 7;
            }
        }
        return values;
    }

    // ---- 送る ----

    /** 格子の値をデータの文字にしてテキストディスプレイに書く (grids が null なら「光なし」: ヘッダーの印のマスを 0 にして、覚えている値を消させる)。
     * far は遠くの太陽の影 (夜は空) */
    private static void setData(State state, Grids grids, int[] far) {
        Player player = Bukkit.getPlayer(state.playerId);
        boolean channel = player != null && viaChannel(player);
        if (!channel && state.display == null) return;
        int[] light = grids != null ? grids.light() : new int[0];
        int[] kinds = grids != null ? grids.kinds() : new int[0];
        if (grids == null || far == null) far = new int[0];
        int cells = Math.min(DATA_CAPACITY, PAYLOAD + light.length + kinds.length + far.length);
        int[] data = new int[cells];
        data[HEADER] = grids != null ? MAGIC : 0;
        data[HEADER + 1] = state.ox & 1023;
        data[HEADER + 2] = state.oy & 1023;
        data[HEADER + 3] = state.oz & 1023;
        data[HEADER + 4] = grids != null ? W : 0;
        data[HEADER + 5] = H;
        data[HEADER + 6] = D;
        data[HEADER + 7] = grids != null ? CW : 0;
        data[HEADER + 8] = CH;
        data[HEADER + 9] = CD;
        data[HEADER + 10] = state.fx & 1023;
        data[HEADER + 11] = state.fy & 1023;
        data[HEADER + 12] = state.fz & 1023;
        data[HEADER + 13] = far.length > 0 ? FAR_COLUMNS : 0;
        data[HEADER + 14] = FAR_COLUMNS;
        int at = PAYLOAD;
        for (int[] part : new int[][]{light, kinds, far}) {
            int n = Math.min(part.length, cells - at);
            System.arraycopy(part, 0, data, at, n);
            at += n;
        }
        if (channel) {
            byte[] bytes = new byte[cells * 2];
            for (int i = 0; i < cells; i++) {
                bytes[2 * i] = (byte) (data[i] >> 8);
                bytes[2 * i + 1] = (byte) data[i];
            }
            player.sendPluginMessage(ImpossbleEscapeMC.getInstance(), CHANNEL, bytes);
            return;
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

    /** mod (IEM_Compatibility) が {@link #CHANNEL} を受け取れると知らせてきたか */
    private static boolean viaChannel(Player player) {
        return player.getListeningPluginChannels().contains(CHANNEL);
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
