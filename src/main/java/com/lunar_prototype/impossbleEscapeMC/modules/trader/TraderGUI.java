package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.item.factory.GameItems;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TraderGUI implements Listener {
    private static final NamespacedKey TRADE_INDEX = new NamespacedKey("iem", "trader_trade_index");
    private static final int[] SELL_INPUT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SELL_BUTTON_SLOT = 22;

    /** 未解放の品物を隠しているプレイヤー (サーバーを再起動するまで覚える) */
    private static final Set<UUID> hideLocked = ConcurrentHashMap.newKeySet();
    /** 購入画面の一番下の段のボタン (段の左端から数えた位置) */
    private static final int TOGGLE_LOCKED_COLUMN = 0;
    private static final int REPAIR_COLUMN = 7;
    private static final int QUEST_COLUMN = 8;
    /** 品物を並べられる数 (一番下の段はボタン) */
    private static final int MAX_ITEM_SLOTS = 45;

    private final TraderModule traderModule;
    private final TraderDefinition trader;
    private final Player player;
    private Inventory inventory;
    /** イベントを受け取る登録をしているか (開き直しても 1 回だけ。2 回登録すると 1 回押して 2 回買う) */
    private boolean listening;

    public TraderGUI(TraderModule traderModule, TraderDefinition trader, Player player) {
        this.traderModule = traderModule;
        this.trader = trader;
        this.player = player;
        
        // 初期サイズの設定
        if (trader.type == TraderType.SELL) {
            this.inventory = Bukkit.createInventory(null, 27, Component.text(trader.displayName).decoration(TextDecoration.ITALIC, false));
        }
    }

    public void open() {
        // リスナーの登録 (閉じると解除される。登録は 1 回だけ)
        if (!listening) {
            Bukkit.getPluginManager().registerEvents(this, traderModule.getPlugin());
            listening = true;
        }

        if (trader.type == TraderType.BUY) {
            setupBuyGUI();
        } else {
            setupSellGUI();
        }
        player.openInventory(inventory);
    }

    /**
     * 購入画面。買える品物を先に並べ、今買えない (未解放の) 品物は段を改めて後ろに、灰色の板にして並べる
     * (隠すこともできる)。一番下の段はボタン専用 (左端: 未解放の表示切り替え、右から2つ目: 修理、右端: クエスト一覧)
     */
    private void setupBuyGUI() {
        PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
        traderModule.checkAndResetDailyPurchases(data);

        List<ItemStack> available = new ArrayList<>();
        List<ItemStack> locked = new ArrayList<>();
        for (TraderItem ti : trader.items) {
            ItemStack icon = GameItems.create(player.getWorld(), ti.itemId, ti.displayName);
            if (icon == null) continue;
            if (traderModule.isUnlocked(data, ti)) {
                available.add(availableIcon(icon, ti, data));
            } else {
                locked.add(lockedIcon(icon, ti, data));
            }
        }
        boolean showLocked = !hideLocked.contains(player.getUniqueId()) && !locked.isEmpty();

        // 品物の段 (買える物の段 + 未解放の段) と、ボタンの段
        int availableRows = (available.size() + 8) / 9;
        int lockedRows = showLocked ? (locked.size() + 8) / 9 : 0;
        int itemRows = Math.max(1, Math.min(MAX_ITEM_SLOTS / 9, availableRows + lockedRows));
        int size = (itemRows + 1) * 9;
        if (this.inventory == null || this.inventory.getSize() != size) {
            this.inventory = Bukkit.createInventory(null, size, Component.text(trader.displayName).decoration(TextDecoration.ITALIC, false));
        }
        inventory.clear();

        int slot = 0;
        for (ItemStack icon : available) {
            if (slot >= itemRows * 9) break;
            inventory.setItem(slot++, icon);
        }
        if (showLocked) {
            slot = availableRows * 9;
            for (ItemStack icon : locked) {
                if (slot >= itemRows * 9) break;
                inventory.setItem(slot++, icon);
            }
        }

        int buttonRow = itemRows * 9;
        inventory.setItem(buttonRow + TOGGLE_LOCKED_COLUMN, toggleLockedButton(locked.size()));

        // クエストボタン (右下)
        ItemStack questBtn = new ItemStack(Material.BOOK);
        ItemMeta qm = questBtn.getItemMeta();
        qm.displayName(Component.text("クエスト一覧", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        qm.lore(List.of(Component.text("受領・報告はこちらから", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        questBtn.setItemMeta(qm);
        inventory.setItem(buttonRow + QUEST_COLUMN, questBtn);

        // 修理ボタン (クエストの隣)
        if (trader.canRepairArmor || trader.canRepairWeapon) {
            ItemStack repairBtn = new ItemStack(Material.ANVIL);
            ItemMeta rm = repairBtn.getItemMeta();
            rm.displayName(Component.text("装備修理", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

            List<Component> rl = new ArrayList<>();
            if (trader.canRepairArmor && trader.canRepairWeapon) {
                rl.add(Component.text("アーマーと武器を修理します", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else if (trader.canRepairArmor) {
                rl.add(Component.text("アーマーを専門に修理します", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                rl.add(Component.text("武器を専門に修理します", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            rl.add(Component.text("クリックで修理メニューへ", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            rm.lore(rl);
            repairBtn.setItemMeta(rm);
            inventory.setItem(buttonRow + REPAIR_COLUMN, repairBtn);
        }
    }

    /** 買える品物: 品物そのものの見た目に、価格・本日の残り・操作を足す */
    private ItemStack availableIcon(ItemStack icon, TraderItem ti, PlayerData data) {
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        if (ti.isBarter()) {
            // 交換の取引: 渡す品物 (と代金があれば代金)
            StringBuilder cost = new StringBuilder();
            for (var c : ti.barter) cost.append(cost.length() > 0 ? "、" : "").append(TraderDialog.itemName(c.itemId())).append(" ×").append(c.amount());
            long price = (long) TraderModule.buyPrice(player.getUniqueId(), trader, ti);
            if (price > 0) cost.append("、").append(price).append("₽");
            lore.add(Component.text("交換: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(cost.toString(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        } else {
            lore.add(Component.text("価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text((long) TraderModule.buyPrice(player.getUniqueId(), trader, ti) + "₽", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)));
        }
        boolean soldOut = false;
        int limit = TraderModule.dailyLimit(player.getUniqueId(), ti);
        if (limit > 0) {
            int bought = data.getDailyPurchases().getOrDefault(TraderModule.limitKey(trader, ti), 0);
            int remaining = Math.max(0, limit - bought);
            soldOut = remaining == 0;
            lore.add(Component.text("本日の残り制限: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(remaining + "/" + limit, soldOut ? NamedTextColor.RED : NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));
        }
        if (soldOut) {
            lore.add(Component.text("本日は売り切れ", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("クリックで購入", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Shift+クリックで個数を指定", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, ti.itemId);
        meta.getPersistentDataContainer().set(TRADE_INDEX, PersistentDataType.INTEGER, trader.items.indexOf(ti));
        icon.setItemMeta(meta);
        return icon;
    }

    /** 未解放の品物: 灰色の板に名前と解放条件 (どこまで満たしたか) */
    private ItemStack lockedIcon(ItemStack sample, TraderItem ti, PlayerData data) {
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sample.effectiveName());
        ItemStack icon = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text("[未解放] " + name, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        int requiredLevel = traderModule.getRequiredLevel(ti);
        lore.add(Component.text("解放レベル: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Lv." + requiredLevel, data.getLevel() >= requiredLevel ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)));
        if (ti.requiredQuestId != null) {
            lore.add(Component.text(traderModule.getUnlockRequirements(ti),
                    data.isQuestCompleted(ti.requiredQuestId) ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        if (ti.isBarter()) {
            StringBuilder cost = new StringBuilder();
            for (var c : ti.barter) cost.append(cost.length() > 0 ? "、" : "").append(TraderDialog.itemName(c.itemId())).append(" ×").append(c.amount());
            lore.add(Component.text("交換: " + cost, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("価格: " + (long) TraderModule.buyPrice(player.getUniqueId(), trader, ti) + "₽", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, ti.itemId);
        meta.getPersistentDataContainer().set(TRADE_INDEX, PersistentDataType.INTEGER, trader.items.indexOf(ti));
        icon.setItemMeta(meta);
        return icon;
    }

    /** 未解放の品物を表示する・隠すの切り替え */
    private ItemStack toggleLockedButton(int lockedCount) {
        boolean hidden = hideLocked.contains(player.getUniqueId());
        ItemStack button = new ItemStack(hidden ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text(hidden ? "未解放の品物を表示する" : "未解放の品物を隠す", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("未解放の品物: " + lockedCount + " 件", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        button.setItemMeta(meta);
        return button;
    }

    private void setupSellGUI() {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, bg);
        }

        // 入力スロットを空にする
        for (int slot : SELL_INPUT_SLOTS) {
            inventory.setItem(slot, null);
        }

        updateSellButton();
    }

    private void updateSellButton() {
        double total = calculateTotalSellValue();
        int supplied = countSupplied();
        boolean any = total > 0 || supplied > 0;
        ItemStack button = new ItemStack(any ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text("一括売却を実行", any ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("合計買取価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(total + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        if (supplied > 0) {
            lore.add(Component.text("支給品 " + supplied + " 点は 0₽ で処分します", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (hasFilledBag()) {
            lore.add(Component.text("中に物の入ったバッグは売れません (中身を出してください)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (any) {
            lore.add(Component.text("クリックで売却を確定します", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("中央のスロットに売却したいアイテムを入れてください", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        button.setItemMeta(meta);
        inventory.setItem(SELL_BUTTON_SLOT, button);
    }

    /**
     * 売る物の品物ID。プラグインのアイテム・弾はアイテムのID、データパックの銃とアタッチメントはデータパックのID
     * (データパックの物にはプラグインのIDが書かれていないため)
     */
    private static String sellableIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        // 中に物の入ったバッグは売らない (売ると中身ごと消える)。欄に残し、閉じた時に返す
        if (hasContents(item)) return null;
        // 支給品は値段が付かない (0₽ で処分する。countSupplied)
        if (com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout.isSupplied(item)) return null;
        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId != null) return itemId;
        String gunId = com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog.gunIdOf(item);
        if (gunId != null) return gunId;
        return com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackAttachments.attachmentIdOf(item);
    }

    private double calculateTotalSellValue() {
        double total = 0;
        for (int slot : SELL_INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            String itemId = sellableIdOf(item);
            if (itemId == null) continue;

            TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
            if (ti != null) {
                double price = TraderModule.conditionAdjustedSellPrice(ti, item);
                total += price * item.getAmount();
            }
        }
        return Math.floor(total * TraderModule.sellMultiplier(player.getUniqueId()));   // ブローカーの買取はスキルで上がる
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;

        if (trader.type == TraderType.BUY) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            
            int buttonRow = inventory.getSize() - 9;
            // 未解放の品物の表示切り替え
            if (slot == buttonRow + TOGGLE_LOCKED_COLUMN) {
                if (!hideLocked.remove(player.getUniqueId())) hideLocked.add(player.getUniqueId());
                Inventory before = inventory;
                setupBuyGUI();
                // 大きさが変わった時だけ開き直す (同じ画面ならその場で並べ替わっている)
                if (inventory != before) player.openInventory(inventory);
                return;
            }

            // クエストボタン
            if (slot == buttonRow + QUEST_COLUMN) {
                com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDialog.openTrader(player, trader);
                return;
            }
            
            // 修理ボタン: 修理の画面 (ダイアログ)。クリックの中では画面を閉じないので次の tick に
            if ((trader.canRepairArmor || trader.canRepairWeapon) && slot == buttonRow + REPAIR_COLUMN) {
                Bukkit.getScheduler().runTask(traderModule.getPlugin(), () -> RepairDialog.open(player, trader));
                return;
            }

            if (slot < buttonRow) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
                    
                    if (event.isShiftClick()) {
                        TraderItem ti = tradeOf(clicked);
                        if (ti != null) {
                            if (!traderModule.isUnlocked(data, ti)) {
                                player.sendMessage(Component.text("この取引の解放条件: " + traderModule.getUnlockRequirements(ti), NamedTextColor.RED));
                                return;
                            }
                            new TraderQuantityGUI(traderModule, trader, ti, player, this).open();
                        }
                    } else {
                        handleBuy(clicked, data, 1);
                    }
                }
            }
        } else {
            // 売却GUI
            int slot = event.getRawSlot();
            if (slot < inventory.getSize()) {
                // 上段インベントリ内の操作制限
                boolean isInputSlot = Arrays.stream(SELL_INPUT_SLOTS).anyMatch(s -> s == slot);
                if (slot == SELL_BUTTON_SLOT) {
                    event.setCancelled(true);
                    handleBulkSell();
                } else if (!isInputSlot) {
                    event.setCancelled(true);
                }
            }
            // クリック後に計算を更新
            Bukkit.getScheduler().runTask(traderModule.getPlugin(), this::updateSellButton);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        
        if (trader.type == TraderType.SELL) {
            for (int slot : event.getRawSlots()) {
                if (slot < inventory.getSize()) {
                    boolean isInputSlot = Arrays.stream(SELL_INPUT_SLOTS).anyMatch(s -> s == slot);
                    if (!isInputSlot) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            Bukkit.getScheduler().runTask(traderModule.getPlugin(), this::updateSellButton);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        
        // 投入エリアに残ったアイテムを返却
        if (trader.type == TraderType.SELL) {
            for (int slot : SELL_INPUT_SLOTS) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item).values().forEach(remaining -> 
                        player.getWorld().dropItemNaturally(player.getLocation(), remaining)
                    );
                    inventory.setItem(slot, null);
                }
            }
        }
        
        HandlerList.unregisterAll(this); // リスナー解除
        listening = false;
    }

    public void handleBuy(ItemStack clicked, PlayerData data, int quantity) {
        handleBuy(tradeOf(clicked), data, quantity);
    }

    /** 数量指定の画面からも、選択した交換・現金販売の取引をそのまま購入する。 */
    public void handleBuy(TraderItem ti, PlayerData data, int quantity) {
        if (ti == null) return;
        TraderModule.Purchase result = traderModule.purchase(player, trader, ti, quantity);
        result.messages().forEach(player::sendMessage);
        if (result.bought() > 0) setupBuyGUI();
    }

    private TraderItem tradeOf(ItemStack icon) {
        if (icon == null || !icon.hasItemMeta()) return null;
        Integer index = icon.getItemMeta().getPersistentDataContainer().get(TRADE_INDEX, PersistentDataType.INTEGER);
        return index != null && index >= 0 && index < trader.items.size() ? trader.items.get(index) : null;
    }

    /** 売る欄の支給品の数 (どのトレーダーでも 0₽ で引き取る。中に物の入ったバッグは除く) */
    private int countSupplied() {
        int count = 0;
        for (int slot : SELL_INPUT_SLOTS) {
            if (isDisposable(inventory.getItem(slot))) count++;
        }
        return count;
    }

    private static boolean isDisposable(ItemStack item) {
        return com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout.isSupplied(item) && !hasContents(item);
    }

    /** 中に物の入ったバックパックか */
    private static boolean hasContents(ItemStack item) {
        var backpacks = com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.getInstance().getServiceContainer()
                .get(com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule.class);
        return backpacks != null && backpacks.isBackpackItem(item) && !backpacks.loadBackpackInventory(item).isEmpty();
    }

    /** 売る欄に、中に物の入ったバッグがあるか */
    private boolean hasFilledBag() {
        for (int slot : SELL_INPUT_SLOTS) {
            if (hasContents(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private void handleBulkSell() {
        double total = calculateTotalSellValue();
        int supplied = countSupplied();
        if (total <= 0 && supplied == 0) {
            player.sendMessage(Component.text("売却可能なアイテムがありません。", NamedTextColor.RED));
            return;
        }

        if (total > 0) traderModule.getEconomyModule().deposit(player.getUniqueId(), total);
        
        // アイテムを消去 (支給品は 0₽ で処分)
        for (int slot : SELL_INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (isDisposable(item)) {
                inventory.setItem(slot, null);
                continue;
            }
            
            String itemId = sellableIdOf(item);
            if (itemId != null && trader.items.stream().anyMatch(i -> i.itemId.equals(itemId))) {
                inventory.setItem(slot, null);
            }
        }

        if (total > 0) {
            player.sendMessage(Component.text("アイテムを売却し、" + total + "₽ を受け取りました。", NamedTextColor.GREEN));
        }
        if (supplied > 0) {
            player.sendMessage(Component.text("支給品 " + supplied + " 点を 0₽ で処分しました。", NamedTextColor.GRAY));
        }
        updateSellButton();
    }
}
