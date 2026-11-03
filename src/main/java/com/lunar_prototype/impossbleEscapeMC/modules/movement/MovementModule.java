package com.lunar_prototype.impossbleEscapeMC.modules.movement;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 参加・復活時に共通倍率を適用し、切断・停止時には自分の補正だけを除去する。 */
public final class MovementModule implements IModule, Listener {
    private boolean active;
    private ImpossbleEscapeMC plugin;

    @Override public void onEnable(ServiceContainer services) {
        plugin = ImpossbleEscapeMC.getInstance();
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    private void apply(Player player) {
        if (!active || DatapackGunnerManager.isGunner(player)) return;
        MovementTuning.applyTo(player.getAttribute(Attribute.MOVEMENT_SPEED));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {apply(event.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        MovementTuning.removeFrom(event.getPlayer().getAttribute(Attribute.MOVEMENT_SPEED));
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) apply(event.getPlayer());
        });
    }

    @Override public void onDisable() {
        active = false;
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) MovementTuning.removeFrom(player.getAttribute(Attribute.MOVEMENT_SPEED));
    }
}
