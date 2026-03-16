package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class PDAGUI implements Listener {
    private final Player player;
    private final Inventory inventory;

    public PDAGUI(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("PDA - Player Information").decoration(TextDecoration.ITALIC, false));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
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

        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        LevelModule levelModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(LevelModule.class);
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());

        // プレイヤーの頭
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.displayName(Component.text(player.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        head.setItemMeta(headMeta);
        inventory.setItem(4, head);

        // レベル情報
        ItemStack stats = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.displayName(Component.text("統計情報", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("レベル: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(data.getLevel(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        
        long currentExp = data.getExperience();
        long requiredExp = levelModule.getRequiredExperience(data.getLevel());
        double progress = (double) currentExp / requiredExp;
        int barLength = 10;
        int completed = (int) (progress * barLength);
        
        StringBuilder progressBar = new StringBuilder("§a");
        progressBar.append("■".repeat(Math.max(0, completed)));
        progressBar.append("§7");
        progressBar.append("■".repeat(Math.max(0, barLength - completed)));
        
        lore.add(Component.text("経験値: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(currentExp + " / " + requiredExp, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("進捗: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(progressBar.toString() + " " + String.format("%.1f", progress * 100) + "%", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("脱出成功回数: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(data.getExtractions(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("所持金: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.1f", data.getBalance()) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        
        statsMeta.lore(lore);
        stats.setItemMeta(statsMeta);
        inventory.setItem(13, stats);
        
        // 閉じるボタン
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inventory.setItem(22, close);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        if (event.getRawSlot() == 22) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
