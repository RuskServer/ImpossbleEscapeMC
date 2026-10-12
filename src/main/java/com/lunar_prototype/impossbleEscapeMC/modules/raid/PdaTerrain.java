package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出撃時の PDA ({@link PdaIntro}) の戦術マップ: マップのワールドを上から見て、範囲をセル (横6 x 縦8 の文字) に分け、
 * セルごとに建物・道路・水辺・木・フェンス・高台・地面を記号にする。記号はリソースパックのフォント iem:pda のセル
 * (IEM_shader/tools/raid_pda.py の CELLS と同じ記号) で描く。
 * <p>
 * 上から見るにはチャンクの読み込みが要るので、マップごとに一度だけ、チャンクを非同期で読み込んでから作り、覚えておく。
 * できるまでの間は null (地形無しで印だけ出す)
 */
final class PdaTerrain {

    /** 範囲の周りに足す余白 (ブロック) */
    private static final int MARGIN = 12;
    /** セルの中で高さを調べる点の数 (縦横) */
    private static final int SAMPLES = 3;
    /** 記号を決めるために、表示する範囲の外側も調べるセル数 (道路の向きなど、周りを見て決める記号のため) */
    private static final int CONTEXT = 6;

    /** 作った地形 (セルの記号と、ワールドの座標との対応) */
    record Grid(char[][] cells, double minX, double minZ, double blocksPerCellX, double blocksPerCellZ) {
        int cols() {
            return cells[0].length;
        }

        int rows() {
            return cells.length;
        }
    }

    private enum Kind { GROUND, ROAD, BUILDING, WATER, TREE, FENCE, HIGH }

    private static final Set<Material> ROAD = new HashSet<>(Arrays.asList(
            Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE_POWDER,
            Material.STONE, Material.SMOOTH_STONE, Material.SMOOTH_STONE_SLAB, Material.ANDESITE, Material.POLISHED_ANDESITE,
            Material.GRAVEL, Material.DIRT_PATH, Material.COBBLESTONE, Material.STONE_SLAB, Material.ANDESITE_SLAB,
            Material.POLISHED_ANDESITE_SLAB, Material.COBBLESTONE_SLAB, Material.COAL_BLOCK, Material.BLACKSTONE,
            Material.POLISHED_BLACKSTONE, Material.TUFF, Material.DEEPSLATE_TILES, Material.CRACKED_STONE_BRICKS));

    private static final Map<String, Grid> TACTICAL = new ConcurrentHashMap<>();
    private static final Map<String, Grid> VIGNETTES = new ConcurrentHashMap<>();
    private static final Set<String> PREPARING = ConcurrentHashMap.newKeySet();

    private PdaTerrain() {
    }

    /** マップ全体の戦術マップ。まだできていなければ null (作り始める) */
    static Grid tactical(RaidMap map, int cols, int rows) {
        Grid grid = TACTICAL.get(map.getMapId());
        if (grid == null) prepare(map, cols, rows);
        return grid;
    }

    /** 脱出地点の周りの拡大図 (地点を中央に)。まだできていなければ null */
    static Grid vignette(RaidMap map, RaidMap.ExtractionPoint point) {
        return VIGNETTES.get(map.getMapId() + "/" + point.getName());
    }

    /** マップの範囲のチャンクを非同期で読み込み、戦術マップと脱出地点の拡大図を作る (マップごとに一度) */
    static void prepare(RaidMap map, int cols, int rows) {
        World world = map.getWorldName() == null ? null : Bukkit.getWorld(map.getWorldName());
        if (world == null || TACTICAL.containsKey(map.getMapId()) || !PREPARING.add(map.getMapId())) return;

        double[] bounds = bounds(map, world);
        if (bounds == null) {
            PREPARING.remove(map.getMapId());
            return;
        }
        // セルは横6 x 縦8 の文字なので、1セルが受け持つブロックも 6:8 にする (地図がゆがまないように)
        double scale = Math.max((bounds[2] - bounds[0]) / (cols * 6.0), (bounds[3] - bounds[1]) / (rows * 8.0));
        double cellX = scale * 6.0, cellZ = scale * 8.0;
        double minX = (bounds[0] + bounds[2]) / 2 - cellX * cols / 2;
        double minZ = (bounds[1] + bounds[3]) / 2 - cellZ * rows / 2;

        List<CompletableFuture<?>> loads = new ArrayList<>();
        // 記号を決めるために外側 CONTEXT セルぶんも見るので、そこまで読み込む
        for (int cx = (int) Math.floor(minX - cellX * CONTEXT) >> 4; cx <= (int) Math.floor(minX + cellX * (cols + CONTEXT)) >> 4; cx++) {
            for (int cz = (int) Math.floor(minZ - cellZ * CONTEXT) >> 4; cz <= (int) Math.floor(minZ + cellZ * (rows + CONTEXT)) >> 4; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                // 読み込みの完了はメインスレッドとは限らないので、ブロックを見るのはメインスレッドで
                Bukkit.getScheduler().runTask(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.getInstance(), () -> {
                    try {
                        TACTICAL.put(map.getMapId(), build(world, minX, minZ, cellX, cellZ, cols, rows));
                        for (RaidMap.ExtractionPoint point : map.getExtractionPoints()) {
                            Location at = point.getLocation(world.getName());
                            if (at == null) continue;
                            // 拡大図: 1セル 1.5 x 2 ブロック、横28 x 縦4 セル
                            double vx = 1.5, vz = 2.0;
                            VIGNETTES.put(map.getMapId() + "/" + point.getName(),
                                    build(world, at.getX() - vx * 14, at.getZ() - vz * 2, vx, vz, 28, 4));
                        }
                    } finally {
                        PREPARING.remove(map.getMapId());
                    }
                }));
    }

    /** マップの地点 (スポーン・脱出・SCAV・コンテナ) を囲む範囲 {minX, minZ, maxX, maxZ} */
    private static double[] bounds(RaidMap map, World world) {
        List<Location> points = new ArrayList<>(map.getSpawnPoints());
        for (RaidMap.ExtractionPoint point : map.getExtractionPoints()) points.add(point.getLocation(world.getName()));
        for (RaidMap.ScavSpawnPoint point : map.getScavSpawnPoints()) points.add(point.getLocation(world.getName()));
        for (RaidMap.LootContainer point : map.getLootContainers()) points.add(point.getLocation(world.getName()));
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Location point : points) {
            if (point == null) continue;
            minX = Math.min(minX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxX = Math.max(maxX, point.getX());
            maxZ = Math.max(maxZ, point.getZ());
        }
        if (minX > maxX) return null;
        return new double[]{minX - MARGIN, minZ - MARGIN, maxX + MARGIN, maxZ + MARGIN};
    }

    private static Grid build(World world, double minX, double minZ, double cellX, double cellZ, int cols, int rows) {
        // 外側 CONTEXT セルぶんも含めて調べ、記号を決めてから表示する範囲を切り出す
        Kind[][] wide = kinds(world, minX - cellX * CONTEXT, minZ - cellZ * CONTEXT, cellX, cellZ, cols + CONTEXT * 2, rows + CONTEXT * 2);
        char[][] cells = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c] = symbol(wide, r + CONTEXT, c + CONTEXT);
            }
        }
        return new Grid(cells, minX, minZ, cellX, cellZ);
    }

    private static Kind[][] kinds(World world, double minX, double minZ, double cellX, double cellZ, int cols, int rows) {
        int[][] heights = new int[rows * SAMPLES][cols * SAMPLES];
        Material[][] tops = new Material[rows * SAMPLES][cols * SAMPLES];
        List<Integer> all = new ArrayList<>();
        for (int sy = 0; sy < rows * SAMPLES; sy++) {
            for (int sx = 0; sx < cols * SAMPLES; sx++) {
                int x = (int) Math.floor(minX + (sx + 0.5) * cellX / SAMPLES);
                int z = (int) Math.floor(minZ + (sy + 0.5) * cellZ / SAMPLES);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    heights[sy][sx] = Integer.MIN_VALUE;
                    continue;
                }
                Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
                heights[sy][sx] = top.getY();
                tops[sy][sx] = top.getType();
                all.add(top.getY());
            }
        }
        // 地面の高さ: 低い方から4割の高さ (屋根や山を地面とみなさないように)
        all.sort(Integer::compare);
        int ground = all.isEmpty() ? 64 : all.get(all.size() * 4 / 10);

        Kind[][] kinds = new Kind[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int[] votes = new int[Kind.values().length];
                boolean known = false;
                for (int sy = r * SAMPLES; sy < (r + 1) * SAMPLES; sy++) {
                    for (int sx = c * SAMPLES; sx < (c + 1) * SAMPLES; sx++) {
                        if (heights[sy][sx] == Integer.MIN_VALUE) continue;
                        known = true;
                        votes[classify(tops[sy][sx], heights[sy][sx] - ground).ordinal()]++;
                    }
                }
                kinds[r][c] = known ? majority(votes) : null;
            }
        }
        return kinds;
    }

    private static Kind classify(Material top, int aboveGround) {
        if (top == Material.WATER || top == Material.ICE || top == Material.KELP || top == Material.SEAGRASS) return Kind.WATER;
        if (Tag.LEAVES.isTagged(top) || Tag.LOGS.isTagged(top)) return Kind.TREE;
        if (Tag.FENCES.isTagged(top) || Tag.WALLS.isTagged(top) || top == Material.IRON_BARS || top.name().endsWith("CHAIN")
                || top.name().endsWith("GLASS_PANE")) return Kind.FENCE;
        if (aboveGround >= 3 && !Tag.DIRT.isTagged(top) && top != Material.GRASS_BLOCK && top != Material.STONE) return Kind.BUILDING;
        if (aboveGround >= 6) return Kind.HIGH;
        if (ROAD.contains(top)) return Kind.ROAD;
        return Kind.GROUND;
    }

    private static Kind majority(int[] votes) {
        int best = 0;
        for (int i = 1; i < votes.length; i++) {
            if (votes[i] > votes[best]) best = i;
        }
        return Kind.values()[best];
    }

    private static char symbol(Kind[][] kinds, int r, int c) {
        Kind kind = kinds[r][c];
        if (kind == null) return ' ';
        return switch (kind) {
            case WATER -> '~';
            case TREE -> 'T';
            case FENCE -> 'x';
            case HIGH -> '^';
            case BUILDING -> {
                // 建物の縁は枠、中は塗りつぶし
                boolean edge = !is(kinds, r - 1, c, Kind.BUILDING) || !is(kinds, r + 1, c, Kind.BUILDING)
                        || !is(kinds, r, c - 1, Kind.BUILDING) || !is(kinds, r, c + 1, Kind.BUILDING);
                yield edge ? '#' : 'B';
            }
            case ROAD -> {
                // 向きは道路が縦横にどれだけ続くかで決める (道幅が数セルある拡大図でも、交差点だらけにならないように)
                int horizontal = run(kinds, r, c, 0, 1) + run(kinds, r, c, 0, -1);
                int vertical = run(kinds, r, c, 1, 0) + run(kinds, r, c, -1, 0);
                yield horizontal >= vertical * 2 ? '=' : vertical >= horizontal * 2 ? '|' : '+';
            }
            case GROUND -> {
                // 地面はまばらな点 (同じ模様が並ばないよう、位置で変える)
                int h = (r * 73 + c * 151) % 10;
                yield h < 3 ? '.' : h < 5 ? ':' : h < 6 ? ',' : ' ';
            }
        };
    }

    /** (r, c) から (dr, dc) の向きに続く道路のセル数 (自分を含まない、最大 CONTEXT) */
    private static int run(Kind[][] kinds, int r, int c, int dr, int dc) {
        int n = 0;
        while (n < CONTEXT && is(kinds, r + dr * (n + 1), c + dc * (n + 1), Kind.ROAD)) n++;
        return n;
    }

    private static boolean is(Kind[][] kinds, int r, int c, Kind kind) {
        return r >= 0 && r < kinds.length && c >= 0 && c < kinds[0].length && kinds[r][c] == kind;
    }
}
