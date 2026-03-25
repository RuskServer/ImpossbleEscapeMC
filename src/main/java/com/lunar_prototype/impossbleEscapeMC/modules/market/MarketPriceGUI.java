package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 出品価格を設定するGUI
 */
public class MarketPriceGUI implements Listener {
    private final Player player;
    private final MarketModule marketModule;
    private final ItemStack itemToSell;
    private final int originalSlot;
    private final Inventory inventory;
    private double price = 1000.0;

    public MarketPriceGUI(Player player, MarketModule marketModule, ItemStack itemToSell, int originalSlot) {
        this.player = player;
        this.marketModule = marketModule;
        this.itemToSell = itemToSell;
        this.originalSlot = originalSlot;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("Market - Set Price").decoration(TextDecoration.ITALIC, false));
    }

    public void open() {
        setupGUI();
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();

        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inventory.setItem(i, bg);

        updateDisplay();

        // 操作ボタン
        inventory.setItem(10, createButton(Material.RED_TERRACOTTA, "§c-10,000", "§7価格を10,000₽減らす"));
        inventory.setItem(11, createButton(Material.ORANGE_TERRACOTTA, "§6-1,000", "§7価格を1,000₽減らす"));
        inventory.setItem(12, createButton(Material.YELLOW_TERRACOTTA, "§e-100", "§7価格を100₽減らす"));
        
        inventory.setItem(14, createButton(Material.LIME_TERRACOTTA, "§a+100", "§7価格を100₽増やす"));
        inventory.setItem(15, createButton(Material.GREEN_TERRACOTTA, "§2+1,000", "§7価格を1,000₽増やす"));
        inventory.setItem(16, createButton(Material.BLUE_TERRACOTTA, "§b+10,000", "§7価格を10,000₽増やす"));

        // 決定・キャンセル
        inventory.setItem(22, createButton(Material.EMERALD_BLOCK, "§a§l出品を確定", "§7出品価格: " + price + "₽"));
        inventory.setItem(18, createButton(Material.BARRIER, "§cキャンセル", "§7出品をやめて戻ります"));
    }

    private void updateDisplay() {
        ItemStack display = itemToSell.clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        
        lore.add(Component.empty());
        lore.add(Component.text("設定中の価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.1f", price) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore);
        display.setItemMeta(meta);
        inventory.setItem(13, display);

        // 確定ボタンの価格も更新
        ItemStack confirm = inventory.getItem(22);
        if (confirm != null) {
            ItemMeta cMeta = confirm.getItemMeta();
            cMeta.lore(List.of(Component.text("出品価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("%.1f", price) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))));
            confirm.setItemMeta(cMeta);
        }
    }

    private ItemStack createButton(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 10) { price = Math.max(0, price - 10000); updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 11) { price = Math.max(0, price - 1000); updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 12) { price = Math.max(0, price - 100); updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 14) { price += 100; updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 15) { price += 1000; updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 16) { price += 10000; updateDisplay(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
        else if (slot == 18) { player.closeInventory(); new MarketSellGUI(player, marketModule).open(); }
        else if (slot == 22) {
            handleConfirm();
        }
    }

    private void handleConfirm() {
        if (price <= 0) {
            player.sendMessage(Component.text("価格は0₽より大きく設定してください。", NamedTextColor.RED));
            return;
        }

        // プレイヤーのインベントリからアイテムを削除
        ItemStack inInv = player.getInventory().getItem(originalSlot);
        if (inInv == null || !inInv.isSimilar(itemToSell)) {
            player.sendMessage(Component.text("出品しようとしたアイテムがインベントリに見つかりません。", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        player.getInventory().setItem(originalSlot, null);

        try {
            String base64 = SerializationUtil.serializeItemStack(itemToSell);
            MarketListing listing = new MarketListing(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    player.getName(),
                    base64,
                    price,
                    System.currentTimeMillis()
            );
            marketModule.addListing(listing);
            player.sendMessage(Component.text("アイテムを " + price + "₽ でマーケットに出品しました。", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            player.closeInventory();
            new MarketMainGUI(player, marketModule).open();
        } catch (IOException e) {
            player.sendMessage(Component.text("出品中にエラーが発生しました。", NamedTextColor.RED));
            player.getInventory().setItem(originalSlot, itemToSell); // 戻す
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
