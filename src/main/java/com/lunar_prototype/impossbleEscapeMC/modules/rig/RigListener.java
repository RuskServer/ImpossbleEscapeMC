package com.lunar_prototype.impossbleEscapeMC.modules.rig;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class RigListener implements Listener {
    private static final int LEGGINGS_SLOT = 37;
    private static final int CHEST_SLOT = 38;
    private final RigModule rigModule;
    private final ImpossbleEscapeMC plugin;

    public RigListener(RigModule rigModule, ImpossbleEscapeMC plugin) {
        this.rigModule = rigModule;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (wouldEquipConflictingArmor(event, player)) {
            event.setCancelled(true);
            showArmorConflict(player);
            return;
        }
        if (rigModule.isControlSuppressed(player)) return;

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
        for (var placed : event.getNewItems().entrySet()) {
            int rawSlot = placed.getKey();
            if (!player.getInventory().equals(event.getView().getInventory(rawSlot))) continue;
            int slot = event.getView().convertSlot(rawSlot);
            if ((slot == CHEST_SLOT || slot == LEGGINGS_SLOT)
                    && rigModule.conflictsWithEquippedArmor(player, placed.getValue())) {
                event.setCancelled(true);
                showArmorConflict(player);
                return;
            }
        }
        if (rigModule.isControlSuppressed(player)) return;

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
        if (rigModule.isControlSuppressed(player)) return;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isRightClick() && event.useItemInHand() != Event.Result.DENY
                && trySwapRigAcrossSlots(event)) return;
        if (event.getAction().isRightClick() && event.useItemInHand() != Event.Result.DENY
                && rigModule.conflictsWithEquippedArmor(event.getPlayer(), event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
            showArmorConflict(event.getPlayer());
            return;
        }
        if (rigModule.isControlSuppressed(event.getPlayer())) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!rigModule.isRigItem(item)) return;
        scheduleSync(event.getPlayer());
    }

    /** 装甲リグと通常リグは装備スロットが異なるので、バニラの右クリック交換に任せない。 */
    private boolean trySwapRigAcrossSlots(PlayerInteractEvent event) {
        ItemStack incoming = event.getItem();
        if (!rigModule.isRigItem(incoming)) return false;
        Player player = event.getPlayer();
        boolean armored = rigModule.isArmoredRigItem(incoming);
        ItemStack worn = armored ? player.getInventory().getLeggings() : player.getInventory().getChestplate();
        if (armored ? !rigModule.isRigItem(worn) : !rigModule.isArmoredRigItem(worn)) return false;

        ItemStack target = armored ? player.getInventory().getChestplate() : player.getInventory().getLeggings();
        event.setUseItemInHand(Event.Result.DENY);
        if (target != null && !target.getType().isAir()) {
            showArmorConflict(player);
            return true;
        }

        ItemStack replacement = incoming.clone();
        ItemStack returned = worn.clone();
        if (armored) {
            player.getInventory().setLeggings(null);
            player.getInventory().setChestplate(replacement);
        } else {
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(replacement);
        }
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(returned);
        } else {
            player.getInventory().setItemInMainHand(returned);
        }
        scheduleSync(player);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) return;
        if (!rigModule.conflictsWithEquippedArmor(player, event.getItem())) return;
        event.setCancelled(true);
        showArmorConflict(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (rigModule.isControlSuppressed(event.getPlayer())) return;
        scheduleSync(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (rigModule.isControlSuppressed(event.getPlayer())) return;
        scheduleSync(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (rigModule.isControlSuppressed(event.getPlayer())) return;
        scheduleSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        rigModule.clearLockedSlotPlaceholders(player);
        event.getDrops().removeIf(item -> RigModule.isLockedSlotPlaceholder(item) || ItemFactory.isCostSlotPlaceholder(item));
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

    private boolean wouldEquipConflictingArmor(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(player.getInventory())) return false;

        int slot = event.getSlot();
        if (slot == CHEST_SLOT || slot == LEGGINGS_SLOT) {
            ItemStack incoming = switch (event.getAction()) {
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> event.getCursor();
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> event.getHotbarButton() >= 0
                        ? player.getInventory().getItem(event.getHotbarButton())
                        : player.getInventory().getItemInOffHand();
                default -> null;
            };
            return rigModule.conflictsWithEquippedArmor(player, incoming);
        }

        // プレイヤー自身の画面でのシフトクリックは、空の防具スロットへ自動装備できる。
        return event.getView().getType() == InventoryType.CRAFTING
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && rigModule.conflictsWithEquippedArmor(player, event.getCurrentItem());
    }

    private void showArmorConflict(Player player) {
        player.sendActionBar(Component.text("装甲リグは胸防具や他のリグと同時に装備できません", NamedTextColor.RED));
    }

    private void scheduleSync(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 次のtickまでに漁る画面が開いていたら触らない (持ち物の欄はバックパックの表示になっている)
            if (rigModule.isControlSuppressed(player)) return;
            rigModule.syncEquippedRigModel(player);
            rigModule.enforceLockedSlots(player);
            rigModule.syncLockedSlotPlaceholders(player);
            BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
            if (backpackModule != null && backpackModule.getDisplayManager() != null) {
                backpackModule.getDisplayManager().sync(player);
            }
        });
    }
}
