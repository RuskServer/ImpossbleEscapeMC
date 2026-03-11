package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.party.Party;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RaidSelectionGUI implements Listener {
    private final RaidModule manager;
    private static final Component TITLE = Component.text("Raid Selection", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);

    public RaidSelectionGUI(RaidModule manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        List<String> mapIds = manager.getMapIds();
        int size = 27; // 固定サイズで見栄えを良くする
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
        int slot = 10; // 中央付近から配置

        for (String id : mapIds) {
            if (slot >= 17) break; // とりあえず1列分

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                boolean isQueued = id.equals(manager.getQueuedMap(player));
                meta.displayName(Component.text(id, isQueued ? NamedTextColor.GOLD : NamedTextColor.GREEN, TextDecoration.BOLD));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                
                RaidInstance active = manager.getActiveRaid(id);
                if (active != null) {
                    lore.add(Component.text("● ", NamedTextColor.RED).append(Component.text("進行中", NamedTextColor.GRAY)));
                    lore.add(Component.text("  参加人数: ", NamedTextColor.GRAY).append(Component.text(active.getPlayerCount() + "名", NamedTextColor.WHITE)));
                } else {
                    lore.add(Component.text("● ", NamedTextColor.GREEN).append(Component.text("待機中", NamedTextColor.GRAY)));
                }

                lore.add(Component.text("  待機人数: ", NamedTextColor.GRAY).append(Component.text(manager.getQueueCount(id) + "名", NamedTextColor.WHITE)));
                
                int tl = manager.getGlobalTimeLeft();
                lore.add(Component.text("  次回の出撃まで: ", NamedTextColor.GRAY).append(Component.text(formatTime(tl), NamedTextColor.AQUA)));
                
                lore.add(Component.empty());
                if (isQueued) {
                    lore.add(Component.text("▶ 出撃待機中 (クリックでキャンセル)", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    // 待機中はエンチャントの輝きを追加して目立たせる
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(Component.text("▶ クリックで出撃待機列に参加", NamedTextColor.YELLOW));
                }
                
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }
        
        // 背景埋め
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String mapId = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            
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
}
