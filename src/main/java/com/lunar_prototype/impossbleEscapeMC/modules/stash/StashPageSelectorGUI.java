package com.lunar_prototype.impossbleEscapeMC.modules.stash;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
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

public class StashPageSelectorGUI implements Listener {
    private final Player player;
    private final Inventory inventory;
    private final StashModule stashModule;
    private final PlayerData data;

    public StashPageSelectorGUI(Player player, StashModule stashModule) {
        this.player = player;
        this.stashModule = stashModule;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("Stash - Page Selection").decoration(TextDecoration.ITALIC, false));
        
        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.data = dataModule.getPlayerData(player.getUniqueId());
        
        setupGUI();
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        
        // 背景
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        int maxPage = stashModule.getMaxUnlockedPage(data.getStashLevel());

        for (int i = 1; i <= 5; i++) {
            boolean unlocked = i <= maxPage;
            ItemStack item = new ItemStack(unlocked ? Material.CHEST : Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            
            meta.displayName(Component.text("ページ " + i, unlocked ? NamedTextColor.YELLOW : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            if (unlocked) {
                int rows = stashModule.getRows(data.getStashLevel(), i);
                lore.add(Component.text("状態: ", NamedTextColor.GRAY).append(Component.text("解除済み", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("サイズ: ", NamedTextColor.GRAY).append(Component.text(rows + "行", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("状態: ", NamedTextColor.GRAY).append(Component.text("未解除", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("HideoutでStashをアップグレードしてください", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
            
            meta.lore(lore);
            item.setItemMeta(meta);
            
            // Slot 11, 12, 13, 14, 15
            inventory.setItem(10 + i, item);
        }

        // 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("戻る", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(22, back);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot >= 11 && slot <= 15) {
            int page = slot - 10;
            if (page <= stashModule.getMaxUnlockedPage(data.getStashLevel())) {
                player.closeInventory();
                stashModule.openStash(player, page);
            }
        } else if (slot == 22) {
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
