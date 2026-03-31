package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * プレイヤーデータのロード・保存・キャッシュを管理するコアモジュール
 */
public class PlayerDataModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final File dataFolder;
    private final File backupFolder;
    private final Gson gson;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> saveLocks = new ConcurrentHashMap<>();

    public PlayerDataModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "userdata");
        this.backupFolder = new File(dataFolder, "backups");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }

    @Override
    public void onEnable(ServiceContainer container) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // オートセーブタスク (5分ごと)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllDirty, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        // 終了時に全データを保存 (同期実行)
        saveAllDirty();
    }

    /**
     * キャッシュからプレイヤーデータを取得。存在しない場合は新規作成。
     */
    public PlayerData getPlayerData(UUID uuid) {
        return dataCache.computeIfAbsent(uuid, PlayerData::new);
    }

    /**
     * 非同期でデータをロード
     */
    private void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();

        CompletableFuture.runAsync(() -> {
            File file = new File(dataFolder, uuid + ".json");
            if (!file.exists()) return;

            try (Reader reader = new FileReader(file)) {
                PlayerData data = gson.fromJson(reader, PlayerData.class);
                if (data != null) {
                    dataCache.put(uuid, data);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load player data for " + uuid);
                e.printStackTrace();
            }
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline()) return;

            PlayerData data = dataCache.get(uuid);
            if (data == null) {
                data = getPlayerData(uuid);
            }

            Bukkit.getPluginManager().callEvent(new PlayerDataLoadedEvent(current, data));
        }));
    }

    /**
     * 非同期でデータを保存
     */
    public void saveAsync(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data == null || !data.isDirty()) return;

        CompletableFuture.runAsync(() -> saveInternal(data));
    }

    /**
     * 全ての変更（Dirty）があるデータを保存
     */
    private void saveAllDirty() {
        for (PlayerData data : dataCache.values()) {
            if (data.isDirty()) {
                saveInternal(data);
            }
        }
    }

    private void saveInternal(PlayerData data) {
        Object lock = saveLocks.computeIfAbsent(data.getUuid(), ignored -> new Object());
        synchronized (lock) {
            saveInternalLocked(data);
        }
    }

    private void saveInternalLocked(PlayerData data) {
        String fileName = data.getUuid() + ".json";
        File finalFile = new File(dataFolder, fileName);
        File tempFile = new File(dataFolder, fileName + ".tmp");

        try {
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IOException("Failed to create userdata directory: " + dataFolder.getAbsolutePath());
            }

            // 1. 一時ファイルに書き込み
            try (Writer writer = new FileWriter(tempFile)) {
                gson.toJson(data, writer);
            }

            // 2. 書き込み成功時のみ、既存ファイルをバックアップ
            if (finalFile.exists()) {
                rotateBackups(data.getUuid());
            }

            // 3. アトミックな移動 (OSレベルで安全に置き換え)
            java.nio.file.Files.move(tempFile.toPath(), finalFile.toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            
            data.setDirty(false); // 保存完了
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + data.getUuid());
            e.printStackTrace();
            if (tempFile.exists()) {
                tempFile.delete(); // 失敗した一時ファイルを削除
            }
        }
    }

    private void rotateBackups(UUID uuid) {
        File backupDir = new File(backupFolder, uuid.toString());
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 3 -> 削除, 2 -> 3, 1 -> 2
        for (int i = 3; i >= 1; i--) {
            File current = new File(backupDir, i + ".json");
            if (current.exists()) {
                if (i == 3) {
                    current.delete();
                } else {
                    File next = new File(backupDir, (i + 1) + ".json");
                    try {
                        java.nio.file.Files.move(current.toPath(), next.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {}
                }
            }
        }

        File mainFile = new File(dataFolder, uuid + ".json");
        if (mainFile.exists()) {
            try {
                java.nio.file.Files.copy(mainFile.toPath(), new File(backupDir, "1.json").toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getPlayerData(player.getUniqueId()); // イベント発火前にキャッシュエントリを用意
        loadAsync(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // 保存してからキャッシュから削除
        PlayerData data = dataCache.get(uuid);
        if (data != null && data.isDirty()) {
            saveInternal(data);
        }
        dataCache.remove(uuid);
        saveLocks.remove(uuid);
    }
}
