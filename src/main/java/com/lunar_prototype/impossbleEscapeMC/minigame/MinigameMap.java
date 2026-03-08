package com.lunar_prototype.impossbleEscapeMC.minigame;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class MinigameMap {
    private String name;
    private List<double[]> team1Spawns = new ArrayList<>();
    private List<double[]> team2Spawns = new ArrayList<>();
    private String worldName;

    public MinigameMap(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public void addSpawn(int team, Location loc) {
        this.worldName = loc.getWorld().getName();
        double[] coords = {loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        if (team == 1) team1Spawns.add(coords);
        else team2Spawns.add(coords);
    }

    public List<Location> getSpawns(int team) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return new ArrayList<>();
        
        List<double[]> coordsList = (team == 1) ? team1Spawns : team2Spawns;
        List<Location> locs = new ArrayList<>();
        for (double[] c : coordsList) {
            locs.add(new Location(world, c[0], c[1], c[2], (float) c[3], (float) c[4]));
        }
        return locs;
    }

    public void clearSpawns(int team) {
        if (team == 1) team1Spawns.clear();
        else team2Spawns.clear();
    }
}
