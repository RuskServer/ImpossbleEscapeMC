package com.lunar_prototype.impossbleEscapeMC.modules.scoreboard;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * スコアボード表示を管理するモジュール
 */
public class ScoreboardModule implements IModule, Listener {
    private ImpossbleEscapeMC plugin;
    private PlayerDataModule dataModule;
    private RaidModule raidModule;
    private final Map<UUID, ScoreboardHandler> handlers = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        this.dataModule = container.get(PlayerDataModule.class);
        this.raidModule = container.get(RaidModule.class);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // すでにオンラインのプレイヤーに対して初期化
        for (Player player : Bukkit.getOnlinePlayers()) {
            handlers.put(player.getUniqueId(), new ScoreboardHandler(player));
        }

        // 更新タスク (0.5秒周期)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    @Override
    public void onDisable() {
        handlers.clear();
        // スコアボードをメインに戻す
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void tick() {
        for (Map.Entry<UUID, ScoreboardHandler> entry : handlers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;

            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data == null) continue;

            boolean isInRaid = raidModule != null && raidModule.isInRaid(player);
            entry.getValue().update(data, isInRaid);
            entry.getValue().updateNameTags(isInRaid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handlers.put(event.getPlayer().getUniqueId(), new ScoreboardHandler(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handlers.remove(event.getPlayer().getUniqueId());
    }
}
