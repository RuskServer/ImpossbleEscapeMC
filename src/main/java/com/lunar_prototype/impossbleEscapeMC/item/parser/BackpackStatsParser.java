package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.BackpackStats;
import org.bukkit.configuration.ConfigurationSection;

public class BackpackStatsParser {

    public static BackpackStats parse(ConfigurationSection section) {
        if (section == null) return null;

        BackpackStats stats = new BackpackStats();

        int configuredSize = section.getInt("size", 9);
        if (configuredSize <= 0) configuredSize = 9;
        if (configuredSize > 54) configuredSize = 54;
        if (configuredSize % 9 != 0) {
            configuredSize = ((configuredSize + 8) / 9) * 9;
            configuredSize = Math.min(configuredSize, 54);
        }
        stats.size = configuredSize;

        double reduction = section.getDouble("reduction", 0.0);
        if (reduction < 0.0) reduction = 0.0;
        if (reduction > 0.95) reduction = 0.95;
        stats.reduction = reduction;

        return stats;
    }
}
