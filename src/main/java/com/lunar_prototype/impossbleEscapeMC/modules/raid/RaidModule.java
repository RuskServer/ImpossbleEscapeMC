package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
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
    private final Map<String, Integer> mapTimers = new HashMap<>();
    private BukkitRunnable globalTimerTask;
    private final Map<String, BossBar> queueBossBars = new HashMap<>();

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
        loadMaps();
        
        // Initialize BossBars and Timers for each map
        for (String mapId : maps.keySet()) {
            mapTimers.put(mapId, CYCLE_DURATION);
            queueBossBars.put(mapId, BossBar.bossBar(
                    Component.text("出撃準備中 (" + mapId + ")...", NamedTextColor.YELLOW),
                    1.0f,
                    BossBar.Color.YELLOW,
                    BossBar.Overlay.PROGRESS
            ));
        }

        // Apply rotation offset if there are 2 maps (one starts at 5 mins)
        applyRotationOffsets();

        loadState(); // 状態の復元
        startGlobalTimer();
        
        // 1分ごとのオートセーブ
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveState, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        saveState(); // 終了時に保存
        stopAllRaids();
        
        // Cleanup BossBars
        for (BossBar bar : queueBossBars.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        queueBossBars.clear();

        if (globalTimerTask != null) {
            globalTimerTask.cancel();
        }
    }

    private void applyRotationOffsets() {
        List<String> mapIds = new ArrayList<>(maps.keySet());
        if (mapIds.size() >= 2) {
            // 2つのマップがある場合、2番目のマップを5分(300秒)ずらす
            String secondMap = mapIds.get(1);
            mapTimers.put(secondMap, 300);
            plugin.getLogger().info("Applied 5-minute offset to " + secondMap + " for rotation.");
        }
    }

    private void saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mapTimers", mapTimers);
        
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

            if (state.containsKey("mapTimers")) {
                Map<String, Double> loadedTimers = (Map<String, Double>) state.get("mapTimers");
                for (Map.Entry<String, Double> entry : loadedTimers.entrySet()) {
                    if (mapTimers.containsKey(entry.getKey())) {
                        mapTimers.put(entry.getKey(), entry.getValue().intValue());
                    }
                }
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
        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String mapId : new ArrayList<>(maps.keySet())) {
                    int timeLeft = mapTimers.getOrDefault(mapId, CYCLE_DURATION);
                    
                    if (timeLeft <= 0) {
                        onMapCycleEnd(mapId);
                        mapTimers.put(mapId, CYCLE_DURATION);
                    } else {
                        mapTimers.put(mapId, timeLeft - 1);
                    }
                    
                    updateMapBossBar(mapId);
                    notifyQueuedPlayers(mapId);
                }
            }
        };
        globalTimerTask.runTaskTimer(plugin, 0, 20);
    }

    private void updateMapBossBar(String mapId) {
        BossBar bar = queueBossBars.get(mapId);
        if (bar == null) return;

        int timeLeft = mapTimers.getOrDefault(mapId, 0);
        float progress = (float) timeLeft / (float) CYCLE_DURATION;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        
        bar.progress(progress);
        bar.name(Component.text("出撃まであと " + formatTime(timeLeft) + " (" + mapId + ")", NamedTextColor.YELLOW));
    }

    private void notifyQueuedPlayers(String mapId) {
        int timeLeft = mapTimers.getOrDefault(mapId, 0);
        if (timeLeft == 60 || (timeLeft <= 10 && timeLeft > 0)) {
            Set<UUID> queue = raidQueues.get(mapId);
            if (queue == null) return;

            String message = "§e[RAID] §f" + mapId + " 出撃まであと §b" + timeLeft + "秒";
            for (UUID uuid : queue) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(message);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }
        }
    }

    private void onMapCycleEnd(String mapId) {
        plugin.getLogger().info("Raid cycle ended for " + mapId + ". Starting new raids and MIA processing...");

        // 1. 既存レイドの終了処理 (MIA) - 対象マップのみ
        RaidInstance activeRaid = activeRaids.get(mapId);
        if (activeRaid != null) {
            activeRaid.handleMIA();
            activeRaid.stop();
            activeRaids.remove(mapId);
        }

        // 2. 物資リセット
        if (plugin.getLootManager() != null) {
            plugin.getLootManager().refillAllContainers();
        }

        // 3. 死体消去
        if (plugin.getCorpseManager() != null) {
            RaidMap map = maps.get(mapId);
            if (map != null) {
                org.bukkit.World world = Bukkit.getWorld(map.getWorldName());
                if (world != null) {
                    plugin.getCorpseManager().cleanup(world);
                }
            }
        }

        // 4. 一斉スタート (キューの掃き出し) - 対象マップのみ
        Set<UUID> playerUuids = raidQueues.get(mapId);
        if (playerUuids != null && !playerUuids.isEmpty()) {
            List<Player> participants = new ArrayList<>();
            BossBar bar = queueBossBars.get(mapId);

            for (UUID uuid : playerUuids) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    participants.add(p);
                    if (bar != null) p.hideBossBar(bar);
                }
            }

            if (!participants.isEmpty()) {
                RaidMap map = maps.get(mapId);
                if (map != null) {
                    RaidInstance raid = new RaidInstance(plugin, map, participants);
                    activeRaids.put(mapId, raid);
                }
            }
            raidQueues.remove(mapId);
        }

        Bukkit.broadcast(Component.text(mapId + " の新たなレイドサイクルが開始されました。", NamedTextColor.AQUA));
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
        int timeLeft = mapTimers.getOrDefault(mapId, CYCLE_DURATION);
        player.sendMessage(Component.text(mapId + " の出撃待機列に参加しました。出撃まであと " + formatTime(timeLeft), NamedTextColor.GREEN));
        
        // Show BossBar
        BossBar bar = queueBossBars.get(mapId);
        if (bar != null) {
            player.showBossBar(bar);
        }
        
        return true;
    }

    /**
     * キューから離脱
     */
    public void leaveQueue(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : raidQueues.entrySet()) {
            if (entry.getValue().remove(player.getUniqueId())) {
                BossBar bar = queueBossBars.get(entry.getKey());
                if (bar != null) player.hideBossBar(bar);
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
        // Fallback: 最初のマップの時間を返す
        if (mapTimers.isEmpty()) return CYCLE_DURATION;
        return mapTimers.values().iterator().next();
    }

    public int getMapTimeLeft(String mapId) {
        return mapTimers.getOrDefault(mapId, CYCLE_DURATION);
    }

    public void forceStartCycle() {
        for (String mapId : maps.keySet()) {
            onMapCycleEnd(mapId);
            mapTimers.put(mapId, CYCLE_DURATION);
        }
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

    /**
     * 全てのアクティブなレイドインスタンスに対して、指定したスカブ（NPC）の死亡を通知する。
     *
     * @param uuid 通知対象のスカブの UUID
     */
    public void onScavDeath(UUID uuid) {
        for (RaidInstance raid : activeRaids.values()) {
            raid.onScavDeath(uuid);
        }
    }

    /**
     * 指定したマップのアクティブなレイドに対して、プレイヤーによるスカブの撃破を通知する。
     *
     * 指定した `mapId` に対応するアクティブなレイドが存在する場合、そのレイドの
     * `onScavKilledByPlayer` を呼び出して撃破イベントを委譲する。該当するレイドがなければ何もしない。
     *
     * @param mapId     対象のマップ識別子
     * @param scavUuid  撃破されたスカブの UUID
     * @param killerUuid 撃破を行ったプレイヤーの UUID
     */
    public void onScavKilledByPlayer(String mapId, UUID scavUuid, UUID killerUuid, ScavBrain.BrainLevel brainLevel) {
        RaidInstance raid = activeRaids.get(mapId);
        if (raid == null) return;
        raid.onScavKilledByPlayer(scavUuid, killerUuid, brainLevel);
    }

    /**
     * 指定した識別子で新しいレイドマップを作成して永続化する。
     * 既に同じ識別子のマップが存在する場合は何もしない。
     *
     * @param mapId 作成するマップの一意の識別子（マップファイル名およびマップ集合のキーとして用いる）
     */
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
