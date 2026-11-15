package com.lunar_prototype.impossbleEscapeMC.modules.compatibility;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import org.bukkit.Bukkit;

@Service
public class CitizensCompatModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final TraderModule traders;

    @Inject
    public CitizensCompatModule(ImpossbleEscapeMC plugin,
            TraderModule traders) {
        this.plugin = plugin;
        this.traders = traders;
    }

    @Override
    public void onEnable(ServiceContainer container) {
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            TraderModule traderModule = traders;
            if (traderModule != null) {
                Bukkit.getPluginManager().registerEvents(new CitizensListener(traderModule), traderModule.getPlugin());
                traderModule.getPlugin().getLogger().info("[Trader] Citizens integration initialized.");
            } else {
                Bukkit.getLogger().warning("[Trader] Citizens found, but TraderModule not registered in ServiceContainer!");
            }
        } else {
            Bukkit.getLogger().info("[Trader] Citizens plugin not found. NPC integration skipped.");
        }
    }

    @Override
    public void onDisable() {
    }
}
