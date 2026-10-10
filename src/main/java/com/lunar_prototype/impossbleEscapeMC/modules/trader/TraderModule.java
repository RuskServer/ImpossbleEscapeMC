package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;

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
