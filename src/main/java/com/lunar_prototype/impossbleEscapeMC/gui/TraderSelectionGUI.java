package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
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
import java.util.Collection;
import java.util.List;

public class TraderSelectionGUI implements Listener {
    private final Player player;
    private final Inventory inventory;
    private final TraderModule traderModule;
    private final List<TraderDefinition> traderList;

    public TraderSelectionGUI(Player player, TraderModule traderModule) {
        this.player = player;
        this.traderModule = traderModule;
        this.traderList = new ArrayList<>(traderModule.getAllTraders());
        
        int size = ((traderList.size() / 9) + 1) * 9;
        this.inventory = Bukkit.createInventory(null, Math.min(54, size), Component.text("トレーダー一覧").decoration(TextDecoration.ITALIC, false));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        
        for (int i = 0; i < traderList.size(); i++) {
            TraderDefinition trader = traderList.get(i);
            ItemStack item = new ItemStack(trader.type == TraderType.BUY ? Material.EMERALD : Material.GOLD_INGOT);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(trader.displayName, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("タイプ: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(trader.type == TraderType.BUY ? "購入" : "売却", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.text("クリックして取引を開始", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < traderList.size()) {
            TraderDefinition trader = traderList.get(slot);
            player.closeInventory();
            new TraderGUI(traderModule, trader, player).open();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
