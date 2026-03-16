package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RaidModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, RaidMap> maps = new HashMap<>();
    private final Map<String, RaidInstance> activeRaids = new HashMap<>();
    private final Map<String, Set<UUID>> raidQueues = new HashMap<>();
    private final File mapsFolder;
    private final File stateFile;

    public static final int CYCLE_DURATION = 600; // 10 minutes (600 seconds)
    private int globalTimeLeft; // seconds
    private BukkitRunnable globalTimerTask;
    private BossBar queueBossBar;

    public RaidModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "raids");
        this.stateFile = new File(plugin.getDataFolder(), "raid_state.json");
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs();
        }
    }

    @Override
    public void onEnable(ServiceContainer container) {
        // Initialize BossBar
        queueBossBar = BossBar.bossBar(
                Component.text("出撃準備中...", NamedTextColor.YELLOW),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );

        loadMaps();
        loadState(); // 状態の復元
        startGlobalTimer();
        
        // 1分ごとのオートセーブ
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveState, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        saveState(); // 終了時に保存
        stopAllRaids();
        
        // Cleanup BossBar
        if (queueBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(queueBossBar);
            }
        }

        if (globalTimerTask != null) {
            globalTimerTask.cancel();
        }
    }

    private void saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("globalTimeLeft", globalTimeLeft);
        
        Map<String, List<String>> raidPlayers = new HashMap<>();
        for (Map.Entry<String, RaidInstance> entry : activeRaids.entrySet()) {
            List<String> uuids = new ArrayList<>();
            for (UUID uuid : entry.getValue().getParticipantUuids()) {
                uuids.add(uuid.toString());
            }
            raidPlayers.put(entry.getKey(), uuids);
        }
        state.put("activeRaids", raidPlayers);

        try (FileWriter writer = new FileWriter(stateFile)) {
            gson.toJson(state, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save raid state.");
        }
    }

    private void loadState() {
        if (!stateFile.exists()) return;
        try (FileReader reader = new FileReader(stateFile)) {
            Map<String, Object> state = gson.fromJson(reader, Map.class);
            if (state == null) return;

            if (state.containsKey("globalTimeLeft")) {
                this.globalTimeLeft = ((Double) state.get("globalTimeLeft")).intValue();
            }

            if (state.containsKey("activeRaids")) {
                Map<String, List<String>> raidPlayers = (Map<String, List<String>>) state.get("activeRaids");
                for (Map.Entry<String, List<String>> entry : raidPlayers.entrySet()) {
                    String mapId = entry.getKey();
                    RaidMap map = maps.get(mapId);
                    if (map == null) continue;

                    Set<UUID> uuids = new HashSet<>();
                    for (String uuidStr : entry.getValue()) {
                        uuids.add(UUID.fromString(uuidStr));
                    }

                    if (!uuids.isEmpty()) {
                        RaidInstance raid = new RaidInstance(plugin, map, new ArrayList<>());
                        raid.restoreParticipants(uuids); // 復元用メソッド
                        activeRaids.put(mapId, raid);
                    }
                }
            }
            stateFile.delete(); // 読み込み後は削除
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load raid state.");
        }
    }

    private void startGlobalTimer() {
        // If state was not loaded (e.g. fresh start), ensure it's set
        if (globalTimeLeft <= 0) {
            this.globalTimeLeft = CYCLE_DURATION;
        }

        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (globalTimeLeft <= 0) {
                    onCycleEnd();
                    globalTimeLeft = CYCLE_DURATION;
                }
                
                // 待機中のプレイヤーに通知 (例: 1分前、10秒前)
                notifyQueuedPlayers();
                
                // Update BossBar
                updateBossBar();

                globalTimeLeft--;
            }
        };
        globalTimerTask.runTaskTimer(plugin, 0, 20);
    }

    private void updateBossBar() {
        if (queueBossBar == null) return;

        float progress = (float) globalTimeLeft / (float) CYCLE_DURATION;
        // Clamp progress
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        
        queueBossBar.progress(progress);
        queueBossBar.name(Component.text("出撃まであと " + formatTime(globalTimeLeft), NamedTextColor.YELLOW));
    }

    private void notifyQueuedPlayers() {
        if (globalTimeLeft == 60 || globalTimeLeft <= 10 && globalTimeLeft > 0) {
            String message = "§e[RAID] §f出撃まであと §b" + globalTimeLeft + "秒";
            for (Set<UUID> queue : raidQueues.values()) {
                for (UUID uuid : queue) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(message);
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private void onCycleEnd() {
        plugin.getLogger().info("Raid cycle ended. Starting new raids and MIA processing...");

        // 1. 既存レイドの終了処理 (MIA)
        for (RaidInstance raid : new ArrayList<>(activeRaids.values())) {
            raid.handleMIA();
            raid.stop();
        }
        activeRaids.clear();

        // 2. 物資リセット
        if (plugin.getLootManager() != null) {
            plugin.getLootManager().refillAllContainers();
        }

        // Hide BossBar for all currently queued players before transferring them
        for (Set<UUID> queue : raidQueues.values()) {
            for (UUID uuid : queue) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.hideBossBar(queueBossBar);
                }
            }
        }

        // 3. 一斉スタート (キューの掃き出し)
        for (Map.Entry<String, Set<UUID>> entry : raidQueues.entrySet()) {
            String mapId = entry.getKey();
            Set<UUID> playerUuids = entry.getValue();
            if (playerUuids.isEmpty()) continue;

            List<Player> participants = new ArrayList<>();
            for (UUID uuid : playerUuids) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) participants.add(p);
            }

            if (!participants.isEmpty()) {
                RaidMap map = maps.get(mapId);
                if (map != null) {
                    RaidInstance raid = new RaidInstance(plugin, map, participants);
                    activeRaids.put(mapId, raid);
                }
            }
        }
        raidQueues.clear();

        Bukkit.broadcast(Component.text("新たなレイドサイクルが開始されました。出撃隊は各マップへ転送されました。", NamedTextColor.AQUA));
    }

    /**
     * キューに参加
     */
    public boolean joinQueue(Player player, String mapId) {
        if (!maps.containsKey(mapId)) return false;
        
        // すでにどこかのレイドに参加中かチェック
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) {
                player.sendMessage(Component.text("すでにレイドに参加中です。", NamedTextColor.RED));
                return false;
            }
        }

        // すでに別のキューにいる場合は削除
        leaveQueue(player);

        raidQueues.computeIfAbsent(mapId, k -> new HashSet<>()).add(player.getUniqueId());
        player.sendMessage(Component.text(mapId + " の出撃待機列に参加しました。出撃まであと " + formatTime(globalTimeLeft), NamedTextColor.GREEN));
        
        // Show BossBar
        if (queueBossBar != null) {
            player.showBossBar(queueBossBar);
        }
        
        return true;
    }

    /**
     * キューから離脱
     */
    public void leaveQueue(Player player) {
        for (Set<UUID> queue : raidQueues.values()) {
            if (queue.remove(player.getUniqueId())) {
                // Hide BossBar if they were in a queue
                if (queueBossBar != null) {
                    player.hideBossBar(queueBossBar);
                }
            }
        }
    }

    public boolean isInQueue(Player player) {
        for (Set<UUID> queue : raidQueues.values()) {
            if (queue.contains(player.getUniqueId())) return true;
        }
        return false;
    }

    public boolean isInRaid(Player player) {
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) return true;
        }
        return false;
    }

    public String getQueuedMap(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : raidQueues.entrySet()) {
            if (entry.getValue().contains(player.getUniqueId())) return entry.getKey();
        }
        return null;
    }

    public int getQueueCount(String mapId) {
        Set<UUID> queue = raidQueues.get(mapId);
        return queue != null ? queue.size() : 0;
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public int getGlobalTimeLeft() {
        return globalTimeLeft;
    }

    public void forceStartCycle() {
        onCycleEnd();
        this.globalTimeLeft = CYCLE_DURATION;
        updateBossBar();
    }

    public RaidInstance getActiveRaid(String mapId) {
        return activeRaids.get(mapId);
    }

    public void stopAllRaids() {
        for (RaidInstance raid : new ArrayList<>(activeRaids.values())) {
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
                // Mark for failure effect on respawn
                player.setMetadata("raid_death_failure", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                return;
            }
        }
    }

    public void applyFailureEffect(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0, false, false, false));
        player.playSound(player.getLocation(), "minecraft:custom.death", org.bukkit.SoundCategory.MASTER, 2.0f, 1.0f);
    }

    public void onPlayerQuit(Player player) {
        leaveQueue(player);
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

    public Map<String, RaidMap> getMaps() {
        return maps;
    }

    public Collection<RaidInstance> getActiveRaids() {
        return activeRaids.values();
    }

    public void saveMap(RaidMap map) {
        File file = new File(mapsFolder, map.getMapId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(map, writer);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
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
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                plugin.getLogger().warning("Failed to load raid map from: " + file.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + maps.size() + " raid maps.");
    }
}
