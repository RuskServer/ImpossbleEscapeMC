package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
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

public class TraderQuantityGUI implements Listener {
    private final TraderModule traderModule;
    private final TraderDefinition trader;
    private final TraderItem traderItem;
    private final Player player;
    private final TraderGUI parentGUI;
    private final Inventory inventory;
    private int quantity = 1;

    public TraderQuantityGUI(TraderModule traderModule, TraderDefinition trader, TraderItem traderItem, Player player, TraderGUI parentGUI) {
        this.traderModule = traderModule;
        this.trader = trader;
        this.traderItem = traderItem;
        this.player = player;
        this.parentGUI = parentGUI;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("購入個数を選択").decoration(TextDecoration.ITALIC, false));
        
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, bg);

        updateQuantityDisplay();

        // 操作ボタン
        inventory.setItem(11, createButton(Material.RED_TERRACOTTA, "§c-10", "§7個数を10減らす"));
        inventory.setItem(12, createButton(Material.ORANGE_TERRACOTTA, "§6-1", "§7個数を1減らす"));
        inventory.setItem(14, createButton(Material.LIME_TERRACOTTA, "§a+1", "§7個数を1増やす"));
        inventory.setItem(15, createButton(Material.GREEN_TERRACOTTA, "§2+10", "§7個数を10増やす"));

        // 決定・キャンセル
        inventory.setItem(22, createButton(Material.EMERALD_BLOCK, "§a§l購入を確定", "§7合計: " + (traderItem.price * quantity) + "₽"));
        inventory.setItem(18, createButton(Material.BARRIER, "§c戻る", "§7トレーダー画面に戻ります"));
    }

    private void updateQuantityDisplay() {
        ItemStack item = ItemFactory.create(traderItem.itemId);
        if (item == null) return;
        item.setAmount(Math.max(1, Math.min(64, quantity))); // 表示上のみ

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(traderItem.itemId, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("現在の選択個数: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(quantity + " 個", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("合計価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text((traderItem.price * quantity) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(13, item);

        // 購入確定ボタンの価格も更新
        ItemStack confirm = inventory.getItem(22);
        if (confirm != null) {
            ItemMeta cMeta = confirm.getItemMeta();
            List<Component> cLore = new ArrayList<>();
            cLore.add(Component.text("合計価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text((traderItem.price * quantity) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
            cMeta.lore(cLore);
            confirm.setItemMeta(cMeta);
        }
    }

    private ItemStack createButton(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        l.add(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 11) { quantity = Math.max(1, quantity - 10); updateQuantityDisplay(); }
        else if (slot == 12) { quantity = Math.max(1, quantity - 1); updateQuantityDisplay(); }
        else if (slot == 14) { quantity = Math.min(1000, quantity + 1); updateQuantityDisplay(); }
        else if (slot == 15) { quantity = Math.min(1000, quantity + 10); updateQuantityDisplay(); }
        else if (slot == 18) { player.closeInventory(); parentGUI.open(); }
        else if (slot == 22) {
            PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
            ItemStack icon = ItemFactory.create(traderItem.itemId);
            player.closeInventory();
            parentGUI.handleBuy(icon, data, quantity);
            parentGUI.open();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
