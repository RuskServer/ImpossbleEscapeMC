package com.lunar_prototype.impossbleEscapeMC.minigame;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class MinigameListener implements Listener {
    private final MinigameManager manager;

    public MinigameListener(MinigameManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (manager.isRunning() && manager.getAlivePlayers().contains(player.getUniqueId())) {
            manager.onPlayerDeath(player);
            
            Player killer = player.getKiller();
            if (killer != null) {
                manager.recordKill(killer.getUniqueId(), player.getUniqueId());
            }

            event.setKeepInventory(true);
            event.getDrops().clear();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!manager.isRunning()) return;
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;

        if (manager.getAlivePlayers().contains(victim.getUniqueId()) && manager.getAlivePlayers().contains(attacker.getUniqueId())) {
            manager.addDamage(attacker.getUniqueId(), victim.getUniqueId(), event.getFinalDamage());
        }
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (manager.isRunning() && manager.isCountdown()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                event.setTo(from.setDirection(to.getDirection()));
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (manager.isRunning() && !manager.getAlivePlayers().contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
}
