package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.factory.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class TraderModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final Map<String, TraderDefinition> traders = new HashMap<>();
    
    private PlayerDataModule dataModule;
    private EconomyModule economyModule;

    public TraderModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable(ServiceContainer container) {
        this.dataModule = container.get(PlayerDataModule.class);
        this.economyModule = container.get(EconomyModule.class);
        container.get(com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry.class).register(new TraderCommand(this));

        loadTraders();
        // 買うトレーダーの画面 (ダイアログ) の操作を受ける
        TraderDialog.init(this);

        // 登録
        container.register(TraderModule.class, this);
    }

    @Override
    public void onDisable() {
        traders.clear();
    }

    /** トレーダー定義 ({@link TraderCatalog}) を読み込み、品物のIDを確かめる */
    public void loadTraders() {
        traders.clear();
        for (TraderDefinition trader : TraderCatalog.create()) {
            if (traders.putIfAbsent(trader.id, trader) != null) {
                plugin.getLogger().severe("トレーダーのIDが重複しています (後の定義を無視): " + trader.id);
                continue;
            }

        }
        File legacy = new File(plugin.getDataFolder(), "traders.yml");
        if (legacy.exists()) {
            plugin.getLogger().warning("traders.yml は読み込まれません。トレーダーは TraderCatalog (Java) で定義します");
        }
        plugin.getLogger().info("Loaded " + traders.size() + " traders.");
        org.bukkit.Bukkit.getScheduler().runTask(plugin, this::validateItems);
    }

    /** Check after datapack load functions, also called on resource reload. */
    public void validateItems() {
        var quests = plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
        for (TraderDefinition trader : traders.values()) {
            for (TraderItem item : trader.items) {
                if (!GameItems.exists(item.itemId)) plugin.getLogger().warning("トレーダー " + trader.id
                        + ": 品物 " + item.itemId + " が登録されていません");
                if (item.requiredQuestId != null && quests != null && quests.getQuest(item.requiredQuestId) == null)
                    plugin.getLogger().warning("トレーダー " + trader.id + ": 解放クエスト " + item.requiredQuestId + " がありません");
                if (!item.isBarter()) continue;
                if (trader.type != TraderType.BUY) plugin.getLogger().warning("トレーダー " + trader.id
                        + ": 交換の取引 " + item.itemId + " は買うトレーダーにしか置けません");
                for (HideoutFacility.ItemCost cost : item.barter) {
                    if (!GameItems.exists(cost.itemId())) plugin.getLogger().warning("トレーダー " + trader.id
                            + ": 交換の品物 " + cost.itemId() + " (" + item.itemId + ") が登録されていません");
                }
            }
        }
    }

    /**
     * アーマークラスごとの100%修理基準価格
     */
    public static double getBaseArmorRepairCost(int armorClass) {
        return switch (armorClass) {
            case 1 -> 15000.0;
            case 2 -> 35000.0;
            case 3 -> 65000.0;
            case 4 -> 100000.0; // 基準
            case 5 -> 220000.0;
            case 6 -> 480000.0;
            default -> 10000.0;
        };
    }

    /**
     * 武器のレア度ごとの100%修理基準価格
     */
    public static double getBaseWeaponRepairCost(int rarity) {
        return switch (rarity) {
            case 1 -> 20000.0;
            case 2 -> 45000.0;
            case 3 -> 75000.0;
            case 4 -> 130000.0;
            case 5 -> 300000.0;
            default -> 15000.0;
        };
    }

    public TraderDefinition getTrader(String id) {
        return traders.get(id);
    }

    public boolean isUnlocked(PlayerData data, TraderItem item) {
        if (data.getLevel() < getRequiredLevel(item)) return false;
        if (item.requiredQuestId != null && !data.isQuestCompleted(item.requiredQuestId)) return false;
        return true;
    }

    public int getRequiredLevel(TraderItem item) {
        return Math.max(1, item.requiredLevel);
    }

    public String getUnlockRequirements(TraderItem item) {
        String requirements = "Lv." + getRequiredLevel(item);
        if (item.requiredQuestId != null) {
            var quests = plugin == null ? null : plugin.getServiceContainer().get(
                    com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
            var quest = quests == null ? null : quests.getQuest(item.requiredQuestId);
            requirements += " ＋ クエスト「" + (quest == null ? item.requiredQuestId : quest.getDisplayName()) + "」完了";
        }
        return requirements;
    }

    public TraderDefinition getTraderByNpcId(int npcId) {
        if (npcId == -1) return null;
        return traders.values().stream()
                .filter(t -> t.npcId == npcId)
                .findFirst()
                .orElse(null);
    }

    public Collection<TraderDefinition> getAllTraders() {
        return traders.values();
    }

    /**
     * 1日の購入制限のリセットが必要か確認し、必要ならリセットする
     */
    public void checkAndResetDailyPurchases(PlayerData data) {
        long now = System.currentTimeMillis();
        Calendar lastReset = Calendar.getInstance();
        lastReset.setTimeInMillis(data.getLastResetTimestamp());
        
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(now);
        
        if (lastReset.get(Calendar.DAY_OF_YEAR) != current.get(Calendar.DAY_OF_YEAR) ||
            lastReset.get(Calendar.YEAR) != current.get(Calendar.YEAR)) {
            data.clearDailyPurchases();
        }
    }

    /** 本日あと何回取引できるか (制限が無ければ Integer.MAX_VALUE) */
    public int remainingToday(PlayerData data, TraderDefinition trader, TraderItem item) {
        int limit = dailyLimit(data.getUuid(), item);
        if (limit <= 0) return Integer.MAX_VALUE;
        checkAndResetDailyPurchases(data);
        int bought = data.getDailyPurchases().getOrDefault(limitKey(trader, item), 0);
        return Math.max(0, limit - bought);
    }

    /**
     * 本日の取引の数を数える鍵。ルーブルの取引は「トレーダー_品物」(前からの記録のまま)、
     * 交換の取引は渡す品物も付ける (同じ品物のルーブルの取引・別の交換とは別に数える)
     */
    public static String limitKey(TraderDefinition trader, TraderItem item) {
        String key = trader.id + "_" + item.itemId;
        if (!item.isBarter()) return key;
        StringBuilder out = new StringBuilder(key).append("_barter");
        for (HideoutFacility.ItemCost cost : item.barter) out.append('_').append(cost.itemId()).append(cost.amount());
        return out.toString();
    }

    /** 持っている数 (have) で、交換が何回できるか (同じ品物が 2 回書いてあっても合わせて数える) */
    public static int affordableTrades(List<HideoutFacility.ItemCost> costs, Map<String, Integer> have) {
        Map<String, Integer> perTrade = new HashMap<>();
        for (HideoutFacility.ItemCost cost : costs) perTrade.merge(cost.itemId(), cost.amount(), Integer::sum);
        int trades = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> cost : perTrade.entrySet()) {
            if (cost.getValue() <= 0) continue;
            trades = Math.min(trades, have.getOrDefault(cost.getKey(), 0) / cost.getValue());
        }
        return trades;
    }

    /** 交換の取引 quantity 回の分の品物 */
    public static List<HideoutFacility.ItemCost> barterCosts(TraderItem item, int quantity) {
        List<HideoutFacility.ItemCost> out = new ArrayList<>();
        for (HideoutFacility.ItemCost cost : item.barter) out.add(new HideoutFacility.ItemCost(cost.itemId(), cost.amount() * quantity));
        return out;
    }

    /** 交換に使う品物を、持ち物とスタッシュで数える (隠れ家の素材と同じ数え方) */
    public Map<String, Integer> countBarter(Player player, PlayerData data, List<HideoutFacility.ItemCost> costs) {
        HideoutModule hideout = plugin.getServiceContainer().get(HideoutModule.class);
        return hideout == null ? Map.of() : hideout.countMaterials(player, data, costs);
    }

    /** そのプレイヤーの 1 日の上限 (0 以下なら制限なし。スキルで増える、端数は切り上げ) */
    public static int dailyLimit(UUID player, TraderItem item) {
        if (item.dailyLimit <= 0) return item.dailyLimit;
        return (int) Math.ceil(item.dailyLimit * SkillModule.multiplier(player, SkillStat.DAILY_LIMIT) - 1e-9);
    }

    /** そのプレイヤーの買値 (スキルで安くなる。1₽ 単位に丸める) */
    public static double buyPrice(UUID player, TraderDefinition trader, TraderItem item) {
        SkillStat own = switch (trader.id) {
            case "kovacs" -> SkillStat.BUY_KOVACS;
            case "pharmakon" -> SkillStat.BUY_PHARMAKON;
            case "bastion" -> SkillStat.BUY_BASTION;
            default -> null;
        };
        double discount = SkillModule.value(player, SkillStat.BUY_ALL) + (own != null ? SkillModule.value(player, own) : 0);
        return Math.round(item.price * Math.max(0, 1 + discount));
    }

    /** ブローカーの買取の倍率 (スキルで上がる) */
    public static double sellMultiplier(UUID player) {
        return SkillModule.multiplier(player, SkillStat.SELL_BROKER);
    }

    /** Broker の 1 個あたり買取額。売却画面とレイド結果で同じ状態補正を使う。 */
    public static double conditionAdjustedSellPrice(TraderItem offer, ItemStack item) {
        if (offer == null || item == null) return 0;
        double price = offer.price;
        if (com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog.gunIdOf(item) != null) {
            double condition = com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackGunCondition.durability(item)
                    / (double) com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackGunCondition.MAX_DURABILITY;
            price *= 0.2 + 0.8 * condition;
        }
        return price;
    }

    /** 修理代の倍率 (スキルで下がる) */
    public static double repairMultiplier(UUID player) {
        return Math.max(0, SkillModule.multiplier(player, SkillStat.REPAIR_COST));
    }

    /**
     * 購入の結果。bought は買えた数、messages はプレイヤーに伝える文 (チャット)、notice は短い一言 (ダイアログのタイトル用)
     */
    public record Purchase(int bought, List<Component> messages, String notice) {
    }

    /**
     * 品物を買う (チェストの画面・ダイアログの画面の両方から)。解放条件・本日の残り・所持金を確かめ、
     * 持ち物に入りきらなかった分は代金を返す
     */
    public Purchase purchase(Player player, TraderDefinition trader, TraderItem ti, int quantity) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        List<Component> messages = new ArrayList<>();
        if (data == null || quantity <= 0) return new Purchase(0, messages, null);
        if (!isUnlocked(data, ti)) {
            messages.add(Component.text("この取引の解放条件: " + getUnlockRequirements(ti), NamedTextColor.RED));
            return new Purchase(0, messages, "未解放です");
        }
        int allowed = remainingToday(data, trader, ti);
        if (allowed <= 0) {
            messages.add(Component.text("本日の購入制限に達しています。", NamedTextColor.RED));
            return new Purchase(0, messages, "本日は売り切れです");
        }
        int finalQuantity = Math.min(quantity, allowed);
        boolean limitAdjusted = finalQuantity < quantity;
        if (ti.isBarter()) return barter(player, data, trader, ti, finalQuantity, limitAdjusted, messages);

        double unitPrice = buyPrice(player.getUniqueId(), trader, ti);
        double totalPrice = unitPrice * finalQuantity;
        if (!economyModule.withdraw(player.getUniqueId(), totalPrice)) {
            messages.add(Component.text("所持金が足りません。", NamedTextColor.RED));
            return new Purchase(0, messages, "所持金が足りません");
        }
        ItemStack sample = GameItems.create(player.getWorld(), ti.itemId, ti.displayName);
        if (sample == null) {
            economyModule.deposit(player.getUniqueId(), totalPrice);
            return new Purchase(0, messages, null);
        }
        String name = PlainTextComponentSerializer.plainText().serialize(sample.effectiveName());

        int maxStack = sample.getMaxStackSize();
        int remainingToGive = finalQuantity;
        while (remainingToGive > 0) {
            ItemStack item = sample.clone();
            item.setAmount(Math.min(remainingToGive, maxStack));
            int amount = item.getAmount();
            var left = com.lunar_prototype.impossbleEscapeMC.modules.inventory.CostSlotManager.addItem(player, item);
            int notGiven = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            remainingToGive -= amount - notGiven;
            if (notGiven > 0) break;
        }

        int actualGiven = finalQuantity - remainingToGive;
        if (actualGiven > 0) {
            if (ti.dailyLimit > 0) {
                for (int i = 0; i < actualGiven; i++) data.incrementPurchase(limitKey(trader, ti));
                dataModule.saveAsync(player.getUniqueId());
            }
            if (limitAdjusted) {
                messages.add(Component.text("購入制限により、残りの購入可能数である " + actualGiven + " 個を購入しました。", NamedTextColor.YELLOW));
            } else {
                messages.add(Component.text(name + " を " + actualGiven + " 個購入しました。", NamedTextColor.GREEN));
            }
        }
        if (remainingToGive > 0) {
            economyModule.deposit(player.getUniqueId(), remainingToGive * unitPrice);
            messages.add(Component.text("インベントリがいっぱいだったため、" + remainingToGive + " 個分の代金を返金しました。", NamedTextColor.RED));
        }
        String notice = actualGiven <= 0 ? "持ち物に空きがありません"
                : "購入: " + name + " x" + actualGiven + (remainingToGive > 0 ? " (一部返金)" : "");
        return new Purchase(actualGiven, messages, notice);
    }

    /**
     * 交換の取引を quantity 回する。渡す品物 (と代金があれば代金) を確かめてから取り、もらう物は持ち物 → スタッシュへ渡す
     * (全体が入りきらなければ元の素材・代金を戻して交換を中止する)
     */
    private Purchase barter(Player player, PlayerData data, TraderDefinition trader, TraderItem ti, int quantity,
                            boolean limitAdjusted, List<Component> messages) {
        HideoutModule hideout = plugin.getServiceContainer().get(HideoutModule.class);
        ItemStack sample = GameItems.create(player.getWorld(), ti.itemId, ti.displayName);
        if (hideout == null || sample == null) return new Purchase(0, messages, null);
        String name = PlainTextComponentSerializer.plainText().serialize(sample.effectiveName());

        int affordable = affordableTrades(ti.barter, countBarter(player, data, ti.barter));
        double unitPrice = buyPrice(player.getUniqueId(), trader, ti);
        if (unitPrice > 0) {
            affordable = Math.min(affordable, (int) Math.floor(economyModule.getBalance(player.getUniqueId()) / unitPrice));
        }
        if (affordable <= 0) {
            messages.add(Component.text("交換に使う品物" + (unitPrice > 0 ? "か所持金" : "") + "が足りません。", NamedTextColor.RED));
            return new Purchase(0, messages, "交換の品物が足りません");
        }
        int trades = Math.min(quantity, affordable);
        double total = unitPrice * trades;
        if (total > 0 && !economyModule.withdraw(player.getUniqueId(), total)) {
            messages.add(Component.text("所持金が足りません。", NamedTextColor.RED));
            return new Purchase(0, messages, "所持金が足りません");
        }
        var materialSnapshot = com.lunar_prototype.impossbleEscapeMC.modules.inventory.PlayerStorageSnapshot.capture(player,data);
        try {
            if (!hideout.takeMaterials(player, data, barterCosts(ti, trades))) {
                if (total > 0) economyModule.deposit(player.getUniqueId(), total);
                messages.add(Component.text("交換に使う品物が足りません。", NamedTextColor.RED));
                return new Purchase(0, messages, "交換の品物が足りません");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "交換の品物を取れませんでした", e);
            if (total > 0) economyModule.deposit(player.getUniqueId(), total);
            return new Purchase(0, messages, "交換できませんでした");
        }

        int given = trades * Math.max(1, ti.count);
        List<ItemStack> items = new ArrayList<>();
        int maxStack = Math.max(1, sample.getMaxStackSize());
        for (int left = given; left > 0; left -= maxStack) {
            ItemStack item = sample.clone();
            item.setAmount(Math.min(left, maxStack));
            items.add(item);
        }
        if (!hideout.deliver(player, data, items)) {
            materialSnapshot.restore(player,data);
            if (total > 0) economyModule.deposit(player.getUniqueId(),total);
            dataModule.saveAsync(player.getUniqueId());
            messages.add(Component.text("受け取り先に空きがないため、交換せず素材と代金を戻しました。", NamedTextColor.RED));
            return new Purchase(0,messages,"持ち物・スタッシュに空きがありません");
        }
        if (ti.dailyLimit > 0) {
            for (int i = 0; i < trades; i++) data.incrementPurchase(limitKey(trader, ti));
        }
        dataModule.saveAsync(player.getUniqueId());

        if (limitAdjusted || trades < quantity) {
            messages.add(Component.text((limitAdjusted ? "購入制限" : "品物の数") + "により、" + trades + " 回だけ交換しました。", NamedTextColor.YELLOW));
        }
        messages.add(Component.text(name + " ×" + given + " と交換しました (持ち物に入らない分はスタッシュへ)。", NamedTextColor.GREEN));
        return new Purchase(trades, messages, "交換: " + name + " x" + given);
    }

    public PlayerDataModule getDataModule() {
        return dataModule;
    }

    public EconomyModule getEconomyModule() {
        return economyModule;
    }

    public ImpossbleEscapeMC getPlugin() {
        return plugin;
    }
}
