package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SearchGUI implements Listener {
    private final ImpossbleEscapeMC plugin;
    private final NamespacedKey SEARCHED_SLOTS_KEY;

    public SearchGUI(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.SEARCHED_SLOTS_KEY = new NamespacedKey(plugin, "searched_slots");
    }

    public static class SearchHolder implements InventoryHolder {
        private final Container container;
        public SearchHolder(Container container) { this.container = container; }
        public Container getContainer() { return container; }
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;

        // ルートテーブル設定があるコンテナか確認
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        if (!pdc.has(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING)) return;

        event.setCancelled(true);
        open(event.getPlayer(), container);
    }

    public void open(Player player, Container container) {
        Inventory inv = Bukkit.createInventory(new SearchHolder(container), container.getInventory().getSize(), Component.text("Searching..."));
        updateInventory(inv, container);
        player.openInventory(inv);
    }

    private void updateInventory(Inventory guiInv, Container container) {
        Inventory realInv = container.getInventory();
        Set<Integer> searchedSlots = getSearchedSlots(container);

        for (int i = 0; i < realInv.getSize(); i++) {
            ItemStack realItem = realInv.getItem(i);
            if (realItem == null || realItem.getType() == Material.AIR) {
                guiInv.setItem(i, null);
                continue;
            }

            if (searchedSlots.contains(i)) {
                guiInv.setItem(i, realItem.clone());
            } else {
                guiInv.setItem(i, createUnknownIcon());
            }
        }
    }

    private ItemStack createUnknownIcon() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("??? (未確認)", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SearchHolder holder)) return;
        if (event.getRawSlot() >= event.getInventory().getSize()) return; // 自分のインベントリは操作可能にする

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        Container container = holder.getContainer();
        Set<Integer> searchedSlots = getSearchedSlots(container);

        if (searchedSlots.contains(slot)) {
            // 既にサーチ済みならアイテムを取り出す
            ItemStack item = container.getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (player.getInventory().addItem(item).isEmpty()) {
                    container.getInventory().setItem(slot, null);
                }
                updateInventory(event.getInventory(), container);
            }
        } else {
            // サーチ開始
            startSearching(player, event.getInventory(), container, slot);
        }
    }

    private void startSearching(Player player, Inventory guiInv, Container container, int slot) {
        ItemStack originalItem = container.getInventory().getItem(slot);
        if (originalItem == null) return;

        new BukkitRunnable() {
            int progress = 0;
            final int maxProgress = 20; // 1秒間 (20 ticks)

            @Override
            public void run() {
                if (!player.getOpenInventory().getTopInventory().equals(guiInv)) {
                    this.cancel();
                    return;
                }

                if (progress < maxProgress) {
                    ItemStack progressItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                    ItemMeta meta = progressItem.getItemMeta();
                    meta.displayName(Component.text("サーチ中... " + (progress * 5) + "%", NamedTextColor.GREEN));
                    progressItem.setAmount(Math.max(1, progress));
                    guiInv.setItem(slot, progressItem);
                    progress += 4; // 少し早く進める
                } else {
                    markAsSearched(container, slot);
                    updateInventory(guiInv, container);
                    player.playSound(player.getLocation(), "ui.cartography_table.draw_map", 1.0f, 1.2f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private Set<Integer> getSearchedSlots(Container container) {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        String data = pdc.get(SEARCHED_SLOTS_KEY, PersistentDataType.STRING);
        Set<Integer> slots = new HashSet<>();
        if (data != null && !data.isEmpty()) {
            for (String s : data.split(",")) {
                try { slots.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        }
        return slots;
    }

    private void markAsSearched(Container container, int slot) {
        Set<Integer> slots = getSearchedSlots(container);
        slots.add(slot);
        StringBuilder sb = new StringBuilder();
        for (int s : slots) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        container.getPersistentDataContainer().set(SEARCHED_SLOTS_KEY, PersistentDataType.STRING, sb.toString());
        container.update();
    }
}
