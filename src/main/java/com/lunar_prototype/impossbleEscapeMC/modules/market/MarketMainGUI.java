package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * マーケットのメイン画面（出品一覧）
 */
public class MarketMainGUI implements Listener {
    private final Player player;
    private final MarketModule marketModule;
    private final Inventory inventory;
    private int page = 0;

    public MarketMainGUI(Player player, MarketModule marketModule) {
        this.player = player;
        this.marketModule = marketModule;
        this.inventory = Bukkit.createInventory(null, 54, Component.text("Global Market").decoration(TextDecoration.ITALIC, false));
    }

    public void open() {
        setupGUI();
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();

        // 背景/枠線
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.empty());
        glass.setItemMeta(glassMeta);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, glass);
        }

        // 出品一覧の取得（新しい順）
        List<MarketListing> allListings = marketModule.getAllListings().stream()
                .sorted(Comparator.comparingLong(MarketListing::getListDate).reversed())
                .collect(Collectors.toList());

        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            int index = start + i;
            if (index >= allListings.size()) break;

            MarketListing listing = allListings.get(index);
            try {
                ItemStack item = SerializationUtil.deserializeItemStack(listing.getItemBase64());
                if (item == null) continue;

                ItemMeta meta = item.getItemMeta();
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();

                lore.add(Component.empty());
                lore.add(Component.text("--------------------", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("出品者: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(listing.getSellerName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
                lore.add(Component.text("価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%.1f", listing.getPrice()) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
                lore.add(Component.empty());
                
                if (listing.getSellerUuid().equals(player.getUniqueId())) {
                    lore.add(Component.text("自分の出品です (クリックで取り消し)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("クリックで購入", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                }

                meta.lore(lore);
                // IDを隠しデータとして持たせる（今回はBase64でシリアライズされたItemStackそのものに入れないため、
                // クリック時のインデックスで判定するか、カスタムPDCに入れる）
                // 面倒なので、ここではアイテムを表示し、クリック時に listing オブジェクトを特定する
                item.setItemMeta(meta);

                inventory.setItem(i, item);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 出品ボタン
        ItemStack sellBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sellBtn.getItemMeta();
        sellMeta.displayName(Component.text("アイテムを出品する", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        sellMeta.lore(List.of(Component.text("手持ちのFIRアイテムをマーケットに出品します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        sellBtn.setItemMeta(sellMeta);
        inventory.setItem(49, sellBtn);

        // ページネーション
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(Component.text("前のページ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(prevMeta);
            inventory.setItem(45, prev);
        }
        if (allListings.size() > (page + 1) * 45) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(Component.text("次のページ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(nextMeta);
            inventory.setItem(53, next);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 49) {
            new MarketSellGUI(player, marketModule).open();
            return;
        }

        if (slot == 45 && page > 0) {
            page--;
            setupGUI();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53) {
            List<MarketListing> allListings = marketModule.getAllListings().stream()
                    .sorted(Comparator.comparingLong(MarketListing::getListDate).reversed())
                    .collect(Collectors.toList());
            if (allListings.size() > (page + 1) * 45) {
                page++;
                setupGUI();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        if (slot >= 0 && slot < 45) {
            List<MarketListing> allListings = marketModule.getAllListings().stream()
                    .sorted(Comparator.comparingLong(MarketListing::getListDate).reversed())
                    .collect(Collectors.toList());
            int index = page * 45 + slot;
            if (index < allListings.size()) {
                MarketListing listing = allListings.get(index);
                handleListingInteraction(listing);
            }
        }
    }

    private void handleListingInteraction(MarketListing listing) {
        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            // 取り消し
            try {
                ItemStack item = SerializationUtil.deserializeItemStack(listing.getItemBase64());
                if (player.getInventory().addItem(item).isEmpty()) {
                    marketModule.removeListing(listing.getId());
                    player.sendMessage(Component.text("出品を取り消しました。", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    setupGUI();
                } else {
                    player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("エラーが発生しました。", NamedTextColor.RED));
                e.printStackTrace();
            }
        } else {
            // 購入
            EconomyModule economy = ImpossbleEscapeMC.getInstance().getServiceContainer().get(EconomyModule.class);
            if (economy.withdraw(player.getUniqueId(), listing.getPrice())) {
                try {
                    ItemStack item = SerializationUtil.deserializeItemStack(listing.getItemBase64());
                    if (player.getInventory().addItem(item).isEmpty()) {
                        marketModule.removeListing(listing.getId());
                        economy.deposit(listing.getSellerUuid(), listing.getPrice());
                        
                        player.sendMessage(Component.text(listing.getPrice() + "₽ でアイテムを購入しました。", NamedTextColor.GREEN));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                        // 出品者に通知（オンラインなら）
                        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                        if (seller != null) {
                            seller.sendMessage(Component.text("マーケットに出品していたアイテムが " + listing.getPrice() + "₽ で売れました！", NamedTextColor.GOLD));
                        }
                        
                        setupGUI();
                    } else {
                        // 返金
                        economy.deposit(player.getUniqueId(), listing.getPrice());
                        player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
                    }
                } catch (Exception e) {
                    economy.deposit(player.getUniqueId(), listing.getPrice());
                    player.sendMessage(Component.text("エラーが発生しました。", NamedTextColor.RED));
                    e.printStackTrace();
                }
            } else {
                player.sendMessage(Component.text("所持金が足りません。", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
