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
        
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            new com.lunar_prototype.impossbleEscapeMC.gui.PDAGUI(player).open();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
