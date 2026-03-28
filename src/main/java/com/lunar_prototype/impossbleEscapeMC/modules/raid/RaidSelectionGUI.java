package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.party.Party;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RaidSelectionGUI implements Listener {
    private final RaidModule manager;
    private static final Component TITLE = Component.text("Raid Selection", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
    private static final String GUI_ID = "raid_selection";

    public RaidSelectionGUI(RaidModule manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        int size = 27; // 固定サイズ
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        updateInventory(inv, player);
        player.openInventory(inv);

        // 定期更新タスク (GUIを開いている間のみ)
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTopInventory().equals(inv)) {
                    updateInventory(inv, player);
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(ImpossbleEscapeMC.getInstance(), 20L, 20L);
    }

    private void updateInventory(Inventory inv, Player player) {
        List<String> mapIds = manager.getMapIds();
        int slot = 11; // 2つ並べる場合にバランスよく

        for (String id : mapIds) {
            if (slot >= 16) break;

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                boolean isQueued = id.equals(manager.getQueuedMap(player));
                meta.displayName(Component.text(id, isQueued ? NamedTextColor.GOLD : NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                
                RaidInstance active = manager.getActiveRaid(id);
                if (active != null) {
                    lore.add(Component.text("● ", NamedTextColor.RED).append(Component.text("進行中 (レイド中)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("  参加人数: ", NamedTextColor.GRAY).append(Component.text(active.getPlayerCount() + "名", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("● ", NamedTextColor.GREEN).append(Component.text("待機中 (空きあり)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                }

                lore.add(Component.text("  待機人数: ", NamedTextColor.GRAY).append(Component.text(manager.getQueueCount(id) + "名", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                
                // マップ個別のタイマーを表示
                int tl = manager.getMapTimeLeft(id);
                lore.add(Component.text("  出撃まで: ", NamedTextColor.GRAY).append(Component.text(formatTime(tl), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
                
                lore.add(Component.empty());
                if (isQueued) {
                    lore.add(Component.text("▶ 出撃待機中", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("  (クリックでキャンセル)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(Component.text("▶ クリックで出撃待機列に参加", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                }
                
                meta.lore(lore);
                
                // PDCにマップIDを保存
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(PDCKeys.RAID_MAP_ID, PDCKeys.STRING, id);
                pdc.set(PDCKeys.GUI_TYPE, PDCKeys.STRING, GUI_ID);
                
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot += 2; // 間隔を空ける
        }
        
        // 背景埋め
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack current = inv.getItem(i);
            if (current == null || current.getType() == Material.AIR || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                inv.setItem(i, filler);
            }
        }
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String guiType = pdc.get(PDCKeys.GUI_TYPE, PDCKeys.STRING);
        if (!GUI_ID.equals(guiType)) return;
        
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String mapId = pdc.get(PDCKeys.RAID_MAP_ID, PDCKeys.STRING);
        if (mapId == null) return;
            
        // パーティ対応
        Party party = ImpossbleEscapeMC.getInstance().getPartyManager().getParty(player.getUniqueId());
        if (party != null && !party.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("パーティリーダーのみが待機列を操作できます。", NamedTextColor.RED));
            return;
        }

        if (mapId.equals(manager.getQueuedMap(player))) {
            // キャンセル
            if (party != null) {
                for (UUID member : party.getMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) manager.leaveQueue(p);
                }
            } else {
                manager.leaveQueue(player);
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.5f);
        } else {
            // 参加
            if (party != null) {
                for (UUID member : party.getMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) manager.joinQueue(p, mapId);
                }
            } else {
                manager.joinQueue(player, mapId);
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        }
        
        updateInventory(event.getInventory(), player);
    }
}
