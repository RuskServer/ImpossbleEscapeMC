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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバーで計算した太陽の影を、リソースパックのシェーダーへ送る (試作。/shaderdebug sun で付け外し)。
 * <p>
 * プレイヤーの周りの格子 ({@link #W} x {@link #H} x {@link #D} ブロック) の空気のブロックごとに、太陽の向きへブロックをたどって
 * 日なた・日陰を決め (空の光がほとんど届かない室内は、日が差し込む所だけ「室内の日なた」、ほかは対象外)、データの画素 (リソースパックの iem_control.glsl の IEM_DATA_*・
 * iem_sun.glsl。並びはそちらと同じ) として、本人にだけ見えるテキストディスプレイの文字で送る。ポストエフェクト (ao.fsh・grade.fsh) が
 * 画面の画素の位置からこの格子を読んで日陰を暗くする。シェーダーは正しく届いたデータをフレームをまたいで覚えておく (ちらつかないように) ので、
 * 止める時・夜は「影なし」(ヘッダーの印のマスを 0 にしたもの) を送ってから消す。
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
    private static final int DATA_CAPACITY = 512 * 16;

    // ---- 太陽の影の格子 (iem_sun.glsl と同じ) ----
    private static final int MAGIC = 0x2B6C;
    private static final int HEADER = 3, PAYLOAD = 16, PER_VALUE = 7;
    public static final int W = 48, H = 24, D = 48;
    /** 太陽へたどる長さ (ブロック) */
    private static final int REACH = 48;
    /** この空の光より暗い空気のブロックは室内 (日陰なら対象外 = 光の表の暗さだけ、日が差し込めば室内の日なた) */
    private static final int OUTDOOR_SKY_LIGHT = 12;
    /** 太陽がこれより低い (向きの y) 時は送らない */
    private static final double MIN_SUN_Y = 0.08;
    /** 太陽の角度がこれだけ動いたら作り直す (度) */
    private static final double ANGLE_STEP = 0.75;
    private static final long REFRESH_MILLIS = 30_000;

    private static final class State {
        TextDisplay display;
        int ox = Integer.MIN_VALUE, oy, oz;
        double angle = Double.NaN;
        long computedAt;
        boolean computing;
    }

    private static final Map<UUID, State> states = new ConcurrentHashMap<>();
    private static BukkitTask task;
    private static boolean[] blocksSun;

    private SunShadowService() {
    }

    /** 付け外し。付けたら true */
    public static boolean toggle(Player player) {
        State old = states.remove(player.getUniqueId());
        if (old != null) {
            // 覚えている影を消させてから (1 秒後に) 表示を消す
            setData(old, null);
            TextDisplay display = old.display;
            if (display != null) Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), display::remove, 20L);
            return false;
        }
        states.put(player.getUniqueId(), new State());
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), SunShadowService::tick, 1L, 10L);
        return true;
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
        int ox = Math.floorDiv(at.getBlockX(), 8) * 8 - 20;
        int oy = Math.floorDiv(at.getBlockY(), 8) * 8 - 8;
        int oz = Math.floorDiv(at.getBlockZ(), 8) * 8 - 20;
        boolean moved = ox != state.ox || oy != state.oy || oz != state.oz;
        boolean turned = Double.isNaN(state.angle) || Math.abs(angle - state.angle) > ANGLE_STEP;
        boolean stale = System.currentTimeMillis() - state.computedAt > REFRESH_MILLIS;
        if (state.computing || !(moved || turned || stale)) return;

        state.ox = ox;
        state.oy = oy;
        state.oz = oz;
        state.angle = angle;
        state.computedAt = System.currentTimeMillis();
        if (sun[1] < MIN_SUN_Y) {
            setData(state, null);   // 夜・朝夕は「影なし」を送る (シェーダーが覚えている影を消す)
            return;
        }
        Snapshot snapshot = Snapshot.take(world, ox, oy, oz, sun);
        state.computing = true;
        Bukkit.getScheduler().runTaskAsynchronously(ImpossbleEscapeMC.getInstance(), () -> {
            int[] values = compute(snapshot, ox, oy, oz, sun);
            Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> {
                state.computing = false;
                if (states.get(player.getUniqueId()) == state) setData(state, values);
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

    /** 計算に使うブロックの写し (メインスレッドで取る)。太陽の向きへたどる分まで広く取る */
    record Snapshot(int x0, int y0, int z0, int nx, int ny, int nz, boolean[] solid, byte[] skyLight) {

        static Snapshot take(World world, int ox, int oy, int oz, double[] sun) {
            int x0 = ox + (int) Math.floor(Math.min(0, sun[0] * REACH)) - 1;
            int x1 = ox + W + (int) Math.ceil(Math.max(0, sun[0] * REACH)) + 1;
            int z0 = oz + (int) Math.floor(Math.min(0, sun[2] * REACH)) - 1;
            int z1 = oz + D + (int) Math.ceil(Math.max(0, sun[2] * REACH)) + 1;
            int y0 = Math.max(world.getMinHeight(), oy);
            int y1 = Math.min(world.getMaxHeight(), oy + H + (int) Math.ceil(sun[1] * REACH) + 1);
            int nx = x1 - x0, ny = Math.max(1, y1 - y0), nz = z1 - z0;
            boolean[] solid = new boolean[nx * ny * nz];
            byte[] sky = new byte[W * H * D];
            boolean[] table = blocksSunTable();
            Map<Long, ChunkSnapshot> chunks = new HashMap<>();
            for (int cx = x0 >> 4; cx <= (x1 - 1) >> 4; cx++) {
                for (int cz = z0 >> 4; cz <= (z1 - 1) >> 4; cz++) {
                    if (world.isChunkLoaded(cx, cz)) chunks.put(key(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
                }
            }
            for (int x = x0; x < x1; x++) {
                for (int z = z0; z < z1; z++) {
                    ChunkSnapshot chunk = chunks.get(key(x >> 4, z >> 4));
                    if (chunk == null) continue;
                    boolean inGrid = x >= ox && x < ox + W && z >= oz && z < oz + D;
                    for (int y = y0; y < y1; y++) {
                        Material type = chunk.getBlockType(x & 15, y, z & 15);
                        solid[((y - y0) * nz + (z - z0)) * nx + (x - x0)] = table[type.ordinal()];
                        if (inGrid && y >= oy && y < oy + H) {
                            sky[x - ox + W * ((z - oz) + D * (y - oy))] = (byte) chunk.getBlockSkyLight(x & 15, y, z & 15);
                        }
                    }
                }
            }
            return new Snapshot(x0, y0, z0, nx, ny, nz, solid, sky);
        }

        boolean solid(int x, int y, int z) {
            int ix = x - x0, iy = y - y0, iz = z - z0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) return false;
            return solid[(iy * nz + iz) * nx + ix];
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** 太陽の光をさえぎるブロック: 光を通さないブロック、葉、当たり判定のあるもの (ハーフブロック・階段・柵など。ガラス・氷は除く) */
    private static synchronized boolean[] blocksSunTable() {
        if (blocksSun != null) return blocksSun;
        Material[] all = Material.values();
        boolean[] table = new boolean[all.length];
        for (Material m : all) {
            if (!m.isBlock() || m.isAir()) continue;
            String name = m.name();
            boolean glassy = name.contains("GLASS") || name.endsWith("ICE");
            table[m.ordinal()] = m.isOccluding() || Tag.LEAVES.isTagged(m) || (m.isSolid() && !glassy);
        }
        blocksSun = table;
        return table;
    }

    /** 格子のブロックごとの状態 (0 = 対象外 (固体・室内の日陰)、1 = 屋外の日陰、2 = 屋外の日なた、3 = 室内の日なた (窓などから差し込む日)) を
     * 7 個ずつ 14bit に詰めた値 */
    static int[] compute(Snapshot s, int ox, int oy, int oz, double[] sun) {
        int count = W * H * D;
        int[] values = new int[(count + PER_VALUE - 1) / PER_VALUE];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    int index = x + W * (z + D * y);
                    int wx = ox + x, wy = oy + y, wz = oz + z;
                    int state = 0;
                    if (!s.solid(wx, wy, wz)) {
                        boolean outdoor = s.skyLight()[index] >= OUTDOOR_SKY_LIGHT;
                        boolean blocked = sunBlocked(s, wx + 0.5, wy + 0.5, wz + 0.5, sun);
                        state = outdoor ? (blocked ? 1 : 2) : (blocked ? 0 : 3);
                    }
                    values[index / PER_VALUE] |= state << ((index % PER_VALUE) * 2);
                }
            }
        }
        return values;
    }

    /** ブロックの真ん中から太陽の向きへ、ブロックをひとつずつたどる (Amanatides-Woo) */
    private static boolean sunBlocked(Snapshot s, double px, double py, double pz, double[] d) {
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int sx = d[0] > 0 ? 1 : -1, sy = d[1] > 0 ? 1 : -1, sz = d[2] > 0 ? 1 : -1;
        double tdx = d[0] != 0 ? Math.abs(1 / d[0]) : Double.MAX_VALUE;
        double tdy = d[1] != 0 ? Math.abs(1 / d[1]) : Double.MAX_VALUE;
        double tdz = d[2] != 0 ? Math.abs(1 / d[2]) : Double.MAX_VALUE;
        double tx = d[0] != 0 ? ((sx > 0 ? x + 1 - px : px - x) * tdx) : Double.MAX_VALUE;
        double ty = d[1] != 0 ? ((sy > 0 ? y + 1 - py : py - y) * tdy) : Double.MAX_VALUE;
        double tz = d[2] != 0 ? ((sz > 0 ? z + 1 - pz : pz - z) * tdz) : Double.MAX_VALUE;
        int maxY = s.y0() + s.ny();
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
            if (t > REACH || y >= maxY) return false;
            if (s.solid(x, y, z)) return true;
        }
    }

    // ---- 送る ----

    /** 格子の値をデータの文字にしてテキストディスプレイに書く (null なら「影なし」: ヘッダーの印のマスを 0 にして、覚えている影を消させる) */
    private static void setData(State state, int[] values) {
        if (state.display == null) return;
        if (values == null) values = new int[0];
        int cells = Math.min(DATA_CAPACITY, PAYLOAD + values.length);
        int[] data = new int[cells];
        data[HEADER] = values.length > 0 ? MAGIC : 0;
        data[HEADER + 1] = state.ox & 1023;
        data[HEADER + 2] = state.oy & 1023;
        data[HEADER + 3] = state.oz & 1023;
        data[HEADER + 4] = W;
        data[HEADER + 5] = H;
        data[HEADER + 6] = D;
        System.arraycopy(values, 0, data, PAYLOAD, cells - PAYLOAD);
        StringBuilder text = new StringBuilder(cells * 2);
        for (int v : data) text.append((char) (DATA_BASE + (v & 0x3FFF))).append(DATA_GAP);
        state.display.text(Component.text(text.toString(), DATA_COLOR).font(DATA_FONT));
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
