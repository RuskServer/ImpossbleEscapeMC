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
    private final Gson gson;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();

    public PlayerDataModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "userdata");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
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
    private void loadAsync(UUID uuid) {
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
        });
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
        File file = new File(dataFolder, data.getUuid() + ".json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
            data.setDirty(false); // 保存完了
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + data.getUuid());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadAsync(event.getPlayer().getUniqueId());
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
    }
}
