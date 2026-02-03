package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.command.AttachmentCommand;
import com.lunar_prototype.impossbleEscapeMC.command.GetItemCommand;
import com.lunar_prototype.impossbleEscapeMC.command.ScavCommand;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUIListener;
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

    public ScavSpawner getScavSpawner() {
        return scavSpawner;
    }

    private ScavSpawner scavSpawner;

    private GunListener gunListener;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        ItemRegistry.loadAllItems(this);
        gunListener = new GunListener(this);
        scavSpawner = new ScavSpawner(this, gunListener);
        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(scavSpawner, this);
        getServer().getPluginManager().registerEvents(new AttachmentGUIListener(), this);
        getCommand("getitem").setExecutor(new GetItemCommand());
        getCommand("scavspawn").setExecutor(new ScavCommand(this));
        getCommand("attachment").setExecutor(new AttachmentCommand());
        CrossbowTask.start(this);
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (scavSpawner != null) {
            scavSpawner.cleanup();
        }
    }
}
