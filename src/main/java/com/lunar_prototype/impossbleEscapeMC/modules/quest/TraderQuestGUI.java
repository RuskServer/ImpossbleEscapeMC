package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

/**
 * トレーダー別のクエスト管理GUI
 */
public class TraderQuestGUI implements Listener {
    private final Player player;
    private final TraderDefinition trader;
    private final QuestModule questModule;
    private final PlayerDataModule dataModule;
    private final Inventory inventory;

    public TraderQuestGUI(Player player, TraderDefinition trader, QuestModule questModule) {
        this.player = player;
        this.trader = trader;
        this.questModule = questModule;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.inventory = Bukkit.createInventory(null, 54, Component.text("Quests - " + trader.displayName).decoration(TextDecoration.ITALIC, false));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        List<QuestDefinition> traderQuests = questModule.getQuestsByTrader(trader.id);

        int slot = 0;
        for (QuestDefinition q : traderQuests) {
            if (slot >= 54) break;

            boolean isCompleted = data.isQuestCompleted(q.getId());
            ActiveQuest active = data.getActiveQuests().get(q.getId());
            boolean isAvailable = questModule.canStart(data, q);

            ItemStack item;
            if (isCompleted) {
                item = new ItemStack(Material.WRITTEN_BOOK);
                setQuestMeta(item, q, "§a§l完了済み", NamedTextColor.GREEN, null, data);
            } else if (active != null) {
                item = new ItemStack(Material.WRITABLE_BOOK);
                boolean allDone = isAllObjectivesMet(q, active);
                setQuestMeta(item, q, allDone ? "§e§l報告可能" : "§6§l進行中", allDone ? NamedTextColor.YELLOW : NamedTextColor.GOLD, active, data);
            } else if (isAvailable) {
                item = new ItemStack(Material.BOOK);
                setQuestMeta(item, q, "§b§l受領可能", NamedTextColor.AQUA, null, data);
            } else {
                item = new ItemStack(Material.BARRIER);
                setQuestMeta(item, q, "§c§lロック中", NamedTextColor.RED, null, data);
            }

            inventory.setItem(slot++, item);
        }
    }

    private void setQuestMeta(ItemStack item, QuestDefinition q, String status, NamedTextColor color, ActiveQuest active, PlayerData data) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(q.getDisplayName() + " [" + status + "]", color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(q.getDescription(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // 目標の表示
        lore.add(Component.text("目標:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < q.getObjectives().size(); i++) {
            QuestObjective obj = q.getObjectives().get(i);
            String progressText = (active != null) ? " (" + obj.getProgressText(active, i) + ")" : "";
            boolean done = (active != null && obj.isCompleted(active, i));
            
            lore.add(Component.text("- " + obj.getDescription() + progressText, done ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());

        // 操作ガイド
        if (active != null) {
            if (isAllObjectivesMet(q, active)) {
                lore.add(Component.text("▶ 左クリックで報酬を受け取って完了", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("▶ 右クリックで手持ちアイテムを納品", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            }
        } else if (questModule.canStart(data, q)) {
            lore.add(Component.text("▶ クリックで受領する", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.QUEST_ID, PDCKeys.STRING, q.getId());
        item.setItemMeta(meta);
    }

    private boolean isAllObjectivesMet(QuestDefinition q, ActiveQuest active) {
        for (int i = 0; i < q.getObjectives().size(); i++) {
            if (!q.getObjectives().get(i).isCompleted(active, i)) return false;
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String questId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.QUEST_ID, PDCKeys.STRING);
        if (questId == null) return;

        QuestDefinition q = questModule.getQuest(questId);
        if (q == null) return;

        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        ActiveQuest active = data.getActiveQuests().get(questId);

        if (active == null) {
            // 受領処理
            if (questModule.canStart(data, q)) {
                data.getActiveQuests().put(questId, new ActiveQuest(questId));
                data.setDirty(true);
                player.sendMessage(Component.text("クエストを受領しました: " + q.getDisplayName(), NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                setupGUI();
            }
        } else {
            if (isAllObjectivesMet(q, active)) {
                // 完了処理
                questModule.completeQuest(player, data, q);
                setupGUI();
            } else if (event.isRightClick()) {
                // 納品処理
                handleHandIn(q, active, data);
            }
        }
    }

    private void handleHandIn(QuestDefinition q, ActiveQuest active, PlayerData data) {
        boolean handedIn = false;
        for (int i = 0; i < q.getObjectives().size(); i++) {
            QuestObjective obj = q.getObjectives().get(i);
            if (obj instanceof HandInObjective && !obj.isCompleted(active, i)) {
                // インベントリから納品可能なアイテムを探す
                if (tryHandIn(player, (HandInObjective) obj, i, active, data)) {
                    handedIn = true;
                }
            }
        }
        if (handedIn) {
            setupGUI();
        } else {
            player.sendMessage(Component.text("納品可能なアイテムを持っていません。", NamedTextColor.RED));
        }
    }

    private boolean tryHandIn(Player player, HandInObjective obj, int index, ActiveQuest active, PlayerData data) {
        // アイテムIDまたはカテゴリーが一致するものをインベントリから探す
        // 簡単のためメインハンドから優先的に納品
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        String itemId = meta != null ? meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        if (itemId == null) return false;

        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null) return false;

        boolean isFIR = meta.getPersistentDataContainer().getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte)0) == 1;
        if (obj.isRequireFIR() && !isFIR) {
            player.sendMessage(Component.text("この目標にはFIR品(レイドで見つけた品)が必要です。", NamedTextColor.RED));
            return false;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("itemId", itemId);
        params.put("itemType", def.type);
        params.put("amount", item.getAmount());
        params.put("isFIR", isFIR);

        // 進行を試みる
        if (obj.updateProgress(player, data, active, index, QuestTrigger.HAND_IN, params)) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(Component.text("アイテムを納品しました: " + def.displayName + " x" + item.getAmount(), NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
