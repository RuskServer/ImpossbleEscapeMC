package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import org.bukkit.configuration.ConfigurationSection;

public class AmmoDefinitionParser {

    public static AmmoDefinition parse(String id, ConfigurationSection section) {
        if (section == null) return null;

        AmmoDefinition ammo = new AmmoDefinition();
        ammo.id = id;
        ammo.caliber = section.getString("caliber");
        ammo.damage = section.getDouble("damage");
        ammo.ammoClass = section.getInt("ammoClass");
        ammo.displayName = section.getString("displayName", id);
        ammo.material = section.getString("material", "IRON_NUGGET");
        ammo.rarity = section.getInt("rarity", 1);
        ammo.weight = ParserUtils.getWeight(section);
        ammo.customModelData = section.getInt("customModelData", 0);
        
        return ammo;
    }
}
