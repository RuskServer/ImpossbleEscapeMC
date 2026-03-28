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
                lore.add(Component.text("▶ [右クリック] で納品 (対象アイテムを一括納品)", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            }
        } else if (questModule.canStart(data, q)) {
            lore.add(Component.text("▶ [左クリック] でクエストを受領する", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.QUEST_ID, PDCKeys.STRING, q.getId());
        item.setItemMeta(meta);
    }

    private List<String> wrapText(String text, int limit) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : text.split(" ")) {
            if (currentLine.length() + word.length() + 1 > limit) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                // 単語が制限より長い場合は強制分割
                while (word.length() > limit) {
                    lines.add(word.substring(0, limit));
                    word = word.substring(limit);
                }
            }
            if (currentLine.length() > 0) currentLine.append(" ");
            currentLine.append(word);
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
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
            if (questModule.isAllObjectivesMet(q, active)) {
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
        boolean anyHandedIn = false;
        Map<String, Integer> totalHandedIn = new HashMap<>();

        for (int i = 0; i < q.getObjectives().size(); i++) {
            QuestObjective obj = q.getObjectives().get(i);
            if (!(obj instanceof HandInObjective)) continue;
            if (obj.isCompleted(active, i)) continue;

            HandInObjective hio = (HandInObjective) obj;
            
            // Scan inventory for this objective
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
                if (obj.isCompleted(active, i)) break;

                ItemMeta meta = item.getItemMeta();
                String itemId = meta != null ? meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
                if (itemId == null) continue;

                ItemDefinition def = ItemRegistry.get(itemId);
                if (def == null) continue;

                boolean isFIR = meta.getPersistentDataContainer().getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
                
                // Match check
                if (hio.isRequireFIR() && !isFIR) continue;
                
                boolean match = false;
                if (hio.getItemId() != null) {
                    match = hio.getItemId().equalsIgnoreCase(itemId);
                } else if (hio.getItemType() != null) {
                    match = hio.getItemType().equalsIgnoreCase(def.type);
                }

                if (match) {
                    int current = active.getProgress(i);
                    int needed = hio.getTargetAmount() - current;
                    if (needed <= 0) break;

                    int toTake = Math.min(item.getAmount(), needed);
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("itemId", itemId);
                    params.put("itemType", def.type);
                    params.put("isFIR", isFIR);
                    params.put("amount", toTake);

                    if (hio.updateProgress(player, data, active, i, QuestTrigger.HAND_IN, params)) {
                        item.setAmount(item.getAmount() - toTake);
                        totalHandedIn.put(def.displayName, totalHandedIn.getOrDefault(def.displayName, 0) + toTake);
                        anyHandedIn = true;
                        data.setDirty(true);
                    }
                }
            }
        }

        if (anyHandedIn) {
            totalHandedIn.forEach((name, amount) -> {
                player.sendMessage(Component.text("納品しました: " + name + " x" + amount, NamedTextColor.GREEN));
            });
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
            setupGUI();
        } else {
            player.sendMessage(Component.text("納品可能なアイテムをインベントリに持っていません。", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
