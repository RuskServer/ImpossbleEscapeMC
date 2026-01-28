package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemRegistry {
    private static final Map<String, ItemDefinition> ITEM_MAP = new HashMap<>();
    private static final Map<String, AmmoDefinition> AMMO_MAP = new HashMap<>();

    /**
     * plugins/ImpossibleEscapeMC/items/ 内の全YAMLをロードします
     */
    public static void loadAllItems(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "items");
        if (!folder.exists()) {
            folder.mkdirs();
            // 初回起動時に空のフォルダを作成
            return;
        }

        ITEM_MAP.clear();
        AMMO_MAP.clear();
        File ammofolder = new File(plugin.getDataFolder(), "ammo");
        if (!ammofolder.exists()) {
            ammofolder.mkdirs();
            // 初回起動時に空のフォルダを作成
            return;
        }
        File[] ammoFiles = ammofolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (ammoFiles == null)
            return;

        for (File file : ammoFiles) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                AmmoDefinition ammo = new AmmoDefinition();
                ammo.id = key;
                ammo.caliber = section.getString("caliber");
                ammo.damage = section.getDouble("damage");
                ammo.ammoClass = section.getInt("ammoClass");
                ammo.displayName = section.getString("displayName", key);
                ammo.material = section.getString("material", "IRON_NUGGET");
                ammo.rarity = section.getInt("rarity", 1);
                AMMO_MAP.put(key, ammo);
            }
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null)
            return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null)
                    continue;

                ItemDefinition def = new ItemDefinition();
                def.id = key;
                def.type = section.getString("type");
                def.material = section.getString("material");
                def.rarity = section.getInt("rarity");
                def.maxDurability = section.getInt("maxDurability");
                def.displayName = section.getString("displayName", key);

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

                // --- GunStats の読み込み処理を追加 ---
                if (section.contains("gunStats")) {
                    ConfigurationSection gunSection = section.getConfigurationSection("gunStats");
                    if (gunSection != null) {
                        GunStats gStats = new GunStats();
                        gStats.damage = gunSection.getDouble("damage");
                        gStats.recoil = gunSection.getDouble("recoil");
                        gStats.rpm = gunSection.getInt("rpm");
                        gStats.magSize = gunSection.getInt("magSize");
                        gStats.fireMode = gunSection.getString("fireMode", "SEMI");
                        gStats.customModelData = gunSection.getInt("customModelData", 0);
                        gStats.reloadTime = gunSection.getInt("reloadTime", 2000);
                        gStats.adsTime = gunSection.getInt("adsTime", 200);
                        gStats.caliber = gunSection.getString("caliber");
                        gStats.shotSound = gunSection.getString("shotSound", "ENTITY_GENERIC_EXPLODE"); // デフォルト値
                        gStats.boltType = gunSection.getString("boltType", "CLOSED");
                        gStats.defaultAttachments = gunSection.getStringList("attachments"); // 【追加】デフォルトアタッチメント読み込み

                        gStats.reloadAnimation = parseAnimation(gunSection, "reloadAnimation");
                        gStats.tacticalReloadAnimation = parseAnimation(gunSection, "tacticalReloadAnimation");
                        gStats.aimAnimation = parseAnimation(gunSection, "aimAnimation");
                        gStats.sprintAnimation = parseAnimation(gunSection, "sprintAnimation");
                        gStats.idleAnimation = parseAnimation(gunSection, "idleAnimation");

                        def.gunStats = gStats;
                    }
                }

                // --- MedStats の読み込み処理も同様に追加可能 ---
                if (section.contains("medStats")) {
                    ConfigurationSection medSection = section.getConfigurationSection("medStats");
                    if (medSection != null) {
                        MedStats mStats = new MedStats();
                        mStats.heal = medSection.getDouble("heal");
                        mStats.useTime = medSection.getInt("useTime");
                        mStats.bleedStop = medSection.getBoolean("bleedStop");

                        def.medStats = mStats;
                    }
                }

                ITEM_MAP.put(key, def);
            }
        }
        plugin.getLogger().info(ITEM_MAP.size() + " items loaded from /items folder.");
        plugin.getLogger().info(AMMO_MAP.size() + " items loaded from /ammo folder.");
    }

    public static ItemDefinition get(String id) {
        return ITEM_MAP.get(id);
    }

    public static AmmoDefinition getAmmo(String id) {
        return AMMO_MAP.get(id);
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

    private static GunStats.AnimationStats parseAnimation(ConfigurationSection section, String key) {
        if (!section.contains(key))
            return null;
        ConfigurationSection animSection = section.getConfigurationSection(key);
        if (animSection == null)
            return null;

        GunStats.AnimationStats stats = new GunStats.AnimationStats();
        stats.model = animSection.getString("model");
        stats.frameCount = animSection.getInt("frameCount");
        stats.fps = animSection.getInt("fps");
        return stats;
    }
}