package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;

public class BrainManager {

    public static void init(ImpossbleEscapeMC plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        Bukkit.getLogger().info("[AI-Manager] AI System Initialized (Generational Stacking Disabled).");
    }
}
