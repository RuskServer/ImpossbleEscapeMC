package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.BackpackStats;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackpackModule implements IModule {
    private final Map<UUID, BackpackSession> sessions = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {
        Bukkit.getPluginManager().registerEvents(new BackpackListener(this), ImpossbleEscapeMC.getInstance());
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            BackpackSession session = sessions.get(player.getUniqueId());
            if (session != null && player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackInventoryHolder) {
                saveBackpackFromOpenInventory(player);
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    public boolean openBackpackFromOffhand(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!isBackpackItem(offhand)) {
            player.sendMessage("§cオフハンドにバックパックを装備してください。");
            return false;
        }
        openBackpack(player, offhand);
        return true;
    }

    public void openBackpack(Player player, ItemStack backpackItem) {
        if (!isBackpackItem(backpackItem)) return;

        String uid = ensureBackpackUid(backpackItem);
        int size = getBackpackSize(backpackItem);

        Inventory inv = loadInventory(backpackItem, size);
        InventoryHolder holder = new BackpackInventoryHolder(player.getUniqueId(), uid);
        Inventory finalInv = Bukkit.createInventory(holder, size, Component.text("Backpack"));
        if (holder instanceof BackpackInventoryHolder backpackHolder) {
            backpackHolder.setInventory(finalInv);
        }
        for (int i = 0; i < Math.min(inv.getSize(), finalInv.getSize()); i++) {
            finalInv.setItem(i, inv.getItem(i));
        }

        sessions.put(player.getUniqueId(), new BackpackSession(uid));
        player.openInventory(finalInv);
    }

    public void handleClose(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof BackpackInventoryHolder)) return;
        saveInventoryToOffhandBackpack(player, inventory);
        sessions.remove(player.getUniqueId());
    }

    public void saveBackpackFromOpenInventory(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof BackpackInventoryHolder)) return;
        saveInventoryToOffhandBackpack(player, top);
    }

    public boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId == null) return false;

        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null) return false;

        return (def.type != null && "BACKPACK".equalsIgnoreCase(def.type)) || def.backpackStats != null;
    }

    public int getBackpackSize(ItemStack backpackItem) {
        BackpackStats stats = getStats(backpackItem);
        if (stats == null) return 9;
        int size = stats.size;
        if (size <= 0) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = ((size + 8) / 9) * 9;
        return Math.min(size, 54);
    }

    public double getBackpackReduction(ItemStack backpackItem) {
        BackpackStats stats = getStats(backpackItem);
        if (stats == null) return 0.0;
        return Math.max(0.0, Math.min(0.95, stats.reduction));
    }

    public int calculateEffectiveContentsWeight(ItemStack backpackItem) {
        if (!isBackpackItem(backpackItem)) return 0;

        int size = getBackpackSize(backpackItem);
        Inventory inventory = loadInventory(backpackItem, size);

        int raw = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            if (isBackpackItem(item)) continue; // バックパックinバックパック禁止ポリシー

            int per = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, 0);
            raw += per * item.getAmount();
        }

        double reduction = getBackpackReduction(backpackItem);
        return (int) Math.round(raw * (1.0 - reduction));
    }

    private void saveInventoryToOffhandBackpack(Player player, Inventory inventory) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!isBackpackItem(offhand)) {
            player.sendMessage("§cバックパックがオフハンドから外れたため保存できませんでした。");
            return;
        }

        String currentUid = ensureBackpackUid(offhand);
        BackpackSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.backpackUid.equals(currentUid)) {
            player.sendMessage("§c別のバックパックに持ち替えたため保存を中断しました。");
            return;
        }

        ItemMeta meta = offhand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        try {
            String data = SerializationUtil.serializeInventory(inventory);
            pdc.set(PDCKeys.BACKPACK_DATA, PDCKeys.STRING, data);
            offhand.setItemMeta(meta);
        } catch (IOException e) {
            ImpossbleEscapeMC.getInstance().getLogger().warning("Failed to save backpack inventory: " + e.getMessage());
        }
    }

    private Inventory loadInventory(ItemStack backpackItem, int size) {
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return Bukkit.createInventory(null, size);

        String data = meta.getPersistentDataContainer().get(PDCKeys.BACKPACK_DATA, PDCKeys.STRING);
        if (data == null || data.isEmpty()) {
            return Bukkit.createInventory(null, size);
        }

        try {
            Inventory loaded = SerializationUtil.deserializeInventory(data, Component.text("Backpack"));
            if (loaded.getSize() == size) {
                return loaded;
            }

            Inventory resized = Bukkit.createInventory(null, size);
            for (int i = 0; i < Math.min(size, loaded.getSize()); i++) {
                resized.setItem(i, loaded.getItem(i));
            }
            return resized;
        } catch (Exception e) {
            ImpossbleEscapeMC.getInstance().getLogger().warning("Failed to load backpack inventory: " + e.getMessage());
            return Bukkit.createInventory(null, size);
        }
    }

    private BackpackStats getStats(ItemStack backpackItem) {
        if (!isBackpackItem(backpackItem)) return null;

        String itemId = backpackItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null ? def.backpackStats : null;
    }

    public String ensureBackpackUid(ItemStack backpackItem) {
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return "";

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uid = pdc.get(PDCKeys.BACKPACK_UID, PDCKeys.STRING);
        if (uid == null || uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            pdc.set(PDCKeys.BACKPACK_UID, PDCKeys.STRING, uid);
            backpackItem.setItemMeta(meta);
        }
        return uid;
    }

    private static class BackpackSession {
        private final String backpackUid;

        private BackpackSession(String backpackUid) {
            this.backpackUid = backpackUid;
        }
    }

    public static class BackpackInventoryHolder implements InventoryHolder {
        private final UUID owner;
        private final String backpackUid;
        private Inventory inventory;

        public BackpackInventoryHolder(UUID owner, String backpackUid) {
            this.owner = owner;
            this.backpackUid = backpackUid;
        }

        public UUID owner() {
            return owner;
        }

        public String backpackUid() {
            return backpackUid;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
