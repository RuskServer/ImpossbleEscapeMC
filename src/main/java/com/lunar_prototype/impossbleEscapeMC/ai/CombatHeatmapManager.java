package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 戦闘記録に基づき、座標ごとの「危険度」と「有利度」を管理するマネージャー。
 */
public class CombatHeatmapManager {
    private static final int GRID_SIZE = 2;
    
    public enum TraceType {
        DANGER(2.0f),      
        SUPPRESSION(0.5f), 
        SAFE(-1.0f);       

        final float weight;
        TraceType(float weight) { this.weight = weight; }
    }

    private static class GridKey {
        final int x, y, z;
        final String world;

        GridKey(Location loc) {
            this.x = Math.floorDiv(loc.getBlockX(), GRID_SIZE);
            this.y = Math.floorDiv(loc.getBlockY(), GRID_SIZE);
            this.z = Math.floorDiv(loc.getBlockZ(), GRID_SIZE);
            this.world = loc.getWorld().getName();
        }

        GridKey(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x * GRID_SIZE + (GRID_SIZE / 2.0), y * GRID_SIZE + (GRID_SIZE / 2.0), z * GRID_SIZE + (GRID_SIZE / 2.0));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GridKey gridKey = (GridKey) o;
            return x == gridKey.x && y == gridKey.y && z == gridKey.z && world.equals(gridKey.world);
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + world.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return world + "," + x + "," + y + "," + z;
        }
    }

    private static class GridData {
        float score = 0;
        long lastUpdate;

        GridData(float score) {
            this.score = score;
            this.lastUpdate = System.currentTimeMillis();
        }

        void add(float amount) {
            decay();
            score += amount;
            score = Math.max(-20.0f, Math.min(50.0f, score)); // 上限を50に引き上げ、下限も拡張
            lastUpdate = System.currentTimeMillis();
        }

        void decay() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdate;
            if (elapsed > 1000) {
                // 減衰を大幅に緩やかに (0.98 -> 0.998)
                // 0.998^300 (5分) ≒ 0.54 (半分残る)
                float decayFactor = (float) Math.pow(0.998, elapsed / 1000.0);
                score *= decayFactor;
                lastUpdate = now;
            }
        }
    }

    private static final Map<GridKey, GridData> heatmap = new ConcurrentHashMap<>();

    public static void record(Location loc, TraceType type, float intensity) {
        if (loc == null) return;
        GridKey key = new GridKey(loc);
        heatmap.computeIfAbsent(key, k -> new GridData(0)).add(type.weight * intensity);
    }

    public static float getScore(Location loc) {
        if (loc == null) return 0;
        GridKey key = new GridKey(loc);
        GridData data = heatmap.get(key);
        if (data == null) return 0;
        data.decay();
        return data.score;
    }

    /**
     * 指定された位置周辺の有効なスコアを持つ全グリッドとそのスコアを取得
     */
    public static Map<Location, Float> getNearbyScores(Location center, int radiusBlocks) {
        Map<Location, Float> results = new HashMap<>();
        int gridRadius = radiusBlocks / GRID_SIZE;
        GridKey centerKey = new GridKey(center);

        for (Map.Entry<GridKey, GridData> entry : heatmap.entrySet()) {
            GridKey key = entry.getKey();
            if (!key.world.equals(centerKey.world)) continue;
            
            if (Math.abs(key.x - centerKey.x) <= gridRadius &&
                Math.abs(key.y - centerKey.y) <= gridRadius &&
                Math.abs(key.z - centerKey.z) <= gridRadius) {
                
                entry.getValue().decay();
                if (Math.abs(entry.getValue().score) > 0.01f) { // 閾値をより小さく
                    Location loc = key.toLocation();
                    if (loc != null) results.put(loc, entry.getValue().score);
                }
            }
        }
        return results;
    }

    public static void recordLine(Location start, Vector direction, double distance, float intensity) {
        if (start == null || direction == null) return;
        Vector step = direction.clone().normalize().multiply(GRID_SIZE);
        for (double d = 0; d < distance; d += GRID_SIZE) {
            record(start.clone().add(step.clone().multiply(d / GRID_SIZE)), TraceType.SUPPRESSION, intensity);
        }
    }

    public static void cleanup() {
        heatmap.entrySet().removeIf(entry -> {
            entry.getValue().decay();
            return Math.abs(entry.getValue().score) < 0.01f; // 閾値を下げて維持しやすくする
        });
    }

    public static void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<GridKey, GridData> entry : heatmap.entrySet()) {
            if (Math.abs(entry.getValue().score) < 0.1f) continue;
            String path = "data." + (i++);
            config.set(path + ".world", entry.getKey().world);
            config.set(path + ".x", entry.getKey().x);
            config.set(path + ".y", entry.getKey().y);
            config.set(path + ".z", entry.getKey().z);
            config.set(path + ".score", entry.getValue().score);
        }
        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load(File file) {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("data");
        if (section == null) return;

        heatmap.clear();
        for (String key : section.getKeys(false)) {
            String world = section.getString(key + ".world");
            int x = section.getInt(key + ".x");
            int y = section.getInt(key + ".y");
            int z = section.getInt(key + ".z");
            float score = (float) section.getDouble(key + ".score");
            
            heatmap.put(new GridKey(world, x, y, z), new GridData(score));
        }
    }
}
