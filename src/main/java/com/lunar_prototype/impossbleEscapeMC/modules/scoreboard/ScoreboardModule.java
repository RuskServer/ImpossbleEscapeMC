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
@com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service
public class ScoreboardModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final PlayerDataModule dataModule;
    private final java.util.function.Supplier<RaidModule> raids;

    @com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject
    public ScoreboardModule(ImpossbleEscapeMC plugin, PlayerDataModule dataModule,
                            java.util.function.Supplier<RaidModule> raids) {
        this.plugin = plugin;
        this.dataModule = dataModule;
        this.raids = raids;
    }
    private final Map<UUID, ScoreboardHandler> handlers = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {

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
        RaidModule raidModule = raids.get();
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
