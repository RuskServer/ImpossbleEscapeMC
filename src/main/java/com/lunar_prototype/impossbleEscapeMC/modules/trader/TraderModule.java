package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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

    public void loadTraders() {
        traders.clear();
        File file = new File(plugin.getDataFolder(), "traders.yml");
        if (!file.exists()) {
            plugin.saveResource("traders.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) continue;

            String displayName = section.getString("displayName", id);
            TraderType type = TraderType.valueOf(section.getString("type", "BUY").toUpperCase());
            int npcId = section.getInt("npc_id", -1);
            
            List<TraderItem> items = new ArrayList<>();
            if (section.contains("items")) {
                for (Map<?, ?> map : section.getMapList("items")) {
                    String itemId = (String) map.get("id");
                    double price = ((Number) map.get("price")).doubleValue();
                    int limit = map.containsKey("limit") ? ((Number) map.get("limit")).intValue() : 0;
                    items.add(new TraderItem(itemId, price, limit));
                }
            }
            
            traders.put(id, new TraderDefinition(id, displayName, type, items, npcId));
        }
        plugin.getLogger().info("Loaded " + traders.size() + " traders.");
    }

    public TraderDefinition getTrader(String id) {
        return traders.get(id);
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
