package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BackpackListener implements Listener {
    private static final int BACKPACK_BUTTON_SLOT = 2;

    private final BackpackModule backpackModule;
    private final ImpossbleEscapeMC plugin;

    public BackpackListener(BackpackModule backpackModule) {
        this.backpackModule = backpackModule;
        this.plugin = ImpossbleEscapeMC.getInstance();
        startButtonTask();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();

        // Crafting grid trigger button behavior
        if (isPlayerCraftingGrid(view) && event.getRawSlot() == BACKPACK_BUTTON_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                if (!isPlayableMode(player)) return;
                backpackModule.openBackpackFromOffhand(player);
            }
            return;
        }

        // Trigger item cleanup
        if (isBackpackTrigger(event.getCurrentItem()) && (!isPlayerCraftingGrid(view) || event.getRawSlot() != BACKPACK_BUTTON_SLOT)) {
            event.setCurrentItem(null);
        }
        if (isBackpackTrigger(event.getCursor())) {
            event.setCursor(null);
        }

        // Right-click to open backpack from player's inventory
        if (event.getWhoClicked() instanceof Player player
                && event.getClick().isRightClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && backpackModule.isBackpackItem(event.getCurrentItem())) {

            if (!isPlayableMode(player)) return;

            // バックパックスロットはオフハンド固定
            if (event.getSlot() != 40) {
                player.sendMessage("§cバックパックはオフハンドに装備して開いてください。");
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            backpackModule.openBackpackFromOffhand(player);
            return;
        }

        // Backpack in backpack prohibition
        if (event.getView().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            if (event.getClickedInventory() == null) return;

            // placing item into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
                ItemStack cursor = event.getCursor();
                if (backpackModule.isBackpackItem(cursor)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

            // shift-click moving from player inv into backpack
            if (event.getClickedInventory().equals(event.getWhoClicked().getInventory())
                    && event.isShiftClick()
                    && backpackModule.isBackpackItem(event.getCurrentItem())) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                }
            }
            // hotbar number key move into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())
                    && event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                if (backpackModule.isBackpackItem(hotbarItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

            // F-key offhand swap move into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())
                    && event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = event.getWhoClicked().getInventory().getItemInOffHand();
                if (backpackModule.isBackpackItem(offhandItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            if (backpackModule.isBackpackItem(event.getOldCursor())) {
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player player) {
                            player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                        }
                        return;
                    }
                }
            }
        }

        if (isPlayerCraftingGrid(event.getView()) && event.getRawSlots().contains(BACKPACK_BUTTON_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isPlayerCraftingGrid(event.getView())) {
            event.getView().setItem(BACKPACK_BUTTON_SLOT, null);
            Player player = (Player) event.getPlayer();
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (isBackpackTrigger(item)) {
                    player.getInventory().setItem(i, null);
                }
            }
        }

        if (event.getPlayer() instanceof Player player) {
            backpackModule.handleClose(player, event.getInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isBackpackTrigger(event.getItem().getItemStack())) {
            event.getItem().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isBackpackTrigger(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isBackpackTrigger(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private void startButtonTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryView view = player.getOpenInventory();
                if (!isPlayerCraftingGrid(view)) continue;

                ItemStack current = view.getItem(BACKPACK_BUTTON_SLOT);
                boolean shouldHave = isPlayableMode(player) && backpackModule.isBackpackItem(player.getInventory().getItemInOffHand());
                if (shouldHave) {
                    if (!isBackpackTrigger(current)) {
                        view.setItem(BACKPACK_BUTTON_SLOT, getBackpackTriggerButton());
                    }
                } else if (isBackpackTrigger(current)) {
                    view.setItem(BACKPACK_BUTTON_SLOT, null);
                }
            }
        }, 0L, 5L);
    }

    private ItemStack getBackpackTriggerButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a[ Backpack ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7オフハンドのバックパックを開きます");
            lore.add("");
            lore.add("§eクリックで開く");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, 3);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isBackpackTrigger(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer trigger = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER);
        return trigger != null && trigger == 3;
    }

    private boolean isPlayerCraftingGrid(InventoryView view) {
        return view.getType() == InventoryType.CRAFTING;
    }

    private boolean isPlayableMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}
