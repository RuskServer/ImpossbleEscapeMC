package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

/**
 * PDAから開ける全クエスト進捗確認GUI
 */
public class PDAQuestGUI implements Listener {
    private final Player player;
    private final QuestModule questModule;
    private final PlayerDataModule dataModule;
    private final Inventory inventory;

    public PDAQuestGUI(Player player, QuestModule questModule) {
        this.player = player;
        this.questModule = questModule;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.inventory = Bukkit.createInventory(null, 54, Component.text("Active Quests").decoration(TextDecoration.ITALIC, false));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());

        int slot = 0;
        for (ActiveQuest active : data.getActiveQuests().values()) {
            if (slot >= 54) break;
            QuestDefinition q = questModule.getQuest(active.getQuestId());
            if (q == null) continue;

            ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(q.getDisplayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("依頼主: " + q.getTraderId(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("進捗状況:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
            for (int i = 0; i < q.getObjectives().size(); i++) {
                QuestObjective obj = q.getObjectives().get(i);
                boolean done = obj.isCompleted(active, i);
                lore.add(Component.text("- " + obj.getDescription() + " (" + obj.getProgressText(active, i) + ")", 
                        done ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        // 受領可能なクエストを表示 (空きスロットから開始)
        List<QuestDefinition> startable = questModule.getStartableQuests(data);
        for (QuestDefinition q : startable) {
            if (slot >= 45) break; // 下部3行分（45-53）はコントロール用に空ける

            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(q.getDisplayName() + " [受領可能]", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("依頼主: " + q.getTraderId(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(q.getDescription(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("▶ クリックして受領", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.QUEST_ID, PDCKeys.STRING, q.getId());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        
        // 45-53スロットを背景で埋める（境界線として）
        ItemStack spacer = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta spacerMeta = spacer.getItemMeta();
        spacerMeta.displayName(Component.empty());
        spacer.setItemMeta(spacerMeta);
        for (int i = 45; i < 54; i++) {
            if (i == 49) continue; // 戻るボタン
            inventory.setItem(i, spacer);
        }

        // 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("PDAに戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        int rawSlot = event.getRawSlot();
        if (rawSlot == 49) {
            player.closeInventory();
            new com.lunar_prototype.impossbleEscapeMC.gui.PDAGUI(player).open();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String questId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.QUEST_ID, PDCKeys.STRING);
        if (questId != null) {
            QuestDefinition q = questModule.getQuest(questId);
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (q != null && data != null && questModule.canStart(data, q)) {
                // クエスト受領処理
                data.getActiveQuests().put(questId, new ActiveQuest(questId));
                data.setDirty(true);
                player.sendMessage(Component.text("クエストを受領しました: " + q.getDisplayName(), NamedTextColor.GREEN));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                setupGUI(); // 再描画
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
