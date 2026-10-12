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
 *   <li>自動の判定: 初めて音が鳴った {@link #CELL} ブロック角のマスで、まわりに光線を飛ばして決め、覚えておく
 *       (空が見えるか・頭の上の重なり・天井の高さ・壁までの距離)。サーバーを再起動すると判定し直す</li>
 *   <li>マップを改変したら /acoustics clear で、覚えた判定を捨てて判定し直させる</li>
 *   <li>自動の判定が外れる所 (吹き抜け・半屋外の駐車場など) は、範囲で上書きする (/acoustics override)。
 *       上書きはワールド名ごとに acoustics_overrides.json に保存する (レイドは決まった名前のワールドを使う)</li>
 * </ul>
 */
public final class AcousticMap {

    /** 判定を覚えるマスの大きさ (ブロック) */
    static final int CELL = 2;
    private static final double UP_RANGE = 64.0;
    private static final double SIDE_RANGE = 32.0;
    /** 上を向いた光線: 真上と、真上から 25° 傾けた 4 本 */
    private static final Vector[] UP_RAYS = {
            new Vector(0, 1, 0),
            new Vector(0.42, 0.91, 0), new Vector(-0.42, 0.91, 0), new Vector(0, 0.91, 0.42), new Vector(0, 0.91, -0.42),
    };
    /** 葉は音を遮らないものとして扱う (木の下を屋内にしない) */
    private static final Predicate<Block> BLOCKS_SOUND = block -> !Tag.LEAVES.isTagged(block.getType());

    private static AcousticMap instance;

    private final File overridesFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Map<Long, AcousticZone>> cells = new HashMap<>();
    private final List<Override> overrides = new ArrayList<>();

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
        return cells.computeIfAbsent(world.getUID(), id -> new HashMap<>())
                .computeIfAbsent(cellKey(x, y, z), key -> detect(world, x, y, z));
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
        int open = 0;
        double ceiling = UP_RANGE;
        for (int i = 0; i < UP_RAYS.length; i++) {
            var hit = world.rayTraceBlocks(eye, UP_RAYS[i], UP_RANGE, FluidCollisionMode.NEVER, true, BLOCKS_SOUND);
            if (hit == null) {
                open++;
            } else if (i == 0) {
                ceiling = hit.getHitPosition().distance(eye.toVector());
            }
        }
        int solidAbove = 0;
        int top = Math.min(world.getMaxHeight(), eye.getBlockY() + (int) UP_RANGE);
        for (int by = eye.getBlockY() + 1; by < top && solidAbove < AcousticZone.UNDERGROUND_SOLID; by++) {
            Block block = world.getBlockAt(eye.getBlockX(), by, eye.getBlockZ());
            if (block.getType().isSolid() && BLOCKS_SOUND.test(block)) solidAbove++;
        }
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            var hit = world.rayTraceBlocks(eye, new Vector(Math.cos(angle), 0, Math.sin(angle)), SIDE_RANGE, FluidCollisionMode.NEVER, true, BLOCKS_SOUND);
            sum += hit == null ? SIDE_RANGE : hit.getHitPosition().distance(eye.toVector());
        }
        return AcousticZone.classify(open, UP_RAYS.length, solidAbove, ceiling, sum / 8);
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
