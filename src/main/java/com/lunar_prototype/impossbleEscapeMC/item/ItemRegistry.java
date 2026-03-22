package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.item.parser.AmmoDefinitionParser;
import com.lunar_prototype.impossbleEscapeMC.item.parser.AttachmentDefinitionParser;
import com.lunar_prototype.impossbleEscapeMC.item.parser.ItemDefinitionParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;

public class ItemRegistry {
    private static final Map<String, ItemDefinition> ITEM_MAP = new HashMap<>();
    private static final Map<String, AmmoDefinition> AMMO_MAP = new HashMap<>();
    private static final Map<String, AttachmentDefinition> ATTACHMENT_MAP = new HashMap<>();

    /**
     * plugins/ImpossibleEscapeMC/items/ 内の全YAMLをロードします
     */
    public static void loadAllItems(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "items");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        ITEM_MAP.clear();
        AMMO_MAP.clear();
        ATTACHMENT_MAP.clear();

        // --- Ammo 読み込み ---
        File ammofolder = new File(plugin.getDataFolder(), "ammo");
        if (!ammofolder.exists()) {
            ammofolder.mkdirs();
        } else {
            File[] ammoFiles = ammofolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (ammoFiles != null) {
                for (File file : ammoFiles) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    for (String key : config.getKeys(false)) {
                        ConfigurationSection section = config.getConfigurationSection(key);
                        AmmoDefinition ammo = AmmoDefinitionParser.parse(key, section);
                        if (ammo != null) AMMO_MAP.put(key, ammo);
                    }
                }
            }
        }

        // --- Attachment 読み込み ---
        File attachmentFolder = new File(plugin.getDataFolder(), "attachments");
        if (!attachmentFolder.exists()) {
            attachmentFolder.mkdirs();
        } else {
            File[] attachmentFiles = attachmentFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (attachmentFiles != null) {
                for (File file : attachmentFiles) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    for (String key : config.getKeys(false)) {
                        ConfigurationSection section = config.getConfigurationSection(key);
                        AttachmentDefinition att = AttachmentDefinitionParser.parse(key, section);
                        if (att != null) ATTACHMENT_MAP.put(key, att);
                    }
                }
            }
        }

        // --- Item 読み込み ---
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    ItemDefinition def = ItemDefinitionParser.parse(key, section);
                    if (def != null) ITEM_MAP.put(key, def);
                }
            }
        }

        plugin.getLogger().info(ITEM_MAP.size() + " items loaded from /items folder.");
        plugin.getLogger().info(AMMO_MAP.size() + " items loaded from /ammo folder.");
        plugin.getLogger().info(ATTACHMENT_MAP.size() + " attachments loaded from /attachments folder.");
    }

    public static ItemDefinition get(String id) {
        return ITEM_MAP.get(id);
    }

    public static List<String> getAllItemIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(ITEM_MAP.keySet());
        ids.addAll(AMMO_MAP.keySet());
        ids.addAll(ATTACHMENT_MAP.keySet());
        return ids;
    }

    public static AmmoDefinition getAmmo(String id) {
        return AMMO_MAP.get(id);
    }

    public static AttachmentDefinition getAttachment(String id) {
        return ATTACHMENT_MAP.get(id);
    }

    public static List<ItemDefinition> getArmorItemsByClass(int armorClass) {
        List<ItemDefinition> armors = new ArrayList<>();
        for (ItemDefinition def : ITEM_MAP.values()) {
            if (def.armorStats != null && def.armorStats.armorClass == armorClass) {
                armors.add(def);
            }
        }
        armors.sort(Comparator.comparing(def -> def.id));
        return armors;
    }

    public static Map<Integer, List<ItemDefinition>> getArmorItemsGroupedByClass() {
        Map<Integer, List<ItemDefinition>> armorByClass = new HashMap<>();
        for (ItemDefinition def : ITEM_MAP.values()) {
            if (def.armorStats == null) {
                continue;
            }
            armorByClass.computeIfAbsent(def.armorStats.armorClass, key -> new ArrayList<>()).add(def);
        }

        for (List<ItemDefinition> armors : armorByClass.values()) {
            armors.sort(Comparator.comparing(def -> def.id));
        }

        return Collections.unmodifiableMap(armorByClass);
    }

    public static AmmoDefinition getWeakestAmmoForCaliber(String caliber) {
        AmmoDefinition weakest = null;
        for (AmmoDefinition ammo : AMMO_MAP.values()) {
            if (ammo.caliber.equalsIgnoreCase(caliber)) {
                if (weakest == null || ammo.ammoClass < weakest.ammoClass) {
                    weakest = ammo;
                }
            }
        }
        return weakest;
    }
}
