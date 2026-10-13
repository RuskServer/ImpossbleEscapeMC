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

    public StashGUI(Player player, Inventory inventory, int page, StashModule module) {
        this.player = player;
        this.inventory = inventory;
        this.page = page;
        this.module = module;
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        player.openInventory(inventory);
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
