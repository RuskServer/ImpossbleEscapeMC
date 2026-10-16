package com.lunar_prototype.impossbleEscapeMC.item.parser;

import org.bukkit.configuration.ConfigurationSection;

public class ParserUtils {

    public static int getWeight(ConfigurationSection section) {
        if (section == null) return 0;
        if (section.contains("weight")) {
            return section.getInt("weight", 0);
        }
        return section.getInt("weightGrams", 0);
    }
}
