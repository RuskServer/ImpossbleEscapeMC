package com.lunar_prototype.impossbleEscapeMC.modules.stash;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class StashGUI implements Listener {
    private final Player player;
    private final Inventory inventory;
    private final int page;
    private final StashModule module;
    /** タイトル (下の欄をバックパックの表示にした時のも) */
    private final java.util.function.Function<com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.Bag, Component> title;

    public StashGUI(Player player, Inventory inventory, int page, StashModule module,
                    java.util.function.Function<com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.Bag, Component> title) {
        this.title = title;
        this.player = player;
        this.inventory = inventory;
        this.page = page;
        this.module = module;
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        player.openInventory(inventory);
        // オフハンドにバックパックがあれば、ルート漁りと同じオーバーレイで持ち物の段に中身を出す。
        // openInventory の後 (InventoryOpenEvent でカーソルのアイテムが持ち物へ戻った後) に始める
        if (player.getOpenInventory().getTopInventory().equals(inventory)) {
            com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener overlay =
                    ImpossbleEscapeMC.getInstance().getServiceContainer()
                            .get(com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener.class);
            if (overlay != null) overlay.beginSession(player, inventory, title);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            module.saveStash(player, page, inventory);
            HandlerList.unregisterAll(this);
            // Optionally return to PDA after short delay to avoid close/reopen flicker
            // Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), () -> new PDAGUI(player).open(), 1L);
        }
    }
}
