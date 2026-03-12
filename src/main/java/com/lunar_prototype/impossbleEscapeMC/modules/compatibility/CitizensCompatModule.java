package com.lunar_prototype.impossbleEscapeMC.modules.compatibility;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import org.bukkit.Bukkit;

public class CitizensCompatModule implements IModule {
    @Override
    public void onEnable(ServiceContainer container) {
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            TraderModule traderModule = container.get(TraderModule.class);
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
