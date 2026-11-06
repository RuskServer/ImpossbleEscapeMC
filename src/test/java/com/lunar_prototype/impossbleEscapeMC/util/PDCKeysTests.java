package com.lunar_prototype.impossbleEscapeMC.util;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PDCKeys のキーの文字列を固定する。保存済みのアイテムの PDC (item_id など) はこの文字列で読み書きされているので、
 * 1 文字でも変わると既存のアイテムが読めなくなる。プラグインのインスタンス (getInstance) には依存しない。
 */
class PDCKeysTests {

    /** 従来 (new NamespacedKey(プラグイン, 名前)) で作っていたキーの全て。フィールド名 → 名前 */
    private static final Map<String, String> LEGACY = new LinkedHashMap<>();

    static {
        String[] pairs = {
                "ITEM_ID", "item_id", "DURABILITY", "durability", "AMMO", "ammo", "CURRENT_AMMO_ID", "current_ammo_id",
                "CHAMBER_LOADED", "chamber_loaded", "CHAMBER_AMMO_ID", "chamber_ammo_id", "JAMMED", "jammed",
                "ATTACHMENTS", "attachments", "ARMOR_CLASS", "armor_class_int", "LOOT_TABLE_ID", "loot_table_id",
                "CORPSE_INVENTORY", "corpse_inventory", "GUI_TRIGGER", "gui_trigger", "ITEM_WEIGHT", "item_weight",
                "ITEM_COST", "item_cost", "COST_SLOT_PLACEHOLDER", "cost_slot_placeholder",
                "BACKPACK_DATA", "backpack_data", "BACKPACK_UID", "backpack_uid", "MAP_ZOOM", "map_zoom",
                "LOCKED_SLOT_PLACEHOLDER", "locked_slot_placeholder", "FIND_IN_RAID", "find_in_raid",
                "RAID_BROUGHT_IN", "raid_brought_in", "DROPPED_BY_PLAYER", "dropped_by_player",
                "QUEST_ID", "quest_id", "RAID_MAP_ID", "raid_map_id", "GUI_TYPE", "gui_type",
                "HELMET_VISOR", "helmet_visor", "VISOR_DURABILITY", "visor_durability", "SUPPLIED", "supplied"
        };
        for (int i = 0; i < pairs.length; i += 2) LEGACY.put(pairs[i], pairs[i + 1]);
    }

    private static String pluginNamespace() throws Exception {
        try (var in = PDCKeysTests.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml が見つからない");
            String name = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getString("name");
            assertNotNull(name, "plugin.yml に name がない");
            return name.toLowerCase(Locale.ROOT);
        }
    }

    @Test
    void everyLegacyKeyKeepsItsStoredString() throws Exception {
        String namespace = pluginNamespace();
        for (Map.Entry<String, String> entry : LEGACY.entrySet()) {
            Field field = PDCKeys.class.getField(entry.getKey());
            NamespacedKey key = (NamespacedKey) field.get(null);
            assertEquals(namespace + ":" + entry.getValue(), key.toString(), entry.getKey());
        }
    }

    @Test
    void everyNamespacedKeyFieldUsesThePluginNamespace() throws Exception {
        String namespace = pluginNamespace();
        for (Field field : PDCKeys.class.getFields()) {
            if (field.getType() != NamespacedKey.class || !Modifier.isStatic(field.getModifiers())) continue;
            assertEquals(namespace, ((NamespacedKey) field.get(null)).getNamespace(), field.getName());
        }
    }

    @Test
    void affixMatchesTheOldPluginBasedKey() throws Exception {
        MockBukkit.mock();
        try {
            // 従来のやり方 (プラグイン名の小文字が名前空間・名前も小文字にされる) と同じキーになること
            Plugin plugin = MockBukkit.createMockPlugin("ImpossbleEscapeMC");
            for (String stat : new String[]{"fire_damage", "FIRE_DAMAGE", "Armor_Pen"}) {
                assertEquals(new NamespacedKey(plugin, "affix_" + stat), PDCKeys.affix(stat), stat);
            }
        } finally {
            MockBukkit.unmock();
        }
    }
}
