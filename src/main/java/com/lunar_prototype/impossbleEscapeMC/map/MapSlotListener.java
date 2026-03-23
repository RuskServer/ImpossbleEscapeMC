package com.lunar_prototype.impossbleEscapeMC.map;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class MapSlotListener implements Listener {

    private final ImpossbleEscapeMC plugin;
    private final RaidMapManager mapManager;

    public MapSlotListener(ImpossbleEscapeMC plugin, RaidMapManager mapManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        
        // 定期的チェックタスク (1秒ごと)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    mapManager.updateMapSlot(player);
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        mapManager.updateMapSlot(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // ゲームモード変更後に更新
        Bukkit.getScheduler().runTask(plugin, () -> mapManager.updateMapSlot(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> mapManager.updateMapSlot(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // スロット8（ホットバー一番右）への直接クリックまたはホットキー操作を制限
        boolean targetIsMapSlot = (event.getSlot() == 8 && event.getSlotType() == InventoryType.SlotType.QUICKBAR) 
                                || event.getHotbarButton() == 8;

        if (targetIsMapSlot || mapManager.isMapSlotItem(event.getCurrentItem()) || mapManager.isMapSlotItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (mapManager.isMapSlotItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (mapManager.isMapSlotItem(event.getMainHandItem()) || mapManager.isMapSlotItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        // 死んだときに地図がドロップ品に含まれないようにする
        event.getDrops().removeIf(mapManager::isMapSlotItem);
    }
}
