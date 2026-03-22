package com.lunar_prototype.impossbleEscapeMC.item.parser;

import com.lunar_prototype.impossbleEscapeMC.item.Affix;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemDefinitionParser {

    public static ItemDefinition parse(String id, ConfigurationSection section) {
        if (section == null) return null;

        ItemDefinition def = new ItemDefinition();
        def.id = id;
        def.type = section.getString("type");
        def.material = section.getString("material");
        def.rarity = section.getInt("rarity");
        def.customModelData = section.getInt("customModelData", 0);
        def.maxDurability = section.getInt("maxDurability");
        def.weight = ParserUtils.getWeight(section);
        def.displayName = section.getString("displayName", id);

        // Affixの読み込み
        if (section.contains("affixes")) {
            List<Affix> affixes = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("affixes")) {
                Affix affix = new Affix();
                affix.stat = (String) map.get("stat");
                affix.min = ((Number) map.get("min")).doubleValue();
                affix.max = ((Number) map.get("max")).doubleValue();
                affixes.add(affix);
            }
            def.affixes = affixes;
        }

        if (section.contains("gunStats")) {
            def.gunStats = GunStatsParser.parse(section.getConfigurationSection("gunStats"));
        }

        if (section.contains("medStats")) {
            def.medStats = MedStatsParser.parse(section.getConfigurationSection("medStats"));
        }

        if (section.contains("armorStats")) {
            def.armorStats = ArmorStatsParser.parse(section.getConfigurationSection("armorStats"));
        }

        return def;
    }
}
