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
    private final List<ExtractionPoint> extractionPoints = new ArrayList<>();
    private final List<ScavSpawnPoint> scavSpawnPoints = new ArrayList<>();
    private List<LootContainer> lootContainers = new ArrayList<>(); // Changed to non-final for safety

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
        String currentWorldName = loc.getWorld().getName();
        if (worldName == null) {
            worldName = currentWorldName;
        } else if (!worldName.equals(currentWorldName)) {
            throw new IllegalArgumentException("RaidMap cannot contain points from multiple worlds");
        }
    }

    public void addSpawnPoint(Location loc) {
        ensureWorld(loc);
        spawnPoints.add(locationToCoords(loc));
    }

    public void clearSpawnPoints() {
        spawnPoints.clear();
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
    }
}
