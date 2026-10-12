package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 音響の地図: 場所ごとの響き方 ({@link AcousticZone})。
 * <ul>
 *   <li>自動の判定: 初めて音が鳴った {@link #CELL} ブロック角のマスで、球面に均等に並べた {@link #RAY_COUNT} 本の光線を飛ばして決め、覚えておく
 *       (上半分の光線のうち空まで抜けた割合・頭の上の重なり・天井の高さ・横の壁までの距離)。サーバーを再起動すると判定し直す</li>
 *   <li>光線はメインスレッドで飛ばすので、新しいマスの判定は 1tick に {@link #DETECTIONS_PER_TICK} 個まで。
 *       超えた分は、判定済みの隣のマスの結果を借りて (覚えずに) 返し、次に音が鳴った時に判定する</li>
 *   <li>マップを改変したら /acoustics clear で、覚えた判定を捨てて判定し直させる</li>
 *   <li>自動の判定が外れる所 (吹き抜け・半屋外の駐車場など) は、範囲で上書きする (/acoustics override)。
 *       上書きはワールド名ごとに acoustics_overrides.json に保存する (レイドは決まった名前のワールドを使う)</li>
 * </ul>
 */
public final class AcousticMap {

    /** 判定を覚えるマスの大きさ (ブロック) */
    static final int CELL = 2;
    /** 上を向いた光線が空に抜けたとみなす距離と、それ以外の光線の長さ (ブロック) */
    private static final double UP_RANGE = 64.0;
    private static final double SIDE_RANGE = 32.0;
    static final int RAY_COUNT = 64;
    /** 球面に均等に並べた光線の向き (フィボナッチ球) */
    static final Vector[] RAYS = fibonacciSphere(RAY_COUNT);
    /** 「上」とみなす光線 (空が見えるかに使う) と、「ほぼ真上」(天井の高さ)・「ほぼ横」(壁までの距離) の境の y */
    private static final double UP_Y = 0.15;
    private static final double CEILING_Y = 0.85;
    private static final double HORIZONTAL_Y = 0.35;
    static final int DETECTIONS_PER_TICK = 4;
    /** 葉は音を遮らないものとして扱う (木の下を屋内にしない) */
    private static final Predicate<Block> BLOCKS_SOUND = block -> !Tag.LEAVES.isTagged(block.getType());

    private static AcousticMap instance;

    private final File overridesFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Map<Long, AcousticZone>> cells = new HashMap<>();
    private final List<Override> overrides = new ArrayList<>();
    private int budgetTick = -1;
    private int detectionsThisTick;

    /** 範囲で決めた場所 (両端を含むブロック座標) */
    public record Override(String world, String zone, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(String worldName, int x, int y, int z) {
            return world.equals(worldName) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        AcousticZone zoneValue() {
            AcousticZone value = AcousticZone.byId(zone);
            return value != null ? value : AcousticZone.OUTDOOR;
        }
    }

    private AcousticMap(JavaPlugin plugin) {
        this.overridesFile = new File(plugin.getDataFolder(), "acoustics_overrides.json");
        loadOverrides(plugin);
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) instance = new AcousticMap(plugin);
    }

    public static AcousticMap get() {
        return instance;
    }

    /** その位置の場所 (地図が無ければ屋外) */
    public static AcousticZone zoneAt(Location location) {
        return instance != null && location.getWorld() != null ? instance.zone(location) : AcousticZone.OUTDOOR;
    }

    public AcousticZone zone(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        Override override = overrideAt(world.getName(), x, y, z);
        if (override != null) return override.zoneValue();
        Map<Long, AcousticZone> worldCells = cells.computeIfAbsent(world.getUID(), id -> new HashMap<>());
        long key = cellKey(x, y, z);
        AcousticZone known = worldCells.get(key);
        if (known != null) return known;
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now != budgetTick) {
            budgetTick = now;
            detectionsThisTick = 0;
        }
        if (detectionsThisTick >= DETECTIONS_PER_TICK) return neighbourZone(worldCells, x, y, z);
        detectionsThisTick++;
        AcousticZone detected = detect(world, x, y, z);
        worldCells.put(key, detected);
        return detected;
    }

    /** 判定済みの隣のマス (上下左右前後) の結果。無ければ屋外 */
    private static AcousticZone neighbourZone(Map<Long, AcousticZone> worldCells, int x, int y, int z) {
        int[][] offsets = {{0, -CELL, 0}, {0, CELL, 0}, {CELL, 0, 0}, {-CELL, 0, 0}, {0, 0, CELL}, {0, 0, -CELL}};
        for (int[] o : offsets) {
            AcousticZone zone = worldCells.get(cellKey(x + o[0], y + o[1], z + o[2]));
            if (zone != null) return zone;
        }
        return AcousticZone.OUTDOOR;
    }

    static Vector[] fibonacciSphere(int count) {
        Vector[] rays = new Vector[count];
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < count; i++) {
            double y = 1.0 - (i + 0.5) * 2.0 / count;
            double r = Math.sqrt(1.0 - y * y);
            rays[i] = new Vector(Math.cos(golden * i) * r, y, Math.sin(golden * i) * r);
        }
        return rays;
    }

    /** 上書きがあればそれ (無ければ null) */
    public Override overrideAt(String worldName, int x, int y, int z) {
        for (int i = overrides.size() - 1; i >= 0; i--) {
            if (overrides.get(i).contains(worldName, x, y, z)) return overrides.get(i);
        }
        return null;
    }

    /** 自動の判定だけを測り直して返す (覚えた判定・上書きは使わない) */
    public AcousticZone detectNow(Location location) {
        return detect(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** 覚えた判定を捨てる。radius が負ならワールド全体。捨てたマスの数を返す */
    public int forget(World world, Location center, int radius) {
        Map<Long, AcousticZone> worldCells = cells.get(world.getUID());
        if (worldCells == null) return 0;
        if (radius < 0) {
            int count = worldCells.size();
            worldCells.clear();
            return count;
        }
        int before = worldCells.size();
        int cx = Math.floorDiv(center.getBlockX(), CELL), cy = Math.floorDiv(center.getBlockY(), CELL), cz = Math.floorDiv(center.getBlockZ(), CELL);
        int r = (radius + CELL - 1) / CELL;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    worldCells.remove(cellKey((cx + dx) * CELL, (cy + dy) * CELL, (cz + dz) * CELL));
                }
            }
        }
        return before - worldCells.size();
    }

    public Override addOverride(Location center, int radius, AcousticZone zone) {
        Override override = new Override(center.getWorld().getName(), zone.id,
                center.getBlockX() - radius, center.getBlockY() - radius, center.getBlockZ() - radius,
                center.getBlockX() + radius, center.getBlockY() + radius, center.getBlockZ() + radius);
        overrides.add(override);
        saveOverrides();
        return override;
    }

    /** その位置を含む上書きを消す。消した数を返す */
    public int removeOverridesAt(Location location) {
        String worldName = location.getWorld().getName();
        int before = overrides.size();
        overrides.removeIf(o -> o.contains(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        if (overrides.size() != before) saveOverrides();
        return before - overrides.size();
    }

    public List<Override> overrides(String worldName) {
        return overrides.stream().filter(o -> o.world().equals(worldName)).toList();
    }

    /** マスの番号 (x, z は 21bit、y は 22bit ずつ) */
    static long cellKey(int x, int y, int z) {
        long cx = Math.floorDiv(x, CELL) & 0x1FFFFFL;
        long cz = Math.floorDiv(z, CELL) & 0x1FFFFFL;
        long cy = Math.floorDiv(y, CELL) & 0x3FFFFFL;
        return (cx << 43) | (cz << 22) | cy;
    }

    /** マスの中心の目の高さから光線を飛ばして決める */
    private static AcousticZone detect(World world, int x, int y, int z) {
        Location eye = new Location(world, Math.floorDiv(x, CELL) * CELL + CELL / 2.0, y + 1.6, Math.floorDiv(z, CELL) * CELL + CELL / 2.0);
        int up = 0, open = 0, horizontal = 0;
        double horizontalSum = 0;
        List<Double> ceilings = new ArrayList<>();
        for (Vector ray : RAYS) {
            boolean upward = ray.getY() > UP_Y;
            double range = upward ? UP_RANGE : SIDE_RANGE;
            var hit = world.rayTraceBlocks(eye, ray, range, FluidCollisionMode.NEVER, true, BLOCKS_SOUND);
            double distance = hit == null ? range : hit.getHitPosition().distance(eye.toVector());
            if (upward) {
                up++;
                if (hit == null) open++;
            }
            if (ray.getY() > CEILING_Y) ceilings.add(distance);
            if (Math.abs(ray.getY()) < HORIZONTAL_Y) {
                horizontal++;
                horizontalSum += Math.min(distance, SIDE_RANGE);
            }
        }
        ceilings.sort(null);
        double ceiling = ceilings.isEmpty() ? UP_RANGE : ceilings.get(ceilings.size() / 2);
        int solidAbove = 0;
        int top = Math.min(world.getMaxHeight(), eye.getBlockY() + (int) UP_RANGE);
        for (int by = eye.getBlockY() + 1; by < top && solidAbove < AcousticZone.UNDERGROUND_SOLID; by++) {
            Block block = world.getBlockAt(eye.getBlockX(), by, eye.getBlockZ());
            if (block.getType().isSolid() && BLOCKS_SOUND.test(block)) solidAbove++;
        }
        return AcousticZone.classify(open, up, solidAbove, ceiling, horizontal > 0 ? horizontalSum / horizontal : SIDE_RANGE);
    }

    private void loadOverrides(JavaPlugin plugin) {
        if (!overridesFile.exists()) return;
        try {
            List<Override> loaded = gson.fromJson(Files.readString(overridesFile.toPath(), StandardCharsets.UTF_8),
                    new TypeToken<List<Override>>() {}.getType());
            if (loaded != null) overrides.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("acoustics_overrides.json を読めませんでした: " + e.getMessage());
        }
    }

    private void saveOverrides() {
        try {
            overridesFile.getParentFile().mkdirs();
            Files.writeString(overridesFile.toPath(), gson.toJson(overrides), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("acoustics_overrides.json を書けませんでした", e);
        }
    }
}
