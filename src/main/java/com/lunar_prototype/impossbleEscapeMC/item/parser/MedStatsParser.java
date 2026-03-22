package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.MedStats;
import org.bukkit.configuration.ConfigurationSection;

public class MedStatsParser {

    public static MedStats parse(ConfigurationSection section) {
        if (section == null) return null;

        MedStats mStats = new MedStats();
        mStats.heal = section.getDouble("heal");
        mStats.useTime = section.getInt("useTime");
        mStats.bleedStop = section.getBoolean("bleedStop");

        // --- Continuous medical settings ---
        mStats.continuous = section.getBoolean("continuous", false);
        mStats.healPerUse = section.getDouble("healPerUse", 0.0);
        mStats.durabilityPerUse = section.getInt("durabilityPerUse", 0);
        mStats.usingCustomModelData = section.getInt("usingCustomModelData", 0);

        // --- One-time/CAT settings ---
        mStats.oneTime = section.getBoolean("oneTime", false);
        mStats.cureBleeding = section.getBoolean("cureBleeding", false);
        mStats.cureLegFracture = section.getBoolean("cureLegFracture", false);
        mStats.cureArmFracture = section.getBoolean("cureArmFracture", false);
        mStats.durationTicks = section.getInt("durationTicks", 0);

        return mStats;
    }
}
