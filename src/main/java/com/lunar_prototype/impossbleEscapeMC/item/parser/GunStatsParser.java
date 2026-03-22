package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.configuration.ConfigurationSection;

public class GunStatsParser {

    public static GunStats parse(ConfigurationSection section) {
        if (section == null) return null;

        GunStats gStats = new GunStats();
        gStats.damage = section.getDouble("damage");
        gStats.recoil = section.getDouble("recoil");
        gStats.rpm = section.getInt("rpm");
        gStats.magSize = section.getInt("magSize");
        gStats.fireMode = section.getString("fireMode", "SEMI");
        gStats.customModelData = section.getInt("customModelData", 0);
        gStats.adsTime = section.getInt("adsTime", 200);
        gStats.boltingTime = section.getInt("boltingTime", 1000);
        gStats.caliber = section.getString("caliber");
        gStats.shotSound = section.getString("shotSound", "ENTITY_GENERIC_EXPLODE");
        gStats.boltType = section.getString("boltType", "CLOSED");
        gStats.defaultAttachments = section.getStringList("attachments");

        gStats.reloadAnimation = ParserUtils.parseAnimation(section, "reloadAnimation");
        gStats.tacticalReloadAnimation = ParserUtils.parseAnimation(section, "tacticalReloadAnimation");
        gStats.reloadLoopAnimation = ParserUtils.parseAnimation(section, "reloadLoopAnimation");
        gStats.boltingAnimation = ParserUtils.parseAnimation(section, "boltingAnimation");
        gStats.independentAnimation = ParserUtils.parseAnimation(section, "independentAnimation");

        if (section.contains("validIndependentAnimStates")) {
            gStats.validIndependentAnimStates = section.getStringList("validIndependentAnimStates");
        }

        gStats.aimAnimation = ParserUtils.parseAnimation(section, "aimAnimation");
        gStats.sprintAnimation = ParserUtils.parseAnimation(section, "sprintAnimation");
        gStats.idleAnimation = ParserUtils.parseAnimation(section, "idleAnimation");
        gStats.scope = ParserUtils.parseScope(section, "scope");
        gStats.pelletCount = section.getInt("pelletCount", 1);

        return gStats;
    }
}
