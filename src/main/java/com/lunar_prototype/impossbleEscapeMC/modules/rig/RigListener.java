package com.lunar_prototype.impossbleEscapeMC.modules.rig;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class RigListener implements Listener {
    private final RigModule rigModule;
    private final ImpossbleEscapeMC plugin;

    public RigListener(RigModule rigModule, ImpossbleEscapeMC plugin) {
        this.rigModule = rigModule;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        scheduleSync(player);
        if (!rigModule.isRestrictedMode(player)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(player.getInventory()) && rigModule.isPlayerInventorySlotLocked(player, event.getSlot())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY && clicked != null && clicked.equals(player.getInventory())
                && rigModule.isPlayerInventorySlotLocked(player, event.getSlot())) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && clicked != null
                && !clicked.equals(player.getInventory())) {
            handleMoveIntoPlayerInventory(event, player);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        scheduleSync(player);
        if (!rigModule.isRestrictedMode(player)) return;

        InventoryView view = event.getView();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < view.getTopInventory().getSize()) continue;

            int slot = view.convertSlot(rawSlot);
            if (rigModule.isPlayerInventorySlotLocked(player, slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!rigModule.isRestrictedMode(player)) return;

        Item entityItem = event.getItem();
        ItemStack picked = entityItem.getItemStack().clone();
        int remaining = rigModule.addItemToAllowedInventory(player, picked);
        event.setCancelled(true);

        if (remaining <= 0) {
            entityItem.remove();
        } else {
            picked.setAmount(remaining);
            entityItem.setItemStack(picked);
        }

        scheduleSync(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!rigModule.isRigItem(item)) return;
        scheduleSync(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleSync(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleSync(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        scheduleSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        rigModule.clearLockedSlotPlaceholders(player);
        event.getDrops().removeIf(RigModule::isLockedSlotPlaceholder);
    }

    private void handleMoveIntoPlayerInventory(InventoryClickEvent event, Player player) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        event.setCancelled(true);
        ItemStack moving = current.clone();
        int remaining = rigModule.addItemToAllowedInventory(player, moving);

        Inventory source = event.getClickedInventory();
        if (source == null) return;

        if (remaining <= 0) {
            source.setItem(event.getSlot(), null);
        } else if (remaining < current.getAmount()) {
            ItemStack leftover = current.clone();
            leftover.setAmount(remaining);
            source.setItem(event.getSlot(), leftover);
        }

        player.updateInventory();
        scheduleSync(player);
    }

    private void scheduleSync(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            rigModule.enforceLockedSlots(player);
            rigModule.syncLockedSlotPlaceholders(player);
            BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
            if (backpackModule != null && backpackModule.getDisplayManager() != null) {
                backpackModule.getDisplayManager().sync(player);
            }
        });
    }
}
