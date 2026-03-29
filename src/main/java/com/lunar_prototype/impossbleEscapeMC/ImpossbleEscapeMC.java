package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.ai.BrainManager;
import com.lunar_prototype.impossbleEscapeMC.ai.AiRaidLogger;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.command.*;
import com.lunar_prototype.impossbleEscapeMC.core.ModuleBootstrap;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutCommand;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.market.MarketModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUIListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.listener.PlayerListener;
import com.lunar_prototype.impossbleEscapeMC.listener.ResourcePackListener;
import com.lunar_prototype.impossbleEscapeMC.util.CrossbowTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

public final class ImpossbleEscapeMC extends JavaPlugin {

    public static ImpossbleEscapeMC getInstance() {
        return instance;
    }

    private static ImpossbleEscapeMC instance;

    public ScavSpawner getScavSpawner() {
        return scavSpawner;
    }

    private ScavSpawner scavSpawner;

    private GunListener gunListener;
    private ResourcePackListener resourcePackListener;
    private com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager minigameManager;
    private RaidModule raidModule;
    private com.lunar_prototype.impossbleEscapeMC.party.PartyManager partyManager;
    private com.lunar_prototype.impossbleEscapeMC.loot.LootManager lootManager;
    private com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI searchGUI;
    private com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener lootEggListener;
    private com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager corpseManager;
    private com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager raidMapManager;
    private AiRaidLogger aiRaidLogger;

    private ServiceContainer serviceContainer;
    private ModuleBootstrap moduleBootstrap;

    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    public com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager getMinigameManager() {
        return minigameManager;
    }

    public RaidModule getRaidModule() {
        return raidModule;
    }

    public com.lunar_prototype.impossbleEscapeMC.party.PartyManager getPartyManager() {
        return partyManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.LootManager getLootManager() {
        return lootManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI getSearchGUI() {
        return searchGUI;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener getLootEggListener() {
        return lootEggListener;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager getCorpseManager() {
        return corpseManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager getRaidMapManager() {
        return raidMapManager;
    }

    public AiRaidLogger getAiRaidLogger() {
        return aiRaidLogger;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        saveDefaultConfig(); // Config must be saved first

        // 基盤の初期化
        serviceContainer = new ServiceContainer();
        moduleBootstrap = new ModuleBootstrap(this, serviceContainer);

        // ヒートマップ読み込み
        com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager.load(new java.io.File(getDataFolder(), "heatmap.yml"));

        BrainManager.init(this); // 追加
        aiRaidLogger = new AiRaidLogger(this);
        ItemRegistry.loadAllItems(this);
        gunListener = new GunListener(this);
        scavSpawner = new ScavSpawner(this, gunListener);
        resourcePackListener = new ResourcePackListener(this);
        minigameManager = new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager(this);
        raidModule = new RaidModule(this);
        partyManager = new com.lunar_prototype.impossbleEscapeMC.party.PartyManager(this);
        lootManager = new com.lunar_prototype.impossbleEscapeMC.loot.LootManager(this);
        searchGUI = new com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI(this);
        lootEggListener = new com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener(this);
        corpseManager = new com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager(this);
        raidMapManager = new com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager(this);

        // 既存マネージャーをコンテナに登録
        serviceContainer.register(ScavSpawner.class, scavSpawner);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager.class, minigameManager);
        serviceContainer.register(RaidModule.class, raidModule);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.party.PartyManager.class, partyManager);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.LootManager.class, lootManager);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI.class, searchGUI);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener.class, lootEggListener);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager.class, corpseManager);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager.class, raidMapManager);
        serviceContainer.register(AiRaidLogger.class, aiRaidLogger);

        // モジュールの登録
        moduleBootstrap.registerModule(new PlayerDataModule(this));
        moduleBootstrap.registerModule(new EconomyModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule());
        moduleBootstrap.registerModule(raidModule); // すでにインスタンス化されているのでそのまま登録
        
        com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule traderModule = new com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule(this);
        moduleBootstrap.registerModule(traderModule);
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.compatibility.CitizensCompatModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.medical.MedicalModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.stamina.StaminaModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule(this));
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.market.MarketModule(this));
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.scoreboard.ScoreboardModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule(this));

        // モジュールの有効化
        moduleBootstrap.enableModules();

        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.listener.InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(scavSpawner, this);
        getServer().getPluginManager().registerEvents(new AttachmentGUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameListener(minigameManager), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI(raidModule), this);
        getServer().getPluginManager().registerEvents(searchGUI, this);
        getServer().getPluginManager().registerEvents(lootEggListener, this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.map.MapSlotListener(this, raidMapManager), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidItemListener(this), this);

        // PacketEvents リスナーの登録
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(
                new com.lunar_prototype.impossbleEscapeMC.listener.LobbyVisibilityListener(this)
        );

        // ヒートマップの定期クリーンアップ (5秒ごと)
        org.bukkit.Bukkit.getScheduler().runTaskTimer(this, com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager::cleanup, 100, 100);

        getCommand("getitem").setExecutor(new GetItemCommand());
        getCommand("scavspawn").setExecutor(new ScavCommand(this));
        getCommand("attachment").setExecutor(new AttachmentCommand());
        getCommand("itemreload").setExecutor(new ItemReloadCommand(this));
        
        com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderCommand traderCmd = new com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderCommand(traderModule);
        getCommand("trader").setExecutor(traderCmd);
        getCommand("trader").setTabCompleter(traderCmd);
        com.lunar_prototype.impossbleEscapeMC.minigame.MinigameCommand mgCmd = new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameCommand(minigameManager);
        getCommand("mg").setExecutor(mgCmd);
        getCommand("mg").setTabCompleter(mgCmd);

        com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidCommand raidCmd = new com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidCommand(raidModule);
        getCommand("raid").setExecutor(raidCmd);
        getCommand("raid").setTabCompleter(raidCmd);

        com.lunar_prototype.impossbleEscapeMC.party.PartyCommand partyCmd = new com.lunar_prototype.impossbleEscapeMC.party.PartyCommand(partyManager);
        getCommand("party").setExecutor(partyCmd);
        getCommand("party").setTabCompleter(partyCmd);

        com.lunar_prototype.impossbleEscapeMC.loot.LootCommand lootCmd = new com.lunar_prototype.impossbleEscapeMC.loot.LootCommand(this);
        getCommand("loot").setExecutor(lootCmd);
        getCommand("loot").setTabCompleter(lootCmd);

        MarketModule marketModule = serviceContainer.get(MarketModule.class);
        LevelModule levelModule = serviceContainer.get(LevelModule.class);
        getCommand("market").setExecutor(new com.lunar_prototype.impossbleEscapeMC.modules.market.MarketCommand(marketModule, levelModule));

        QuestModule questModule = serviceContainer.get(QuestModule.class);
        QuestCommand questCmd = new QuestCommand(questModule);
        getCommand("quest").setExecutor(questCmd);
        getCommand("quest").setTabCompleter(questCmd);

        // Settings (操作設定) コマンド登録
        com.lunar_prototype.impossbleEscapeMC.modules.core.SettingsCommand settingsCmd = new com.lunar_prototype.impossbleEscapeMC.modules.core.SettingsCommand(serviceContainer.get(PlayerDataModule.class));
        getCommand("settings").setExecutor(settingsCmd);
        getCommand("settings").setTabCompleter(settingsCmd);

        HideoutModule hideoutModule = serviceContainer.get(HideoutModule.class);
        HideoutCommand hideoutCmd = new HideoutCommand(hideoutModule);
        getCommand("hideout").setExecutor(hideoutCmd);
        getCommand("hideout").setTabCompleter(hideoutCmd);

        CrossbowTask.start(this);

        asyncComputeResourcePackHash();
    }

    private void asyncComputeResourcePackHash() {
        String urlStr = getConfig().getString("resource-pack.url");
        if (urlStr == null || urlStr.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                getLogger().info("Computing resource pack SHA-1 hash...");
                URL url = new URI(urlStr).toURL();
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream is = url.openStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                    }
                }

                byte[] hash = digest.digest();
                resourcePackListener.setResourcePackHash(hash);
                getLogger().info("Successfully computed resource pack hash.");
            } catch (Exception e) {
                getLogger().warning("Failed to compute resource pack hash: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        // モジュールの無効化
        if (moduleBootstrap != null) {
            moduleBootstrap.disableModules();
        }

        // ヒートマップ保存
        com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager.save(new java.io.File(getDataFolder(), "heatmap.yml"));

        // Plugin shutdown logic
        if (scavSpawner != null) {
            scavSpawner.cleanup();
        }
        if (minigameManager != null) {
            minigameManager.stopGame();
        }
        if (raidModule != null) {
            raidModule.stopAllRaids();
        }
        if (corpseManager != null) {
            corpseManager.cleanup();
        }
        if (aiRaidLogger != null) {
            aiRaidLogger.forceFlushAll("plugin_disable");
        }
    }
}
