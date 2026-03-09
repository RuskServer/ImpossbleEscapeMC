package com.lunar_prototype.impossbleEscapeMC.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RaidManager {
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, RaidMap> maps = new HashMap<>();
    private final Map<String, RaidInstance> activeRaids = new HashMap<>();
    private final File mapsFolder;

    private int globalTimeLeft; // seconds
    private final int cycleDuration = 1500; // 25 minutes
    private BukkitRunnable globalTimerTask;

    public RaidManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "raids");
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs();
        }
        loadMaps();
        startGlobalTimer();
    }

    private void startGlobalTimer() {
        this.globalTimeLeft = cycleDuration;
        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (globalTimeLeft <= 0) {
                    onCycleEnd();
                    globalTimeLeft = cycleDuration;
                }
                globalTimeLeft--;
            }
        };
        globalTimerTask.runTaskTimer(plugin, 0, 20);
    }

    private void onCycleEnd() {
        plugin.getLogger().info("Raid cycle ended. Resetting all maps and loot...");
        for (RaidInstance raid : activeRaids.values()) {
            raid.handleMIA(); // Force MIA for those who didn't extract
            raid.resetCycle(); // Refresh extractions and state
        }
        // TODO: Trigger loot reset here
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("新たなレイドサイクルが開始されました。全ての物資が補充されました。", net.kyori.adventure.text.format.NamedTextColor.AQUA));
    }

    public int getGlobalTimeLeft() {
        return globalTimeLeft;
    }

    public void startRaid(String mapId, List<Player> participants) {
        RaidMap map = maps.get(mapId);
        if (map == null) return;
        
        RaidInstance raid = activeRaids.computeIfAbsent(mapId, id -> new RaidInstance(plugin, map, new ArrayList<>()));
        for (Player p : participants) {
            raid.joinPlayer(p);
        }
    }

    public RaidInstance getActiveRaid(String mapId) {
        return activeRaids.get(mapId);
    }

    public void stopAllRaids() {
        for (RaidInstance raid : activeRaids.values()) {
            raid.stop();
        }
        activeRaids.clear();
    }

    public void removeRaid(String mapId) {
        activeRaids.remove(mapId);
    }

    public void onPlayerDeath(Player player) {
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) {
                raid.onPlayerDeath(player);
                return;
            }
        }
    }

    public void onPlayerQuit(Player player) {
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) {
                raid.onPlayerQuit(player);
                return;
            }
        }
    }

    public void onScavDeath(UUID uuid) {
        for (RaidInstance raid : activeRaids.values()) {
            raid.onScavDeath(uuid);
        }
    }

    public void createMap(String mapId) {
        if (maps.containsKey(mapId)) return;
        RaidMap map = new RaidMap(mapId);
        maps.put(mapId, map);
        saveMap(map);
    }

    public void deleteMap(String mapId) {
        maps.remove(mapId);
        File file = new File(mapsFolder, mapId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    public RaidMap getMap(String mapId) {
        return maps.get(mapId);
    }

    public List<String> getMapIds() {
        return new ArrayList<>(maps.keySet());
    }

    public void saveMap(RaidMap map) {
        File file = new File(mapsFolder, map.getMapId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(map, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save raid map: " + map.getMapId());
            e.printStackTrace();
        }
    }

    private void loadMaps() {
        File[] files = mapsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                RaidMap map = gson.fromJson(reader, RaidMap.class);
                if (map != null) {
                    maps.put(map.getMapId(), map);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load raid map from: " + file.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + maps.size() + " raid maps.");
    }
}
