package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class RaidMap {
    private final String mapId;
    private String worldName;
    private final List<double[]> spawnPoints = new ArrayList<>();
    // 座標の配列は旧JSONと共通。IDは削除しても詰め直さず、再利用しない。
    private List<Integer> spawnIds = new ArrayList<>();
    private int nextSpawnId = 1;
    private List<RaidRadioZone> highThreatZones = new ArrayList<>();

    List<RaidRadioZone> highThreatZones() {
        if(highThreatZones==null)highThreatZones=new ArrayList<>();
        return List.copyOf(highThreatZones);
    }
    boolean addHighThreatZone(RaidRadioZone zone) {
        if(highThreatZones().stream().anyMatch(z->z.id().equals(zone.id())))return false;
        highThreatZones.add(zone);return true;
    }
    boolean removeHighThreatZone(String id) {
        highThreatZones();return highThreatZones.removeIf(z->z.id().equals(id));
    }

    public record SpawnEntry(int id, double x, double y, double z, float yaw, float pitch) {}

    public boolean initializeSpawnIds() {
        boolean changed = false;
        nextSpawnId = Math.max(1, nextSpawnId);
        if (spawnIds == null) { spawnIds = new ArrayList<>(); changed = true; }
        for (int id : spawnIds) nextSpawnId = Math.max(nextSpawnId, id + 1);
        while (spawnIds.size() < spawnPoints.size()) { spawnIds.add(nextSpawnId++); changed = true; }
        return changed;
    }

    public List<SpawnEntry> spawnEntries() {
        initializeSpawnIds();
        List<SpawnEntry> entries = new ArrayList<>();
        for (int i = 0; i < spawnPoints.size(); i++) {
            double[] c = spawnPoints.get(i);
            entries.add(new SpawnEntry(spawnIds.get(i), c[0], c[1], c[2], (float)c[3], (float)c[4]));
        }
        return List.copyOf(entries);
    }

    public boolean removeSpawnPoint(int id) {
        initializeSpawnIds();
        int index = spawnIds.indexOf(id);
        if (index < 0) return false;
        spawnIds.remove(index);
        spawnPoints.remove(index);
        return true;
    }
    private final List<ExtractionPoint> extractionPoints = new ArrayList<>();
    private final List<ScavSpawnPoint> scavSpawnPoints = new ArrayList<>();
    private List<LootContainer> lootContainers = new ArrayList<>(); // Changed to non-final for safety
    /** 地面ルートの地点 (古い JSON には無いので null になりうる) */
    private List<GroundLootPoint> groundLoot = new ArrayList<>();

    public RaidMap(String mapId) {
        this.mapId = mapId;
    }

    public String getMapId() {
        return mapId;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    private void ensureWorld(Location loc) {
        if (loc.getWorld() == null) {
            throw new IllegalArgumentException("Location world must not be null");
        }
        if (worldName == null) {
            worldName = loc.getWorld().getName();
        } else if (!isWorld(loc.getWorld())) {
            throw new IllegalArgumentException("RaidMap cannot contain points from multiple worlds (map: " + worldName
                    + ", location: " + loc.getWorld().getName() + ")");
        }
    }

    /**
     * world がこのマップのワールドか。保存してある名前と今の名前の文字列が違っても (サーバーの版でワールドの名前の形が変わった時など)、
     * その名前で探したワールドが同じならこのマップのワールドとみなす (レイドも同じ探し方でワールドを使っている)
     */
    public boolean isWorld(World world) {
        if (world == null || worldName == null) return false;
        return worldName.equals(world.getName()) || world.equals(Bukkit.getWorld(worldName));
    }

    public void addSpawnPoint(Location loc) {
        ensureWorld(loc);
        initializeSpawnIds();
        spawnPoints.add(locationToCoords(loc));
        spawnIds.add(nextSpawnId++);
    }

    public void clearSpawnPoints() {
        spawnPoints.clear();
        if (spawnIds != null) spawnIds.clear();
    }

    public List<Location> getSpawnPoints() {
        World world = Bukkit.getWorld(worldName);
        List<Location> locations = new ArrayList<>();
        if (world == null) return locations;

        for (double[] c : spawnPoints) {
            locations.add(new Location(world, c[0], c[1], c[2], (float) c[3], (float) c[4]));
        }
        return locations;
    }

    public void addExtractionPoint(Location loc, String name, double radius) {
        ensureWorld(loc);
        extractionPoints.add(new ExtractionPoint(name, locationToCoords(loc), radius));
    }

    public List<ExtractionPoint> getExtractionPoints() {
        return extractionPoints;
    }

    public void addScavSpawnPoint(Location loc, boolean permanent) {
        ensureWorld(loc);
        scavSpawnPoints.add(new ScavSpawnPoint(locationToCoords(loc), permanent));
    }

    public void clearScavSpawnPoints() {
        scavSpawnPoints.clear();
    }

    public List<ScavSpawnPoint> getScavSpawnPoints() {
        return scavSpawnPoints;
    }

    public void addLootContainer(Location loc, String tableId) {
        ensureWorld(loc);
        if (lootContainers == null) {
            lootContainers = new ArrayList<>();
        }
        lootContainers.add(new LootContainer(locationToCoords(loc), tableId));
    }

    /** block と同じブロックの位置のルートコンテナの登録を外す (このマップのワールドの時だけ)。外した数 */
    public int removeLootContainersAt(Location block) {
        if (lootContainers == null || !isWorld(block.getWorld())) return 0;
        int before = lootContainers.size();
        lootContainers.removeIf(lc -> lc.isAt(block));
        return before - lootContainers.size();
    }

    /** 地面ルートの地点を足す (block はアイテムを置くブロックの位置。そのブロックの床の真ん中に置く) */
    public void addGroundLoot(Location block, String crateId) {
        ensureWorld(block);
        if (groundLoot == null) groundLoot = new ArrayList<>();
        groundLoot.add(new GroundLootPoint(new double[]{block.getBlockX(), block.getBlockY(), block.getBlockZ()}, crateId));
    }

    /** block と同じブロックの位置の地面ルートの地点を外す (このマップのワールドの時だけ)。外した数 */
    public int removeGroundLootAt(Location block) {
        if (groundLoot == null || !isWorld(block.getWorld())) return 0;
        int before = groundLoot.size();
        groundLoot.removeIf(p -> p.isAt(block));
        return before - groundLoot.size();
    }

    public List<GroundLootPoint> getGroundLoot() {
        if (groundLoot == null) groundLoot = new ArrayList<>();
        return groundLoot;
    }

    private List<LootDrawer> lootDrawers = new ArrayList<>();

    /** 古いマップJSONでは未定義。既存コンテナとは別の登録を持つ。 */
    public List<LootDrawer> getLootDrawers() {
        if (lootDrawers == null) lootDrawers = new ArrayList<>();
        return lootDrawers;
    }

    public static final class LootDrawer {
        public final String id, crateId;
        public final int x, y, z, facing;
        public long generation;
        public LootDrawer(String id,String crateId,int x,int y,int z,int facing) {
            this.id=id;this.crateId=crateId;this.x=x;this.y=y;this.z=z;this.facing=facing;
        }
    }

    private List<LootLocker> lootLockers=new ArrayList<>();

    public List<LootLocker> getLootLockers() {
        if(lootLockers==null)lootLockers=new ArrayList<>();
        return lootLockers;
    }
    public static final class LootLocker {
        public final String id;
        public final int x,y,z,facing;
        public long generation;
        public LootLocker(String id,int x,int y,int z,int facing) {
            this.id=id;this.x=x;this.y=y;this.z=z;this.facing=facing;
        }
    }

    public List<LootContainer> getLootContainers() {
        if (lootContainers == null) {
            lootContainers = new ArrayList<>();
        }
        return lootContainers;
    }

    private double[] locationToCoords(Location loc) {
        return new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
    }

    public static class ExtractionPoint {
        private final String name;
        private final double[] coords;
        private final double radius;

        public ExtractionPoint(String name, double[] coords, double radius) {
            this.name = name;
            this.coords = coords;
            this.radius = radius;
        }

        public String getName() {
            return name;
        }

        public Location getLocation(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, coords[0], coords[1], coords[2]);
        }

        public double getRadius() {
            return radius;
        }
    }

    public static class ScavSpawnPoint {
        private final double[] coords;
        private final boolean permanent;

        public ScavSpawnPoint(double[] coords, boolean permanent) {
            this.coords = coords;
            this.permanent = permanent;
        }

        public Location getLocation(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, coords[0], coords[1], coords[2], (float) coords[3], (float) coords[4]);
        }

        public boolean isPermanent() {
            return permanent;
        }
    }

    public static class LootCrateReference {
        // Alias for compatibility if needed, but we use LootContainer class name for now
    }

    /** 地面ルートの地点: レイドの始めに、クレートと同じ種類 (crateId) の中身から 1 個を床に置く */
    public static class GroundLootPoint {
        private final double[] coords;   // ブロックの位置
        private final String crateId;

        public GroundLootPoint(double[] coords, String crateId) {
            this.coords = coords;
            this.crateId = crateId;
        }

        /** アイテムを置く所 (ブロックの床の真ん中) */
        public Location getLocation(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, coords[0] + 0.5, coords[1] + 0.05, coords[2] + 0.5);
        }

        public String getCrateId() {
            return crateId;
        }

        boolean isAt(Location block) {
            return (int) coords[0] == block.getBlockX() && (int) coords[1] == block.getBlockY() && (int) coords[2] == block.getBlockZ();
        }
    }

    public static class LootContainer {
        private final double[] coords;
        private final String tableId;

        public LootContainer(double[] coords, String tableId) {
            this.coords = coords;
            this.tableId = tableId;
        }

        public Location getLocation(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, coords[0], coords[1], coords[2]);
        }

        public String getTableId() {
            return tableId;
        }

        /** block と同じブロックの位置か */
        boolean isAt(Location block) {
            return (int) Math.floor(coords[0]) == block.getBlockX() && (int) Math.floor(coords[1]) == block.getBlockY()
                    && (int) Math.floor(coords[2]) == block.getBlockZ();
        }
    }
}
