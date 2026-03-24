package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.RigStats;
import org.bukkit.configuration.ConfigurationSection;

public class RigStatsParser {

    public static RigStats parse(ConfigurationSection section) {
        if (section == null) return null;

        RigStats stats = new RigStats();
        int size = section.getInt("size", 0);
        if (size < 0) size = 0;
        if (size > 27) size = 27;
        stats.size = size;

        double reduction = section.getDouble("reduction", 0.0);
        if (reduction < 0.0) reduction = 0.0;
        if (reduction > 0.95) reduction = 0.95;
        stats.reduction = reduction;

        return stats;
    }
}
