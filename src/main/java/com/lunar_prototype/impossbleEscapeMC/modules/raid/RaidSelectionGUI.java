package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.GuiBackground;
import com.lunar_prototype.impossbleEscapeMC.party.Party;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RaidSelectionGUI implements Listener {
    private final RaidModule manager;
    private static final String GUI_ID = "raid_selection";
    private static final int SIZE = 54;
    /**
     * 地域の地形図 (背景) の上の各マップの地点。背景の画像で地点を描いた位置なので、
     * 変えるときは IEM_shader/tools/region_map.py の FACTORY_SLOT / COASTAL_SLOT も合わせて描き直す
     */
    private static final Map<String, Integer> MAP_SLOTS = Map.of(
            "ノヴォザリエ薬品工場", 12,
            "ノヴォザリエ沿岸工業地区", 41);
    /** 地図にまだ地点の無いマップ (追加したばかりなど) を置く場所 (地図の右上の山の中) */
    private static final int[] SPARE_SLOTS = {6, 7};
    private static final int AUTO_MATCH_SLOT = 45;

    /**
     * この画面のインベントリの目印。背景の画像を見せるため空きスロットを残しているので、
     * 開いている間のクリックとドラッグはすべて止める (物を置けないように)
     */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public RaidSelectionGUI(RaidModule manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, GuiBackground.RAID.title());
        holder.inventory = inv;

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
        // オートマッチングボタン
        ItemStack autoMatch = new ItemStack(Material.COMPASS);
        ItemMeta autoMeta = autoMatch.getItemMeta();
        if (autoMeta != null) {
            autoMeta.displayName(Component.text("オートマッチング", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> autoLore = new ArrayList<>();
            autoLore.add(Component.empty());
            autoLore.add(Component.text("最適なレイドを自動で選択し出撃します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            autoLore.add(Component.text("- 途中参加可能なレイドを優先", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            autoLore.add(Component.text("- または開始が最も近いマップ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            autoLore.add(Component.empty());
            autoLore.add(Component.text("▶ クリックでマッチング開始", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            autoMeta.lore(autoLore);
            
            PersistentDataContainer pdc = autoMeta.getPersistentDataContainer();
            pdc.set(PDCKeys.RAID_MAP_ID, PDCKeys.STRING, "AUTO_MATCH");
            pdc.set(PDCKeys.GUI_TYPE, PDCKeys.STRING, GUI_ID);
            autoMatch.setItemMeta(autoMeta);
        }
        inv.setItem(AUTO_MATCH_SLOT, autoMatch);

        int spare = 0;
        for (String id : manager.getMapIds()) {
            Integer slot = MAP_SLOTS.get(id);
            if (slot == null) {
                if (spare >= SPARE_SLOTS.length) continue;
                slot = SPARE_SLOTS[spare++];
            }

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                boolean isQueued = id.equals(manager.getQueuedMap(player));
                meta.displayName(Component.text(id, isQueued ? NamedTextColor.GOLD : NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                
                RaidInstance active = manager.getActiveRaid(id);
                long age = (System.currentTimeMillis() - manager.getMapLastStartTime(id)) / 1000;
                boolean isLateJoinable = age < RaidModule.LATE_JOIN_WINDOW;
                // 地図の上の目印 (リソースパックの iem:raid_marker_*)。色で状態がわかる: 待機中 / 途中参加可 / 進行中
                String state = isLateJoinable ? "join" : active != null ? "live" : "open";
                meta.setItemModel(new NamespacedKey("iem", "raid_marker_" + state));

                if (active != null || isLateJoinable) {
                    if (isLateJoinable) {
                        lore.add(Component.text("● ", NamedTextColor.AQUA).append(Component.text("進行中 (途中参加可能)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                    } else {
                        lore.add(Component.text("● ", NamedTextColor.RED).append(Component.text("進行中 (レイド中)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                    }
                    int playerCount = active != null ? active.getPlayerCount() : 0;
                    lore.add(Component.text("  参加人数: ", NamedTextColor.GRAY).append(Component.text(playerCount + "名", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
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
                } else if (isLateJoinable) {
                    lore.add(Component.text("▶ クリックで途中参加", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
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
        }
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String guiType = pdc.get(PDCKeys.GUI_TYPE, PDCKeys.STRING);
        if (!GUI_ID.equals(guiType)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String mapId = pdc.get(PDCKeys.RAID_MAP_ID, PDCKeys.STRING);
        if (mapId == null) return;

        if (mapId.equals("AUTO_MATCH")) {
            manager.autoMatch(player);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            player.closeInventory();
            return;
        }
            
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
