package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.ArmorStats;
import org.bukkit.configuration.ConfigurationSection;

public class ArmorStatsParser {

    public static ArmorStats parse(ConfigurationSection section) {
        if (section == null) return null;

        ArmorStats aStats = new ArmorStats();
        aStats.defense = section.getInt("defense", 0);
        aStats.armorClass = section.getInt("armorClass", 1);
        aStats.customModelData = section.getInt("customModelData", 0);
        aStats.slot = section.getString("slot", null);
        aStats.equipSound = section.getString("equipSound");
        aStats.model = section.getString("model");
        
        // Support both camelCase and snake_case
        aStats.cameraOverlay = section.contains("camera_overlay")
                ? section.getString("camera_overlay")
                : section.getString("cameraOverlay");

        aStats.dispensable = section.getBoolean("dispensable", true);
        aStats.swappable = section.getBoolean("swappable", true);
        aStats.damageOnHurt = section.getBoolean("damageOnHurt", true);

        return aStats;
    }
}
