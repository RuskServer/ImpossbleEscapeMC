package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
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
 * トレーダー別のクエスト管理GUI。上の5段にクエストを並べ、一番下の段の中央に「まとめて納品」
 * (進行中のクエストの納品をこのトレーダーの分まとめて行う画面、{@link QuestHandInGUI#forTrader}) を置く
 */
public class TraderQuestGUI implements Listener {
    /** クエストを並べるスロットの数 (上の5段) */
    private static final int QUEST_SLOTS = 45;
    private static final int BULK_HAND_IN_SLOT = 49;

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
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        List<QuestDefinition> traderQuests = questModule.getQuestsByTrader(trader.id);

        int slot = 0;
        for (QuestDefinition q : traderQuests) {
            if (slot >= QUEST_SLOTS) break;

            boolean isCompleted = data.isQuestCompleted(q.getId());
            ActiveQuest active = data.getActiveQuests().get(q.getId());
            boolean isAvailable = questModule.canStart(data, q);

            ItemStack item;
            if (isCompleted) {
                item = new ItemStack(Material.WRITTEN_BOOK);
                setQuestMeta(item, q, "§a§l完了済み", NamedTextColor.GREEN, null, data);
            } else if (active != null) {
                item = new ItemStack(Material.WRITABLE_BOOK);
                boolean allDone = questModule.isAllObjectivesMet(q, active);
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
        inventory.setItem(BULK_HAND_IN_SLOT, bulkHandInButton(data));
    }

    /** まとめて納品のボタン。納品できるクエストが無ければ押せない見た目にする */
    private ItemStack bulkHandInButton(PlayerData data) {
        List<QuestDefinition> open = QuestHandInGUI.openHandIns(data, trader, questModule);
        ItemStack item = new ItemStack(open.isEmpty() ? Material.GRAY_DYE : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("まとめて納品", open.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (open.isEmpty()) {
            lore.add(Component.text("納品が残っている進行中のクエストはありません", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("入れた物を、次のクエストの納品に順に充てます", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            for (QuestDefinition q : open) {
                lore.add(Component.text("- " + q.getDisplayName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("▶ [クリック] で納品画面を開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setQuestMeta(ItemStack item, QuestDefinition q, String status, NamedTextColor color, ActiveQuest active, PlayerData data) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(q.getDisplayName() + " [" + status + "]", color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        
        // 説明文の自動改行
        for (String line : wrapText(q.getDescription(), 30)) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());

        // 目標の表示
        lore.add(Component.text("目標:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < q.getObjectives().size(); i++) {
            QuestObjective obj = q.getObjectives().get(i);
            String progressText = (active != null) ? " (" + obj.getProgressText(active, i) + ")" : "";
            boolean done = (active != null && obj.isCompleted(active, i));
            
            String fullDesc = "- " + obj.getDescription() + progressText;
            for (String line : wrapText(fullDesc, 30)) {
                lore.add(Component.text(line, done ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());

        // 操作ガイド
        if (active != null) {
            if (questModule.isAllObjectivesMet(q, active)) {
                lore.add(Component.text("▶ [左クリック] で報酬を受け取って完了", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("▶ [右クリック] で納品画面を開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            }
        } else if (questModule.canStart(data, q)) {
            lore.add(Component.text("▶ [左クリック] でクエストを受領する", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.QUEST_ID, PDCKeys.STRING, q.getId());
        item.setItemMeta(meta);
    }

    private List<String> wrapText(String text, int limit) {
        return QuestText.wrap(text, limit);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (event.getRawSlot() == BULK_HAND_IN_SLOT) {
            QuestHandInGUI bulk = QuestHandInGUI.forTrader(player, trader, questModule);
            if (bulk == null) {
                player.sendMessage(Component.text("納品が残っている進行中のクエストはありません。", NamedTextColor.RED));
                return;
            }
            player.closeInventory();
            bulk.open();
            return;
        }

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
            if (questModule.isAllObjectivesMet(q, active)) {
                // 完了処理
                questModule.completeQuest(player, data, q);
                setupGUI();
            } else if (event.isRightClick()) {
                // 納品画面を開く (納品する物はプレイヤーが選んで入れる)
                if (!QuestHandInGUI.hasOpenHandIn(q, active)) {
                    player.sendMessage(Component.text("このクエストに納品する目標はありません。", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                new QuestHandInGUI(player, trader, questModule, q).open();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
