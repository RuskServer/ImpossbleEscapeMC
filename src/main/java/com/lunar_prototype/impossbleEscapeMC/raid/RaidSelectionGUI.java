package com.lunar_prototype.impossbleEscapeMC.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaidSelectionGUI implements Listener {
    private final RaidManager manager;
    private final String title = "Raid Selection";

    public RaidSelectionGUI(RaidManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        List<String> mapIds = manager.getMapIds();
        int size = Math.max(9, ((mapIds.size() + 8) / 9) * 9);
        if (size > 54) {
            throw new IllegalStateException("RaidSelectionGUI requires pagination for more than 54 maps");
        }
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title));

        for (String id : mapIds) {
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(id, NamedTextColor.GREEN));
                
                RaidInstance active = manager.getActiveRaid(id);
                List<Component> lore = new ArrayList<>();
                if (active != null) {
                    lore.add(Component.text("Players: " + active.getPlayerCount(), NamedTextColor.GRAY));
                    int tl = active.getTimeLeft();
                    lore.add(Component.text("Time Left: " + String.format("%02d:%02d", tl / 60, tl % 60), NamedTextColor.GRAY));
                } else {
                    lore.add(Component.text("Status: Available", NamedTextColor.YELLOW));
                    lore.add(Component.text("Time Limit: 20:00", NamedTextColor.GRAY));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text(title))) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            
            manager.startRaid(plainName, Collections.singletonList(player));
            player.closeInventory();
        }
    }
}
