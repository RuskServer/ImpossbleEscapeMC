package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
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
import java.util.List;

public class TraderGUI implements Listener {
    private final TraderModule traderModule;
    private final TraderDefinition trader;
    private final Player player;
    private final Inventory inventory;

    public TraderGUI(TraderModule traderModule, TraderDefinition trader, Player player) {
        this.traderModule = traderModule;
        this.trader = trader;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, Component.text(trader.displayName));
        
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugins()[0]); // Briefly register
    }

    public void open() {
        refresh();
        player.openInventory(inventory);
    }

    private void refresh() {
        inventory.clear();
        PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
        traderModule.checkAndResetDailyPurchases(data);

        int slot = 0;
        for (TraderItem ti : trader.items) {
            ItemDefinition def = ItemRegistry.get(ti.itemId);
            if (def == null) continue;

            ItemStack icon = ItemFactory.create(ti.itemId);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();

            lore.add(Component.empty());
            if (trader.type == TraderType.BUY) {
                lore.add(Component.text("価格: ", NamedTextColor.GRAY).append(Component.text(ti.price + "₽", NamedTextColor.GOLD)));
                if (ti.dailyLimit > 0) {
                    int bought = data.getDailyPurchases().getOrDefault(trader.id + "_" + ti.itemId, 0);
                    int remaining = Math.max(0, ti.dailyLimit - bought);
                    lore.add(Component.text("本日の残り制限: ", NamedTextColor.GRAY).append(Component.text(remaining + "/" + ti.dailyLimit, NamedTextColor.YELLOW)));
                }
                lore.add(Component.text("クリックで購入", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("買取価格: ", NamedTextColor.GRAY).append(Component.text(ti.price + "₽", NamedTextColor.GOLD)));
                lore.add(Component.text("インベントリ内のアイテムをクリックで売却", NamedTextColor.YELLOW));
            }

            meta.lore(lore);
            // メタデータに情報を埋め込む
            meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, ti.itemId);
            icon.setItemMeta(meta);

            inventory.setItem(slot++, icon);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (event.getWhoClicked() != player) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
        traderModule.checkAndResetDailyPurchases(data);

        if (trader.type == TraderType.BUY) {
            // 購入処理 (上のインベントリをクリックした場合)
            if (event.getRawSlot() < inventory.getSize()) {
                handleBuy(clicked, data);
            }
        } else {
            // 売却処理 (下のインベントリをクリックした場合)
            if (event.getRawSlot() >= inventory.getSize()) {
                handleSell(clicked, data);
            }
        }
    }

    private void handleBuy(ItemStack clicked, PlayerData data) {
        String itemId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
        
        if (ti == null) return;

        // 制限チェック
        if (ti.dailyLimit > 0) {
            int bought = data.getDailyPurchases().getOrDefault(trader.id + "_" + ti.itemId, 0);
            if (bought >= ti.dailyLimit) {
                player.sendMessage(Component.text("本日の購入制限に達しています。", NamedTextColor.RED));
                return;
            }
        }

        // 所持金チェックと支払い
        if (traderModule.getEconomyModule().withdraw(player.getUniqueId(), ti.price)) {
            ItemStack item = ItemFactory.create(ti.itemId);
            if (player.getInventory().addItem(item).isEmpty()) {
                if (ti.dailyLimit > 0) {
                    data.incrementPurchase(trader.id + "_" + ti.itemId);
                    traderModule.getDataModule().saveAsync(player.getUniqueId());
                }
                player.sendMessage(Component.text(ti.itemId + " を購入しました。", NamedTextColor.GREEN));
                refresh();
            } else {
                // インベントリがいっぱいなら返金
                traderModule.getEconomyModule().deposit(player.getUniqueId(), ti.price);
                player.sendMessage(Component.text("インベントリがいっぱいです。", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("所持金が足りません。", NamedTextColor.RED));
        }
    }

    private void handleSell(ItemStack clicked, PlayerData data) {
        String itemId = clicked.hasItemMeta() ? clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        if (itemId == null) return;

        TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
        if (ti == null) {
            player.sendMessage(Component.text("このアイテムは買い取っていません。", NamedTextColor.RED));
            return;
        }

        double totalReward = ti.price * clicked.getAmount();
        traderModule.getEconomyModule().deposit(player.getUniqueId(), totalReward);
        clicked.setAmount(0);
        player.sendMessage(Component.text(itemId + " を売却し、" + totalReward + "₽ を受け取りました。", NamedTextColor.GREEN));
    }
}
