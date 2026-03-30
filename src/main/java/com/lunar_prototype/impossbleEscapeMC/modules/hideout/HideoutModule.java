package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import org.bukkit.Bukkit;

/**
 * 隠れ家システムのメインモジュール
 */
public class HideoutModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final HideoutWorldManager worldManager;
    private final SmartStructureService structureService;
    private PlayerDataModule dataModule;

    public HideoutModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.worldManager = new HideoutWorldManager();
        this.structureService = new SmartStructureService(this);
    }

    public void onEnable(ServiceContainer serviceContainer) {
        this.dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        
        // ワールドの初期化
        worldManager.initWorlds();

        // リスナーの登録
        Bukkit.getPluginManager().registerEvents(new HideoutListener(this), plugin);

        Bukkit.getLogger().info("HideoutModule enabled.");
    }

    public void onDisable() {
        Bukkit.getLogger().info("HideoutModule disabled.");
    }

    public ImpossbleEscapeMC getPlugin() {
        return plugin;
    }

    public HideoutWorldManager getWorldManager() {
        return worldManager;
    }

    public SmartStructureService getStructureService() {
        return structureService;
    }

    public PlayerDataModule getDataModule() {
        return dataModule;
    }
}
