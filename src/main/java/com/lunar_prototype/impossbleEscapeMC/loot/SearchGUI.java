package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
        private final Object source; // Container or Entity
        public SearchHolder(Object source) { this.source = source; }
        public Object getSource() { return source; }
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;

        PersistentDataContainer pdc = container.getPersistentDataContainer();
        if (!pdc.has(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING)) return;

        event.setCancelled(true);
        open(event.getPlayer(), container);
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;

        PersistentDataContainer pdc = mannequin.getPersistentDataContainer();
        if (!pdc.has(PDCKeys.CORPSE_INVENTORY, PDCKeys.STRING)) return;

        event.setCancelled(true);
        open(event.getPlayer(), mannequin);
    }

    public void open(Player player, Object source) {
        Inventory realInv = getInventoryFromSource(source);
        if (realInv == null) return;

        Inventory guiInv = Bukkit.createInventory(new SearchHolder(source), realInv.getSize(), Component.text("Searching..."));
        updateInventory(guiInv, source);
        player.openInventory(guiInv);
    }

    private Inventory getInventoryFromSource(Object source) {
        if (source instanceof Container c) return c.getInventory();
        if (source instanceof Mannequin m) {
            String data = m.getPersistentDataContainer().get(PDCKeys.CORPSE_INVENTORY, PersistentDataType.STRING);
            if (data != null) {
                try {
                    Component title = m.customName();
                    if (title == null) title = Component.text("死体漁り");
                    return CorpseManager.deserializeInventory(data, title);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deserialize corpse inventory!");
                }
            }
        }
        return null;
    }

    private PersistentDataContainer getPDCFromSource(Object source) {
        if (source instanceof Container c) return c.getPersistentDataContainer();
        if (source instanceof Entity e) return e.getPersistentDataContainer();
        return null;
    }

    private void updateInventory(Inventory guiInv, Object source) {
        Inventory realInv = getInventoryFromSource(source);
        PersistentDataContainer pdc = getPDCFromSource(source);
        if (realInv == null || pdc == null) return;

        Set<Integer> searchedSlots = getSearchedSlots(pdc);

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
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        Object source = holder.getSource();
        PersistentDataContainer pdc = getPDCFromSource(source);

        Set<Integer> searchedSlots = getSearchedSlots(pdc);

        if (searchedSlots.contains(slot)) {
            ItemStack item = getInventoryFromSource(source).getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (player.getInventory().addItem(item).isEmpty()) {
                    removeFromSourceInventory(source, slot);
                }
                updateInventory(event.getInventory(), source);
            }
        } else {
            startSearching(player, event.getInventory(), source, slot);
        }
    }

    private void removeFromSourceInventory(Object source, int slot) {
        if (source instanceof Container c) {
            c.getInventory().setItem(slot, null);
        } else if (source instanceof Mannequin m) {
            Inventory inv = getInventoryFromSource(source);
            inv.setItem(slot, null);
            try {
                m.getPersistentDataContainer().set(PDCKeys.CORPSE_INVENTORY, PersistentDataType.STRING, CorpseManager.serializeInventory(inv));
                CorpseManager.updateMannequinAppearance(m, inv);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save updated corpse inventory!");
            }
        }
    }

    private void startSearching(Player player, Inventory guiInv, Object source, int slot) {
        Inventory realInv = getInventoryFromSource(source);
        if (realInv == null || realInv.getItem(slot) == null) return;

        new BukkitRunnable() {
            int progress = 0;
            final int maxProgress = 20;

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
                    progress += 4;
                } else {
                    markAsSearched(source, slot);
                    updateInventory(guiInv, source);
                    player.playSound(player.getLocation(), "ui.cartography_table.draw_map", 1.0f, 1.2f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private Set<Integer> getSearchedSlots(PersistentDataContainer pdc) {
        String data = pdc.get(SEARCHED_SLOTS_KEY, PersistentDataType.STRING);
        Set<Integer> slots = new HashSet<>();
        if (data != null && !data.isEmpty()) {
            for (String s : data.split(",")) {
                try { slots.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        }
        return slots;
    }

    private void markAsSearched(Object source, int slot) {
        PersistentDataContainer pdc = getPDCFromSource(source);
        if (pdc == null) return;

        Set<Integer> slots = getSearchedSlots(pdc);
        slots.add(slot);
        StringBuilder sb = new StringBuilder();
        for (int s : slots) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        pdc.set(SEARCHED_SLOTS_KEY, PersistentDataType.STRING, sb.toString());

        if (source instanceof Container c) c.update();
    }
}