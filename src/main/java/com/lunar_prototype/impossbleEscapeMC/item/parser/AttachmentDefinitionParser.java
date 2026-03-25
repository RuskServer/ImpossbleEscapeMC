package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.AttachmentDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.AttachmentSlot;
import org.bukkit.configuration.ConfigurationSection;

public class AttachmentDefinitionParser {

    public static AttachmentDefinition parse(String id, ConfigurationSection section) {
        if (section == null) return null;

        AttachmentDefinition att = new AttachmentDefinition();
        att.id = id;
        att.displayName = section.getString("displayName", id);
        att.material = section.getString("material", "IRON_NUGGET");
        att.slot = AttachmentSlot.fromName(section.getString("slot", "SIGHT"));
        att.modelId = section.getString("modelId", id);
        att.customModelData = section.getInt("customModelData", 0);
        att.rarity = section.getInt("rarity", 1);
        att.weight = ParserUtils.getWeight(section);

        ConfigurationSection modSection = section.getConfigurationSection("modifiers");
        if (modSection != null) {
            for (String key : modSection.getKeys(false)) {
                att.modifiers.put(key, modSection.getDouble(key));
            }
        }
        
        att.aimAnimation = ParserUtils.parseAnimation(section, "aimAnimation");
        att.equipAnimation = ParserUtils.parseAnimation(section, "equipAnimation");
        att.scope = ParserUtils.parseScope(section, "scope");

        return att;
    }
}
