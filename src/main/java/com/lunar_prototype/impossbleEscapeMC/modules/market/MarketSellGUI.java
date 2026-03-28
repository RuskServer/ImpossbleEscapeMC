package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
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

import java.util.ArrayList;
import java.util.List;

/**
 * アイテム出品用のGUI。プレイヤーのインベントリからFIRアイテムを選択する。
 */
public class MarketSellGUI implements Listener {
    private final Player player;
    private final MarketModule marketModule;
    private final Inventory inventory;

    public MarketSellGUI(Player player, MarketModule marketModule) {
        this.player = player;
        this.marketModule = marketModule;
        this.inventory = Bukkit.createInventory(null, 45, Component.text("Market - Select Item to Sell").decoration(TextDecoration.ITALIC, false));
    }

    public void open() {
        setupGUI();
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();

        ItemStack[] contents = player.getInventory().getContents();
        int slot = 0;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (slot >= 36) break; // メインインベントリのみ

            ItemStack icon = item.clone();
            boolean isFir = marketModule.isFir(item);
            boolean canSell = marketModule.canSellOnMarket(item);
            
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();
            
            lore.add(Component.empty());
            if (canSell) {
                if (isFir) {
                    lore.add(Component.text("FIRステータス: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("あり (出品可能)", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
                } else {
                    lore.add(Component.text("FIRステータス: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("なし (弾薬は出品可能)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
                }
                lore.add(Component.text("クリックして価格設定へ", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("FIRステータス: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("なし (出品不可)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
                lore.add(Component.text("※マーケットではFIRアイテム、または弾薬のみ売却できます。", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
            }
            
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot++, icon);
        }

        // 戻るボタン
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(40, back);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 40) {
            new MarketMainGUI(player, marketModule).open();
            return;
        }

        if (slot >= 0 && slot < 36) {
            ItemStack clicked = inventory.getItem(slot);
            if (clicked == null || clicked.getType() == Material.AIR) return;

            // 元のアイテムを特定
            ItemStack[] contents = player.getInventory().getContents();
            ItemStack original = contents[slot];
            if (original == null || original.getType() == Material.AIR) return;

            if (!marketModule.canSellOnMarket(original)) {
                player.sendMessage(Component.text("出品するにはFIRステータス、または弾薬である必要があります。", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            // 価格設定画面へ
            new MarketPriceGUI(player, marketModule, original, slot).open();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
