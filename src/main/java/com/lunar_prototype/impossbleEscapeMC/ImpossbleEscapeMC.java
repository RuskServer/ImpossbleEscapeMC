package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.command.GetItemCommand;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.listener.PlayerListener;
import com.lunar_prototype.impossbleEscapeMC.listener.ResourcePackListener;
import com.lunar_prototype.impossbleEscapeMC.util.CrossbowTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class ImpossbleEscapeMC extends JavaPlugin {

    public static ImpossbleEscapeMC getInstance() {
        return instance;
    }

    private static ImpossbleEscapeMC instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        ItemRegistry.loadAllItems(this);
        getServer().getPluginManager().registerEvents(new GunListener(this), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(),this);
        getCommand("getitem").setExecutor(new GetItemCommand());
        CrossbowTask.start(this);
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
