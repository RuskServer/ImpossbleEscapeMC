package com.lunar_prototype.impossbleEscapeMC.modules.rig;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.RigStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class RigModule implements IModule {
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 35;

    private ImpossbleEscapeMC plugin;

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        Bukkit.getPluginManager().registerEvents(new RigListener(this, plugin), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                enforceLockedSlots(player);
            }
        }, 1L, 5L);
    }

    @Override
    public void onDisable() {
    }

    public boolean isRigItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;

        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId == null) return false;

        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null && (("RIG".equalsIgnoreCase(def.type)) || def.rigStats != null);
    }

    public ItemStack getEquippedRig(Player player) {
        ItemStack leggings = player.getInventory().getLeggings();
        return isRigItem(leggings) ? leggings : null;
    }

    public int getEnabledMainInventorySlots(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return 27;
        }

        RigStats stats = getRigStats(getEquippedRig(player));
        if (stats == null) return 0;
        return Math.max(0, Math.min(27, stats.size));
    }

    public double getRigReduction(Player player) {
        RigStats stats = getRigStats(getEquippedRig(player));
        if (stats == null) return 0.0;
        return Math.max(0.0, Math.min(0.95, stats.reduction));
    }

    public boolean isRestrictedMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    public boolean isPlayerInventorySlotLocked(Player player, int slot) {
        if (slot < MAIN_INVENTORY_START || slot > MAIN_INVENTORY_END) return false;
        if (!isRestrictedMode(player)) return false;

        int enabled = getEnabledMainInventorySlots(player);
        int unlockedEnd = MAIN_INVENTORY_START + enabled - 1;
        return slot > unlockedEnd;
    }

    public int addItemToAllowedInventory(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        if (!isRestrictedMode(player)) {
            return player.getInventory().addItem(stack).values().stream().mapToInt(ItemStack::getAmount).sum();
        }

        PlayerInventory inventory = player.getInventory();
        int remaining = stack.getAmount();
        int maxStack = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());

        for (int slot : getAllowedStorageSlots(player)) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(stack)) continue;

            int space = maxStack - existing.getAmount();
            if (space <= 0) continue;

            int moved = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + moved);
            inventory.setItem(slot, existing);
            remaining -= moved;
            if (remaining <= 0) return 0;
        }

        for (int slot : getAllowedStorageSlots(player)) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;

            ItemStack placed = stack.clone();
            placed.setAmount(Math.min(maxStack, remaining));
            inventory.setItem(slot, placed);
            remaining -= placed.getAmount();
            if (remaining <= 0) return 0;
        }

        return remaining;
    }

    public void enforceLockedSlots(Player player) {
        if (!isRestrictedMode(player)) return;

        PlayerInventory inventory = player.getInventory();
        for (int slot = MAIN_INVENTORY_START; slot <= MAIN_INVENTORY_END; slot++) {
            if (!isPlayerInventorySlotLocked(player, slot)) continue;

            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            inventory.setItem(slot, null);
            Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), item);
            dropped.setOwner(player.getUniqueId());
        }
    }

    public int calculateEffectiveUnlockedInventoryWeight(Player player) {
        if (!isRestrictedMode(player)) return 0;

        double reduction = getRigReduction(player);
        int raw = 0;
        PlayerInventory inventory = player.getInventory();
        int enabled = getEnabledMainInventorySlots(player);
        int unlockedEnd = Math.min(MAIN_INVENTORY_END, MAIN_INVENTORY_START + enabled - 1);

        for (int slot = MAIN_INVENTORY_START; slot <= unlockedEnd; slot++) {
            raw += getItemWeight(inventory.getItem(slot));
        }

        return (int) Math.round(raw * (1.0 - reduction));
    }

    public int getUnlockedMainInventoryEndSlot(Player player) {
        int enabled = getEnabledMainInventorySlots(player);
        return Math.min(MAIN_INVENTORY_END, MAIN_INVENTORY_START + enabled - 1);
    }

    private int[] getAllowedStorageSlots(Player player) {
        int enabled = getEnabledMainInventorySlots(player);
        int[] slots = new int[9 + enabled];
        for (int i = 0; i < 9; i++) {
            slots[i] = i;
        }
        for (int i = 0; i < enabled; i++) {
            slots[9 + i] = MAIN_INVENTORY_START + i;
        }
        return slots;
    }

    private RigStats getRigStats(ItemStack rigItem) {
        if (!isRigItem(rigItem)) return null;

        String itemId = rigItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null ? def.rigStats : null;
    }

    private int getItemWeight(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        int weightPerItem = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, 0);
        return weightPerItem * item.getAmount();
    }
}
