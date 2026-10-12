package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

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

    /** 本日あと何個買えるか (制限が無ければ Integer.MAX_VALUE) */
    public int remainingToday(PlayerData data, TraderDefinition trader, TraderItem item) {
        if (item.dailyLimit <= 0) return Integer.MAX_VALUE;
        checkAndResetDailyPurchases(data);
        int bought = data.getDailyPurchases().getOrDefault(trader.id + "_" + item.itemId, 0);
        return Math.max(0, item.dailyLimit - bought);
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

        double totalPrice = ti.price * finalQuantity;
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
            var left = player.getInventory().addItem(item);
            int notGiven = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            remainingToGive -= amount - notGiven;
            if (notGiven > 0) break;
        }

        int actualGiven = finalQuantity - remainingToGive;
        if (actualGiven > 0) {
            if (ti.dailyLimit > 0) {
                for (int i = 0; i < actualGiven; i++) data.incrementPurchase(trader.id + "_" + ti.itemId);
                dataModule.saveAsync(player.getUniqueId());
            }
            if (limitAdjusted) {
                messages.add(Component.text("購入制限により、残りの購入可能数である " + actualGiven + " 個を購入しました。", NamedTextColor.YELLOW));
            } else {
                messages.add(Component.text(name + " を " + actualGiven + " 個購入しました。", NamedTextColor.GREEN));
            }
        }
        if (remainingToGive > 0) {
            economyModule.deposit(player.getUniqueId(), remainingToGive * ti.price);
            messages.add(Component.text("インベントリがいっぱいだったため、" + remainingToGive + " 個分の代金を返金しました。", NamedTextColor.RED));
        }
        String notice = actualGiven <= 0 ? "持ち物に空きがありません"
                : "購入: " + name + " x" + actualGiven + (remainingToGive > 0 ? " (一部返金)" : "");
        return new Purchase(actualGiven, messages, notice);
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
