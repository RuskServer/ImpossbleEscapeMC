package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.item.parser.AttachmentDefinitionParser;
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

        ITEM_MAP.clear();
        AMMO_MAP.clear();
        ATTACHMENT_MAP.clear();

        // --- Ammo (Java 定義) ---
        for (AmmoDefinition ammo : AmmoCatalog.create()) {
            if (AMMO_MAP.putIfAbsent(ammo.id, ammo) != null) {
                plugin.getLogger().severe("弾のIDが重複しています (後の定義を無視): " + ammo.id);
            }
        }
        Map<String, Integer> referencesPerCaliber = new HashMap<>();
        for (AmmoDefinition ammo : AMMO_MAP.values()) {
            referencesPerCaliber.merge(ammo.caliber.toLowerCase(java.util.Locale.ROOT), ammo.reference ? 1 : 0, Integer::sum);
        }
        referencesPerCaliber.forEach((caliber, count) -> {
            if (count != 1) plugin.getLogger().warning("口径 " + caliber + " の基準弾が " + count + " 個あります (1個にしてください)");
        });
        File ammoFolder = new File(plugin.getDataFolder(), "ammo");
        File[] legacyAmmoFiles = ammoFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (legacyAmmoFiles != null && legacyAmmoFiles.length > 0) {
            plugin.getLogger().warning("ammo/ の " + legacyAmmoFiles.length + " 個の yml は読み込まれません。弾は AmmoCatalog (Java) で定義します");
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

        // --- Item (Java 定義) ---
        for (ItemDefinition def : ItemCatalog.create()) {
            if (ITEM_MAP.putIfAbsent(def.id, def) != null) {
                plugin.getLogger().severe("アイテムのIDが重複しています (後の定義を無視): " + def.id);
            }
        }
        File[] legacyItemFiles = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (legacyItemFiles != null && legacyItemFiles.length > 0) {
            plugin.getLogger().warning("items/ の " + legacyItemFiles.length + " 個の yml は読み込まれません。アイテムは ItemCatalog (Java) で定義します (銃はデータパック銃)");
        }

        plugin.getLogger().info(ITEM_MAP.size() + " items loaded (ItemCatalog).");
        plugin.getLogger().info(AMMO_MAP.size() + " ammo types loaded (AmmoCatalog).");
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

    /** 定義されている弾のID */
    public static List<String> getAmmoIds() {
        return new ArrayList<>(AMMO_MAP.keySet());
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

    public static List<ItemDefinition> getBackpackItems() {
        List<ItemDefinition> backpacks = new ArrayList<>();
        for (ItemDefinition def : ITEM_MAP.values()) {
            if (def.backpackStats != null) {
                backpacks.add(def);
            }
        }
        backpacks.sort(Comparator.comparing(def -> def.id));
        return backpacks;
    }

    /** 口径の基準弾 (データパック銃のダメージはこの弾に合わせてある)。基準弾が無ければいちばん弱い弾 */
    public static AmmoDefinition getReferenceAmmoForCaliber(String caliber) {
        for (AmmoDefinition ammo : AMMO_MAP.values()) {
            if (ammo.reference && ammo.caliber.equalsIgnoreCase(caliber)) return ammo;
        }
        return getWeakestAmmoForCaliber(caliber);
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
