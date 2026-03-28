package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TraderGUI implements Listener {
    private static final int[] SELL_INPUT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SELL_BUTTON_SLOT = 22;
    private static final int REPAIR_INPUT_SLOT = 13;
    private static final int REPAIR_EXECUTE_SLOT = 22;
    private static final int REPAIR_BACK_SLOT = 18;

    private enum GUIMode { SHOP, REPAIR }
    private GUIMode mode = GUIMode.SHOP;

    private final TraderModule traderModule;
    private final TraderDefinition trader;
    private final Player player;
    private Inventory inventory;

    public TraderGUI(TraderModule traderModule, TraderDefinition trader, Player player) {
        this.traderModule = traderModule;
        this.trader = trader;
        this.player = player;
        
        // 初期サイズとモードの設定
        if (trader.type == TraderType.SELL) {
            this.inventory = Bukkit.createInventory(null, 27, Component.text(trader.displayName).decoration(TextDecoration.ITALIC, false));
        }
    }

    public void open() {
        // リスナーの登録 (閉じると解除されるため、開くたびに登録)
        Bukkit.getPluginManager().registerEvents(this, traderModule.getPlugin());

        if (mode == GUIMode.SHOP) {
            if (trader.type == TraderType.BUY) {
                setupBuyGUI();
            } else {
                setupSellGUI();
            }
        } else {
            setupRepairGUI();
        }
        player.openInventory(inventory);
    }

    private void setupBuyGUI() {
        PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
        traderModule.checkAndResetDailyPurchases(data);

        // アイテム数に基づいてサイズを計算 (最小9, 最大54)
        int itemCount = trader.items.size();
        int rows = (int) Math.ceil(itemCount / 9.0);
        int size = Math.min(54, Math.max(9, rows * 9));
        
        // 修理ボタンを表示するために、最低2行は確保 (サイズ 18 以上)
        if (trader.canRepairArmor || trader.canRepairWeapon) {
            size = Math.max(18, size);
        }

        if (this.inventory == null || this.inventory.getSize() != size) {
            this.inventory = Bukkit.createInventory(null, size, Component.text(trader.displayName).decoration(TextDecoration.ITALIC, false));
        }
        inventory.clear();

        int slot = 0;
        for (TraderItem ti : trader.items) {
            if (slot >= size) break;
            ItemStack icon = ItemFactory.create(ti.itemId);
            if (icon == null) continue;
            boolean unlocked = traderModule.isUnlocked(data, ti);
            int requiredLevel = traderModule.getRequiredLevel(ti);

            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();

            lore.add(Component.empty());
            lore.add(Component.text("解放レベル: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Lv." + requiredLevel, unlocked ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.text("価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(ti.price + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
            if (ti.dailyLimit > 0) {
                int bought = data.getDailyPurchases().getOrDefault(trader.id + "_" + ti.itemId, 0);
                int remaining = Math.max(0, ti.dailyLimit - bought);
                lore.add(Component.text("本日の残り制限: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(remaining + "/" + ti.dailyLimit, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
            }
            if (unlocked) {
                lore.add(Component.text("クリックで購入", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Shift+クリックで個数を指定", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("未解放: Lv." + requiredLevel + " で購入可能", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, ti.itemId);
            icon.setItemMeta(meta);

            inventory.setItem(slot++, icon);
        }

        // クエストボタン (右下に配置)
        ItemStack questBtn = new ItemStack(Material.BOOK);
        ItemMeta qm = questBtn.getItemMeta();
        qm.displayName(Component.text("クエスト一覧", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        qm.lore(List.of(Component.text("受領・報告はこちらから", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        questBtn.setItemMeta(qm);
        inventory.setItem(inventory.getSize() - 1, questBtn);

        // 修理ボタン (左下に配置、またはクエストの隣)
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
            inventory.setItem(inventory.getSize() - 2, repairBtn);
        }
    }

    private void setupRepairGUI() {
        if (this.inventory == null || this.inventory.getSize() != 27) {
            this.inventory = Bukkit.createInventory(null, 27, Component.text(trader.displayName + " - 修理").decoration(TextDecoration.ITALIC, false));
        }
        inventory.clear();

        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, bg);
        }

        inventory.setItem(REPAIR_INPUT_SLOT, null); // 投入口

        // 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(Component.text("ショップに戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(bm);
        inventory.setItem(REPAIR_BACK_SLOT, back);

        updateRepairButton();
    }

    private void updateRepairButton() {
        if (mode != GUIMode.REPAIR) return;

        ItemStack target = inventory.getItem(REPAIR_INPUT_SLOT);
        double cost = calculateRepairCost(target);

        ItemStack button = new ItemStack(cost > 0 ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text("修理を実行する", cost > 0 ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        if (target != null && target.getType() != Material.AIR) {
            if (cost > 0) {
                lore.add(Component.text("修理費用: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%.0f", cost) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
                lore.add(Component.empty());
                lore.add(Component.text("クリックで修理を確定します", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                String error = getRepairError(target);
                lore.add(Component.text(error, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("中央のスロットにアイテムを入れてください", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        button.setItemMeta(meta);
        inventory.setItem(REPAIR_EXECUTE_SLOT, button);
    }

    private String getRepairError(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "アイテムが無効です";
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId == null) return "修理不可能なアイテムです";
        
        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null || def.maxDurability <= 0) return "耐久値のないアイテムです";

        int currentDur = meta.getPersistentDataContainer().getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        if (currentDur >= def.maxDurability) return "修理の必要がありません";

        if (def.armorStats != null) {
            if (!trader.canRepairArmor) return "この店ではアーマーを修理できません";
        } else if ("GUN".equalsIgnoreCase(def.type)) {
            if (!trader.canRepairWeapon) return "この店では武器を修理できません";
        } else {
            return "このアイテムは修理できません";
        }

        return "不明なエラー";
    }

    private boolean isRepairable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId == null) return false;
        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null || def.maxDurability <= 0) return false;

        if (def.armorStats != null) {
            return trader.canRepairArmor;
        } else if ("GUN".equalsIgnoreCase(def.type)) {
            return trader.canRepairWeapon;
        }
        return false;
    }

    private double calculateRepairCost(ItemStack item) {
        if (!isRepairable(item)) return 0;
        
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);
        
        int currentDur = meta.getPersistentDataContainer().getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        if (currentDur >= def.maxDurability) return 0;

        double damageRatio = 1.0 - ((double) currentDur / def.maxDurability);
        double basePrice;
        
        if (def.armorStats != null) {
            basePrice = TraderModule.getBaseArmorRepairCost(def.armorStats.armorClass);
        } else {
            basePrice = TraderModule.getBaseWeaponRepairCost(def.rarity);
        }
        
        return basePrice * damageRatio;
    }

    private void handleRepair() {
        ItemStack target = inventory.getItem(REPAIR_INPUT_SLOT);
        double cost = calculateRepairCost(target);

        if (cost <= 0) return;

        if (traderModule.getEconomyModule().withdraw(player.getUniqueId(), cost)) {
            ItemMeta meta = target.getItemMeta();
            String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);

            // 耐久値をリセット
            meta.getPersistentDataContainer().set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            
            // 武器の場合はジャムも解除
            if ("GUN".equalsIgnoreCase(def.type)) {
                meta.getPersistentDataContainer().set(PDCKeys.JAMMED, PDCKeys.BOOLEAN, (byte) 0);
            }

            target.setItemMeta(meta);
            
            // 見た目を更新 (Lore, 耐久値バー)
            ItemFactory.updateLore(target);

            player.sendMessage(Component.text("アイテムを修理し、" + String.format("%.0f", cost) + "₽ を支払いました。", NamedTextColor.GREEN));
            updateRepairButton();
        } else {
            player.sendMessage(Component.text("所持金が足りません。", NamedTextColor.RED));
        }
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
        ItemStack button = new ItemStack(total > 0 ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text("一括売却を実行", total > 0 ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("合計買取価格: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(total + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        if (total > 0) {
            lore.add(Component.text("クリックで売却を確定します", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("中央のスロットに売却したいアイテムを入れてください", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        button.setItemMeta(meta);
        inventory.setItem(SELL_BUTTON_SLOT, button);
    }

    private double calculateTotalSellValue() {
        double total = 0;
        for (int slot : SELL_INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            String itemId = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
            if (itemId == null) continue;

            TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
            if (ti != null) {
                total += ti.price * item.getAmount();
            }
        }
        return total;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;

        if (mode == GUIMode.REPAIR) {
            int slot = event.getRawSlot();
            if (slot < inventory.getSize()) {
                if (slot == REPAIR_BACK_SLOT) {
                    event.setCancelled(true);
                    // ショップに戻る際にアイテムがあれば返却
                    returnRepairItem();
                    mode = GUIMode.SHOP;
                    open();
                } else if (slot == REPAIR_EXECUTE_SLOT) {
                    event.setCancelled(true);
                    handleRepair();
                } else if (slot != REPAIR_INPUT_SLOT) {
                    event.setCancelled(true);
                }
            }
            // クリック後に計算を更新
            Bukkit.getScheduler().runTask(traderModule.getPlugin(), this::updateRepairButton);
            return;
        }

        if (trader.type == TraderType.BUY) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            
            // クエストボタン
            if (slot == inventory.getSize() - 1) {
                player.closeInventory();
                com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule qm = traderModule.getPlugin().getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
                new com.lunar_prototype.impossbleEscapeMC.modules.quest.TraderQuestGUI(player, trader, qm).open();
                return;
            }
            
            // 修理ボタン
            if ((trader.canRepairArmor || trader.canRepairWeapon) && slot == inventory.getSize() - 2) {
                mode = GUIMode.REPAIR;
                open();
                return;
            }

            if (slot < inventory.getSize()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    PlayerData data = traderModule.getDataModule().getPlayerData(player.getUniqueId());
                    
                    if (event.isShiftClick()) {
                        String itemId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                        TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
                        if (ti != null) {
                            if (!traderModule.isUnlocked(data, ti)) {
                                player.sendMessage(Component.text("この取引は Lv." + traderModule.getRequiredLevel(ti) + " で解放されます。", NamedTextColor.RED));
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

    private void returnRepairItem() {
        ItemStack item = inventory.getItem(REPAIR_INPUT_SLOT);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item).values().forEach(remaining -> 
                player.getWorld().dropItemNaturally(player.getLocation(), remaining)
            );
            inventory.setItem(REPAIR_INPUT_SLOT, null);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        
        if (mode == GUIMode.REPAIR) {
            for (int slot : event.getRawSlots()) {
                if (slot < inventory.getSize() && slot != REPAIR_INPUT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(traderModule.getPlugin(), this::updateRepairButton);
            return;
        }

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
        if (mode == GUIMode.REPAIR) {
            returnRepairItem();
        } else if (trader.type == TraderType.SELL) {
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
    }

    public void handleBuy(ItemStack clicked, PlayerData data, int quantity) {
        String itemId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        TraderItem ti = trader.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst().orElse(null);
        if (ti == null) return;
        if (quantity <= 0) return;
        if (!traderModule.isUnlocked(data, ti)) {
            player.sendMessage(Component.text("この取引は Lv." + traderModule.getRequiredLevel(ti) + " で解放されます。", NamedTextColor.RED));
            return;
        }

        traderModule.checkAndResetDailyPurchases(data);
        int finalQuantity = quantity;
        boolean limitAdjusted = false;
        if (ti.dailyLimit > 0) {
            int bought = data.getDailyPurchases().getOrDefault(trader.id + "_" + ti.itemId, 0);
            int allowed = Math.max(0, ti.dailyLimit - bought);
            
            if (allowed <= 0) {
                player.sendMessage(Component.text("本日の購入制限に達しています。", NamedTextColor.RED));
                return;
            }
            
            if (quantity > allowed) {
                finalQuantity = allowed;
                limitAdjusted = true;
            }
        }

        double totalPrice = ti.price * finalQuantity;
        if (traderModule.getEconomyModule().withdraw(player.getUniqueId(), totalPrice)) {
            // アイテムをスタック数に合わせて配布
            ItemStack sample = ItemFactory.create(ti.itemId);
            if (sample == null) {
                // 万が一アイテムが見つからない場合は返金
                traderModule.getEconomyModule().deposit(player.getUniqueId(), totalPrice);
                return;
            }
            
            int maxStack = sample.getType().getMaxStackSize();
            int remainingToGive = finalQuantity;
            boolean inventoryFull = false;

            while (remainingToGive > 0) {
                int amount = Math.min(remainingToGive, maxStack);
                ItemStack item = sample.clone(); // 使い回す
                item.setAmount(amount);
                if (!player.getInventory().addItem(item).isEmpty()) {
                    inventoryFull = true;
                    break;
                }
                remainingToGive -= amount;
            }

            int actualGiven = finalQuantity - remainingToGive;
            if (actualGiven > 0) {
                if (ti.dailyLimit > 0) {
                    for (int i = 0; i < actualGiven; i++) {
                        data.incrementPurchase(trader.id + "_" + ti.itemId);
                    }
                    traderModule.getDataModule().saveAsync(player.getUniqueId());
                }
                
                if (limitAdjusted) {
                    player.sendMessage(Component.text("購入制限により、残りの購入可能数である " + actualGiven + " 個を購入しました。", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text(ti.itemId + " を " + actualGiven + " 個購入しました。", NamedTextColor.GREEN));
                }
                setupBuyGUI();
            }

            // 余った分を返金
            if (remainingToGive > 0) {
                traderModule.getEconomyModule().deposit(player.getUniqueId(), remainingToGive * ti.price);
                player.sendMessage(Component.text("インベントリがいっぱいだったため、" + remainingToGive + " 個分の代金を返金しました。", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("所持金が足りません。", NamedTextColor.RED));
        }
    }

    private void handleBulkSell() {
        double total = calculateTotalSellValue();
        if (total <= 0) {
            player.sendMessage(Component.text("売却可能なアイテムがありません。", NamedTextColor.RED));
            return;
        }

        traderModule.getEconomyModule().deposit(player.getUniqueId(), total);
        
        // アイテムを消去
        for (int slot : SELL_INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            
            String itemId = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
            if (itemId != null && trader.items.stream().anyMatch(i -> i.itemId.equals(itemId))) {
                inventory.setItem(slot, null);
            }
        }

        player.sendMessage(Component.text("アイテムを売却し、" + total + "₽ を受け取りました。", NamedTextColor.GREEN));
        updateSellButton();
    }
}
