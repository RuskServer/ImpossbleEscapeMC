package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.ai.BrainManager;
import com.lunar_prototype.impossbleEscapeMC.ai.AiRaidLogger;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.command.*;
import com.lunar_prototype.impossbleEscapeMC.core.ModuleBootstrap;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
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
    private com.lunar_prototype.impossbleEscapeMC.loot.LootManager lootManager;
    private com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI searchGUI;
    private com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener lootBackpackOverlayListener;
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
        // コマンドの登録簿 (モジュールが onEnable で自分のコマンドを入れる。Paper のコマンドとして登録するのは install)
        com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry commands = new com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry();
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry.class, commands);

        // ヒートマップ読み込み
        com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager.load(new java.io.File(getDataFolder(), "heatmap.yml"));

        BrainManager.init(this); // 追加
        aiRaidLogger = new AiRaidLogger(this);
        ItemRegistry.loadAllItems(this);
        gunListener = new GunListener(this);
        scavSpawner = new ScavSpawner(this, gunListener);
        com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.init(this);
        // アドレナリン・死亡時の画面演出 (26.3のポストエフェクト)
        com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService.init(this);
        com.lunar_prototype.impossbleEscapeMC.effect.LightHeads.init(this);
        resourcePackListener = new ResourcePackListener(this);
        minigameManager = new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager(this);
        raidModule = new RaidModule(this);
        lootManager = new com.lunar_prototype.impossbleEscapeMC.loot.LootManager(this);
        searchGUI = new com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI(this);
        lootBackpackOverlayListener = new com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener(this);
        lootEggListener = new com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener(this);
        corpseManager = new com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager(this);
        raidMapManager = new com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager(this);

        // 既存マネージャーをコンテナに登録
        serviceContainer.register(ScavSpawner.class, scavSpawner);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager.class, minigameManager);
        serviceContainer.register(RaidModule.class, raidModule);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.LootManager.class, lootManager);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI.class, searchGUI);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener.class, lootBackpackOverlayListener);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.LootEggListener.class, lootEggListener);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.loot.CorpseManager.class, corpseManager);
        serviceContainer.register(com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager.class, raidMapManager);
        serviceContainer.register(AiRaidLogger.class, aiRaidLogger);

        // モジュールの登録
        moduleBootstrap.registerModule(new PlayerDataModule(this));
        moduleBootstrap.registerModule(new EconomyModule());
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule());
        moduleBootstrap.registerModule(raidModule); // すでにインスタンス化されているのでそのまま登録
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule(this));
        
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
        moduleBootstrap.registerModule(new com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule(this));

        // モジュールの有効化
        moduleBootstrap.enableModules();

        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.listener.InventoryListener(this), this);
        // 持ち物の大きさの合計が使えるスロットを超える操作を、入る前に止める
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.listener.CostCapacityListener(), this);
        // 容量のゲージ (クラフトグリッドの左下) と、大きい物の説明の「サイズ N マス」
        com.lunar_prototype.impossbleEscapeMC.item.CapacityGauge.init();
        com.lunar_prototype.impossbleEscapeMC.item.ItemSizeTooltip.init();
        // フラッシュライトの点け消し
        com.lunar_prototype.impossbleEscapeMC.item.Flashlight.init(this);
        // 初めて参加したプレイヤーのスタッシュに初期物資を入れる
        com.lunar_prototype.impossbleEscapeMC.modules.starter.StarterKit.init(this);
        // データパック銃の命中もプラグイン銃と同じBulletHitEventとして通知する (負傷・アドレナリン・SCAVの命中記録)
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.listener.DatapackBulletHitBridge(), this);
        getServer().getPluginManager().registerEvents(scavSpawner, this);
        getServer().getPluginManager().registerEvents(new AttachmentGUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.listener.TooltipStyleMigration(), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.gui.DatapackAttachmentGUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.gui.HelmetAttachmentGUIListener(), this);
        // データパック銃のリロードに、プラグインの弾を使わせる
        com.lunar_prototype.impossbleEscapeMC.listener.DatapackAmmoTracker ammoTracker = new com.lunar_prototype.impossbleEscapeMC.listener.DatapackAmmoTracker();
        getServer().getPluginManager().registerEvents(ammoTracker, this);
        getServer().getScheduler().runTaskTimer(this, ammoTracker, 1L, 1L);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameListener(minigameManager), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI(raidModule), this);
        getServer().getPluginManager().registerEvents(searchGUI, this);
        getServer().getPluginManager().registerEvents(lootBackpackOverlayListener, this);
        getServer().getPluginManager().registerEvents(lootEggListener, this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.map.MapSlotListener(this, raidMapManager), this);
        // 地図を持っている間はオフハンドを空に見せ、地図を大きく表示させる
        new com.lunar_prototype.impossbleEscapeMC.map.MapOffhandHider(this, raidMapManager);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidItemListener(this), this);

        // PacketEvents リスナーの登録
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(
                new com.lunar_prototype.impossbleEscapeMC.listener.LobbyVisibilityListener(this)
        );
        // レイド終盤の EMP を空と雲のシェーダーに伝える (全員の時刻パケットを書き換える)
        com.lunar_prototype.impossbleEscapeMC.effect.EmpSkySignal.init();
        // レイド中のプレイヤーだけ、デバッグ画面 (F3) の座標などを出さない
        com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidDebugInfo.init(this);
        // 足音などの響きを場所ごとに選ぶ音響の地図
        com.lunar_prototype.impossbleEscapeMC.modules.acoustics.AcousticMap.init(this);
        // レイド中の背景音 (場所と時間帯で選ぶ)
        com.lunar_prototype.impossbleEscapeMC.modules.acoustics.AmbienceService.init(this);
        // 落ちているアイテム (寝かせた見た目・左クリックで拾う)
        com.lunar_prototype.impossbleEscapeMC.modules.grounditem.GroundItemService.init(this);

        // ヒートマップの定期クリーンアップ (5秒ごと)
        org.bukkit.Bukkit.getScheduler().runTaskTimer(this, com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager::cleanup, 100, 100);

        // モジュールに属さないコマンド (モジュールのコマンドは各モジュールの onEnable で登録簿に入れる)
        commands.register(new GetItemCommand(), new ScavCommand(this), new AttachmentCommand(), new ItemReloadCommand(this),
                new ShaderDebugCommand(), new com.lunar_prototype.impossbleEscapeMC.modules.acoustics.AcousticsCommand(),
                new DatapackTestCommand(), new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameCommand(minigameManager),
                new com.lunar_prototype.impossbleEscapeMC.loot.LootCommand(this));
        commands.install(this);

        QuestModule questModule = serviceContainer.get(QuestModule.class);
        // 受けているクエストで納品するアイテムを、そのプレイヤーの画面で強調する
        com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestItemHighlighter.init(this, questModule);

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
        // 開いている画面を閉じる。納品・売却・修理の欄に入れていたアイテムは、各画面の閉じる処理が持ち主に返す
        // (リスナーが外れる前に閉じないと、欄の中身が消える)
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            player.closeInventory();
        }

        // モジュールの無効化
        if (moduleBootstrap != null) {
            moduleBootstrap.disableModules();
        }

        // ヒートマップ保存
        com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager.save(new java.io.File(getDataFolder(), "heatmap.yml"));

        // Plugin shutdown logic
        // SCAVのデータパック銃用FakePlayerは、サーバー停止時の退出・保存処理に乗らないよう先に取り除く
        com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.shutdown();
        com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService.shutdown();
        com.lunar_prototype.impossbleEscapeMC.effect.LightHeads.shutdown();
        com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestItemHighlighter.shutdown();
        com.lunar_prototype.impossbleEscapeMC.item.ItemSizeTooltip.shutdown();
        com.lunar_prototype.impossbleEscapeMC.effect.EmpSkySignal.shutdown();
        com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidDebugInfo.shutdown();
        com.lunar_prototype.impossbleEscapeMC.modules.grounditem.GroundItemService.shutdown();
        if (scavSpawner != null) {
            scavSpawner.cleanup();
        }
        if (minigameManager != null) {
            minigameManager.stopGame();
        }
        if (raidModule != null) {
            raidModule.suspendAllRaids();
        }
        if (corpseManager != null) {
            corpseManager.cleanup();
        }
        if (aiRaidLogger != null) {
            aiRaidLogger.forceFlushAll("plugin_disable");
        }
    }
}
