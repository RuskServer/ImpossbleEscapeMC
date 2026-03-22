package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.configuration.ConfigurationSection;

public class ParserUtils {

    public static int getWeight(ConfigurationSection section) {
        if (section == null) return 0;
        if (section.contains("weight")) {
            return section.getInt("weight", 0);
        }
        return section.getInt("weightGrams", 0);
    }

    public static GunStats.AnimationStats parseAnimation(ConfigurationSection section, String key) {
        if (!section.contains(key)) return null;
        ConfigurationSection animSection = section.getConfigurationSection(key);
        if (animSection == null) return null;

        GunStats.AnimationStats stats = new GunStats.AnimationStats();
        stats.model = animSection.getString("model");
        stats.frameCount = animSection.getInt("frameCount");
        stats.fps = animSection.getInt("fps");
        stats.playbackSpeed = animSection.getDouble("playbackSpeed", 1.0);
        return stats;
    }

    public static GunStats.ScopeStats parseScope(ConfigurationSection section, String key) {
        if (!section.contains(key)) return null;
        ConfigurationSection scopeSection = section.getConfigurationSection(key);
        if (scopeSection == null) return null;

        GunStats.ScopeStats stats = new GunStats.ScopeStats();
        stats.zoom = scopeSection.getDouble("zoom", 1.0);
        return stats;
    }
}
